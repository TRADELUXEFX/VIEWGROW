-- 20261121020000: device ban (a banned member's phone cannot be used to sign up again in the app)
-- Requested by the owner on 21 Nov 2026.
--
-- Before: a ban only blocked the phone number. A banned person could sign up again with another number on the same
-- phone. Nothing in the app or the database knew which phone was used.
--
-- What changes:
-- 1. The app (new version) sends a device code: "a:" + 32 letters/numbers, a SHA-256 of the phone's Android ID mixed
--    with "viewgrow". The Android ID itself never leaves the phone and cannot be worked back from the code.
-- 2. web_devices: which device codes each member used (sign up, log in, opening the app). Goes when the member is deleted.
--    Filled by: a trigger on web_sessions (every app log in through web_login already stores p_device there),
--    web_register_device (sign up), and web_device_seen (opening the app while logged in).
-- 3. web_banned_devices: the device codes of banned members. Kept even if the member is deleted later.
--    A trigger on web_users keeps it in step: ban -> that member's devices are added; unban -> removed.
--    A device seen later for a member who is already banned is added at once.
-- 4. web_register_device(p_name, p_phone, p_consent, p_pin, p_device): the app's new sign-up. Refuses a banned
--    device with code DEVICE_BANNED, otherwise calls the existing web_register unchanged and records the device.
-- 5. web_device_seen(p_phone, p_pin, p_device): the app calls it when it opens (at most once a day). Records the
--    device for the member whose login token it is, even while banned (so a member banned before this change gets
--    their phone added the first time the new app opens).
-- 6. web_admin_devices(p_admin_key, p_user_id): the admin can see a member's devices and whether each is banned.
--
-- Nothing existing is edited: web_register, web_login, _web_auth and the ban functions stay as they are.
-- The website sends no device code, so sign-up on the website is not affected (a browser has no Android ID).
-- One transaction. Safe to run twice. Undo: rollback/device_ban_rollback.sql.

begin;

-- Stop without changing anything if something this file relies on is missing.
do $$
begin
  if to_regprocedure('public.web_register(text,text,boolean,text)') is null
     or to_regprocedure('public._web_admin_ok(text)') is null
     or to_regprocedure('public._web_norm(text)') is null
     or to_regclass('public.web_sessions') is null
     or not exists (select 1 from information_schema.columns where table_schema = 'public' and table_name = 'web_users' and column_name = 'banned') then
    raise exception 'Stopped, nothing changed: web_register, _web_admin_ok, _web_norm, web_sessions or web_users.banned is missing.';
  end if;
end $$;

create table if not exists public.web_devices (
  device     text        not null check (device ~ '^a:[0-9a-f]{32}$'),
  user_id    bigint      not null references public.web_users(id) on delete cascade,
  first_seen timestamptz not null default now(),
  last_seen  timestamptz not null default now(),
  primary key (device, user_id)
);
create index if not exists web_devices_user_idx on public.web_devices(user_id);
alter table public.web_devices enable row level security;   -- no policies: only the functions below touch it
revoke all on public.web_devices from public, anon, authenticated;

create table if not exists public.web_banned_devices (
  device    text        not null,
  user_id   bigint      not null,        -- no link: the record stays if the member is deleted
  phone     text        not null default '',
  banned_at timestamptz not null default now(),
  primary key (device, user_id)
);
alter table public.web_banned_devices enable row level security;
revoke all on public.web_banned_devices from public, anon, authenticated;

-- Internal: remember that member u used device d (ignores anything that is not a device code).
create or replace function public._web_device_note(p_user bigint, p_device text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if p_user is null or p_device is null or p_device !~ '^a:[0-9a-f]{32}$' then return; end if;
  insert into public.web_devices (device, user_id) values (p_device, p_user)
  on conflict (device, user_id) do update set last_seen = now();
end
$$;
revoke all on function public._web_device_note(bigint, text) from public, anon, authenticated;

-- Internal: true when the device belongs to a banned member (now or before).
create or replace function public._web_device_banned(p_device text)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select p_device is not null and p_device ~ '^a:[0-9a-f]{32}$'
     and exists (select 1 from public.web_banned_devices b where b.device = p_device)
$$;
revoke all on function public._web_device_banned(text) from public, anon, authenticated;

-- Trigger: every app log in (web_login stores p_device in web_sessions) records the device.
create or replace function public._web_devices_from_session()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform public._web_device_note(new.user_id, new.device);
  return new;
end
$$;
revoke all on function public._web_devices_from_session() from public, anon, authenticated;
drop trigger if exists web_sessions_device on public.web_sessions;
create trigger web_sessions_device after insert on public.web_sessions
  for each row execute function public._web_devices_from_session();

-- Trigger: a device recorded for a member who is already banned is banned at once.
create or replace function public._web_devices_ban_new()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.web_banned_devices (device, user_id, phone)
  select new.device, u.id, u.phone from public.web_users u where u.id = new.user_id and u.banned
  on conflict (device, user_id) do nothing;
  return new;
end
$$;
revoke all on function public._web_devices_ban_new() from public, anon, authenticated;
drop trigger if exists web_devices_ban_new on public.web_devices;
create trigger web_devices_ban_new after insert on public.web_devices
  for each row execute function public._web_devices_ban_new();

-- Trigger: ban adds the member's devices to the banned list, unban takes them off.
create or replace function public._web_users_ban_devices()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if coalesce(new.banned, false) and not coalesce(old.banned, false) then
    insert into public.web_banned_devices (device, user_id, phone)
    select d.device, new.id, new.phone from public.web_devices d where d.user_id = new.id
    on conflict (device, user_id) do nothing;
  elsif not coalesce(new.banned, false) and coalesce(old.banned, false) then
    delete from public.web_banned_devices where user_id = new.id;
  end if;
  return new;
end
$$;
revoke all on function public._web_users_ban_devices() from public, anon, authenticated;
drop trigger if exists web_users_ban_devices on public.web_users;
create trigger web_users_ban_devices after update of banned on public.web_users
  for each row execute function public._web_users_ban_devices();

-- Sign up from the app: refuses a banned device, otherwise the normal sign-up (web_register, unchanged).
create or replace function public.web_register_device(p_name text, p_phone text, p_consent boolean, p_pin text, p_device text default '')
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare r jsonb; v_uid bigint;
begin
  if public._web_device_banned(p_device) then
    return jsonb_build_object('ok', false, 'code', 'DEVICE_BANNED');
  end if;
  r := public.web_register(p_name, p_phone, p_consent, p_pin);
  if coalesce((r->>'ok')::boolean, false) then
    select id into v_uid from public.web_users where phone = public._web_norm(p_phone);
    perform public._web_device_note(v_uid, p_device);
  end if;
  return r;
end
$$;
revoke all on function public.web_register_device(text, text, boolean, text, text) from public;
grant execute on function public.web_register_device(text, text, boolean, text, text) to anon, authenticated;

-- Opening the app while logged in: records the device for the member whose login token this is (banned or not).
create or replace function public.web_device_seen(p_phone text, p_pin text, p_device text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $$
declare v_uid bigint;
begin
  if p_pin is null or left(p_pin, 4) <> 'vgs_' or p_device is null or p_device !~ '^a:[0-9a-f]{32}$' then
    return jsonb_build_object('ok', false);
  end if;
  select s.user_id into v_uid
    from public.web_sessions s join public.web_users u on u.id = s.user_id
   where s.token_hash = encode(digest(p_pin, 'sha256'), 'hex') and s.expires_at > now()
     and u.phone = public._web_norm(p_phone);
  if v_uid is null then return jsonb_build_object('ok', false); end if;
  perform public._web_device_note(v_uid, p_device);
  return jsonb_build_object('ok', true);
end
$$;
revoke all on function public.web_device_seen(text, text, text) from public;
grant execute on function public.web_device_seen(text, text, text) to anon, authenticated;

-- Admin: a member's devices (newest first) and whether each one is on the banned list.
create or replace function public.web_admin_devices(p_admin_key text, p_user_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_admin_key);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  return jsonb_build_object('ok', true, 'devices', coalesce((
    select jsonb_agg(jsonb_build_object(
             'device', right(d.device, 6),
             'first_seen', d.first_seen,
             'last_seen', d.last_seen,
             'banned', exists (select 1 from public.web_banned_devices b where b.device = d.device),
             'other_members', (select count(*) from public.web_devices o where o.device = d.device and o.user_id <> d.user_id))
           order by d.last_seen desc)
      from public.web_devices d where d.user_id = p_user_id), '[]'::jsonb));
end
$$;
revoke all on function public.web_admin_devices(text, bigint) from public;
grant execute on function public.web_admin_devices(text, bigint) to anon, authenticated;

commit;
