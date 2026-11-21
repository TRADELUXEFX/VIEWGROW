-- 20261121030000: one account per phone (app sign-up)
-- Requested by the owner on 21 Nov 2026. Needs 20261121020000_device_ban.sql (applied 21 Nov 2026).
--
-- There is no SMS code, so a person could make a second account with another number (for example when their
-- contact limit is full). From now on, in the app:
-- 1. A sign-up from a phone that already has a ViewGrow account is stopped with code DEVICE_HAS_ACCOUNT, and recorded
--    in web_device_flags (the phone, the number and name they tried, which account(s) the phone already has).
--    The app shows a full-screen page that sends them to message the admin.
-- 2. The admin can let that phone make one more account (web_admin_device_flag_allow), or close the flag
--    (web_admin_device_flag_dismiss). Allowing is used up by the next successful sign-up from that phone.
-- 3. A banned phone is still refused first with DEVICE_BANNED (unchanged).
-- Only phones the database knows count: a member's phone is known once they sign up, log in or open the new app.
-- Deleted accounts do not count (their device rows go with them). The website sends no device code: not affected.
-- One transaction. Safe to run twice. Undo: rollback/one_account_per_phone_rollback.sql (puts the 20261121020000
-- version of web_register_device back).

begin;

do $$
begin
  if to_regclass('public.web_devices') is null or to_regprocedure('public._web_device_banned(text)') is null then
    raise exception 'Stopped, nothing changed: run 20261121020000_device_ban.sql first.';
  end if;
end $$;

create table if not exists public.web_device_flags (
  id          bigserial   primary key,
  device      text        not null,
  tried_phone text        not null default '',
  tried_name  text        not null default '',
  user_ids    bigint[]    not null default '{}',   -- the account(s) this phone already had
  created_at  timestamptz not null default now(),
  status      text        not null default 'open' check (status in ('open', 'allowed', 'dismissed')),
  closed_at   timestamptz
);
create index if not exists web_device_flags_open_idx on public.web_device_flags(status, created_at desc);
alter table public.web_device_flags enable row level security;
revoke all on public.web_device_flags from public, anon, authenticated;

-- A phone the admin let make one more account. Removed when that sign-up succeeds.
create table if not exists public.web_device_allow (
  device     text        primary key,
  flag_id    bigint,
  created_at timestamptz not null default now()
);
alter table public.web_device_allow enable row level security;
revoke all on public.web_device_allow from public, anon, authenticated;

create or replace function public.web_register_device(p_name text, p_phone text, p_consent boolean, p_pin text, p_device text default '')
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare r jsonb; v_uid bigint; v_ids bigint[]; v_ok boolean := p_device is not null and p_device ~ '^a:[0-9a-f]{32}$';
begin
  if public._web_device_banned(p_device) then
    return jsonb_build_object('ok', false, 'code', 'DEVICE_BANNED');
  end if;

  if v_ok and not exists (select 1 from public.web_device_allow a where a.device = p_device) then
    select array_agg(d.user_id order by d.first_seen) into v_ids
      from public.web_devices d join public.web_users u on u.id = d.user_id
     where d.device = p_device and u.phone is distinct from public._web_norm(p_phone);
    if v_ids is not null then
      -- One open flag per phone and number a day is enough (no flood from tapping Sign up again).
      if not exists (select 1 from public.web_device_flags f
                      where f.device = p_device and f.status = 'open' and f.tried_phone = coalesce(public._web_norm(p_phone), left(coalesce(p_phone, ''), 30))
                        and f.created_at > now() - interval '1 day') then
        insert into public.web_device_flags (device, tried_phone, tried_name, user_ids)
        values (p_device, coalesce(public._web_norm(p_phone), left(coalesce(p_phone, ''), 30)), left(btrim(coalesce(p_name, '')), 60), v_ids);
      end if;
      return jsonb_build_object('ok', false, 'code', 'DEVICE_HAS_ACCOUNT');
    end if;
  end if;

  r := public.web_register(p_name, p_phone, p_consent, p_pin);
  if coalesce((r->>'ok')::boolean, false) then
    select id into v_uid from public.web_users where phone = public._web_norm(p_phone);
    perform public._web_device_note(v_uid, p_device);
    if v_ok then delete from public.web_device_allow where device = p_device; end if;
  end if;
  return r;
end
$$;
revoke all on function public.web_register_device(text, text, boolean, text, text) from public;
grant execute on function public.web_register_device(text, text, boolean, text, text) to anon, authenticated;

-- Admin: flags, newest first. p_all false = open ones only.
create or replace function public.web_admin_device_flags(p_key text, p_all boolean default false)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_key);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  return jsonb_build_object('ok', true, 'rows', coalesce((
    select jsonb_agg(jsonb_build_object(
             'id', f.id, 'device', right(f.device, 6), 'tried_phone', f.tried_phone, 'tried_name', f.tried_name,
             'created_at', f.created_at, 'status', f.status,
             'accounts', coalesce((select jsonb_agg(jsonb_build_object('id', u.id, 'name', u.name, 'phone', u.phone, 'banned', u.banned))
                                     from public.web_users u where u.id = any(f.user_ids)), '[]'::jsonb))
           order by f.created_at desc)
      from (select * from public.web_device_flags where p_all or status = 'open' order by created_at desc limit 200) f), '[]'::jsonb));
end
$$;
revoke all on function public.web_admin_device_flags(text, boolean) from public;
grant execute on function public.web_admin_device_flags(text, boolean) to anon, authenticated;

-- Admin: let the phone in this flag make one more account.
create or replace function public.web_admin_device_flag_allow(p_key text, p_flag_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_key); f public.web_device_flags;
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  select * into f from public.web_device_flags where id = p_flag_id;
  if not found then return jsonb_build_object('ok', false, 'code', 'NOT_FOUND'); end if;
  insert into public.web_device_allow (device, flag_id) values (f.device, f.id)
  on conflict (device) do update set flag_id = excluded.flag_id, created_at = now();
  update public.web_device_flags set status = 'allowed', closed_at = now() where device = f.device and status = 'open';
  return jsonb_build_object('ok', true);
end
$$;
revoke all on function public.web_admin_device_flag_allow(text, bigint) from public;
grant execute on function public.web_admin_device_flag_allow(text, bigint) to anon, authenticated;

-- Admin: close a flag without allowing anything.
create or replace function public.web_admin_device_flag_dismiss(p_key text, p_flag_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_key);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  update public.web_device_flags set status = 'dismissed', closed_at = now() where id = p_flag_id and status = 'open';
  return jsonb_build_object('ok', true);
end
$$;
revoke all on function public.web_admin_device_flag_dismiss(text, bigint) from public;
grant execute on function public.web_admin_device_flag_dismiss(text, bigint) to anon, authenticated;

commit;
