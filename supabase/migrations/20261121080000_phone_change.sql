-- 20261121080000: change phone number (member asks, admin approves)
-- Requested by the owner on 21 Nov 2026: the admin approves each change; other members' phones swap the saved
-- ViewGrow contact to the new number.
--
-- MEMBER (app and website, logged in):
-- 1. web_phone_change_request(p_phone, p_pin, p_new_phone): asks to move the account to a new number. Refused if the
--    new number is not a real number, is the same, belongs to another member, is on the deleted list, is the admin's
--    or a VIP number, or another member already asked for it. One open request per member (a new one replaces it).
--    At most 5 requests a day.
-- 2. web_phone_change_mine(p_phone, p_pin): the member's open request, or the last one decided in the past 7 days.
-- 3. web_phone_change_cancel(p_phone, p_pin): withdraws the open request.
-- ADMIN:
-- 4. web_admin_phone_changes(p_key): open requests (oldest first), then the last 30 decided in the past 30 days.
-- 5. web_admin_phone_change_decide(p_key, p_id, p_action 'approve' | 'refuse', p_note):
--    Approve moves everything to the new number in one go: the account, what other members were given (web_received),
--    the saved day lists (web_snapshot_contacts), contact files handed out but not confirmed (web_downloads.picked),
--    the member's purchases, and reports about them. Their login tokens end, so they log in again with the new number
--    and the SAME password. History, verified days, limit and name stay. The old number becomes free.
-- OTHER MEMBERS' PHONES:
-- 6. web_phone_swaps records each approved change; web_swaps_for_me(p_phone, p_pin, p_since) gives a member only the
--    changes for people they were given, so the app can change the saved "Name VG" contact to the new number.
--
-- Nothing existing is edited. One transaction: if anything fails, nothing changes. Safe to run twice.
-- Check after (read-only), expect true | true | true:
--   select to_regclass('public.web_phone_changes') is not null, to_regclass('public.web_phone_swaps') is not null,
--          to_regprocedure('public.web_admin_phone_change_decide(text,bigint,text,text)') is not null;

begin;

do $$
begin
  if to_regclass('public.web_users') is null or to_regclass('public.web_received') is null
     or to_regclass('public.web_snapshot_contacts') is null or to_regclass('public.web_purchases') is null
     or to_regclass('public.web_reports') is null or to_regclass('public.web_gone') is null
     or to_regclass('public.web_downloads') is null or to_regclass('public.deleted_numbers') is null
     or to_regclass('public.web_sessions') is null
     or to_regprocedure('public._web_auth(text,text)') is null or to_regprocedure('public._web_admin_ok(text)') is null
     or to_regprocedure('public._web_norm(text)') is null or to_regprocedure('public.vg_phone_key(text)') is null
     or to_regprocedure('public._web_is_admin_number(text)') is null then
    raise exception 'Stopped, nothing changed: a table or function this needs is missing (run the 21 Nov 2026 updates first).';
  end if;
end $$;

create table if not exists public.web_phone_changes (
  id         bigserial   primary key,
  user_id    bigint      not null references public.web_users(id) on delete cascade,
  old_phone  text        not null,
  new_phone  text        not null,
  status     text        not null default 'pending' check (status in ('pending', 'approved', 'refused', 'cancelled')),
  note       text        not null default '',
  created_at timestamptz not null default now(),
  decided_at timestamptz
);
create unique index if not exists web_phone_changes_one_open on public.web_phone_changes(user_id) where status = 'pending';
create index if not exists web_phone_changes_status_idx on public.web_phone_changes(status, created_at);
alter table public.web_phone_changes enable row level security;
revoke all on public.web_phone_changes from public, anon, authenticated;

create table if not exists public.web_phone_swaps (
  id         bigserial   primary key,
  old_phone  text        not null,
  new_phone  text        not null,
  swapped_at timestamptz not null default now()
);
create index if not exists web_phone_swaps_at_idx on public.web_phone_swaps(swapped_at);
alter table public.web_phone_swaps enable row level security;
revoke all on public.web_phone_swaps from public, anon, authenticated;

-- Why a number cannot be this member's new number (null = it can).
create or replace function public._web_phone_change_problem(p_uid bigint, p_new text)
returns text
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare n text := public._web_norm(p_new); k text;
begin
  if n is null then return 'INVALID_PHONE'; end if;
  k := public.vg_phone_key(n);
  if exists (select 1 from public.web_users u where u.id = p_uid and public.vg_phone_key(u.phone) = k) then return 'SAME_NUMBER'; end if;
  if exists (select 1 from public.web_users u where u.id <> p_uid and public.vg_phone_key(u.phone) = k) then return 'NUMBER_TAKEN'; end if;
  if exists (select 1 from public.deleted_numbers d where public.vg_phone_key(d.phone) = k) then return 'NUMBER_DELETED'; end if;
  if public._web_is_admin_number(n) then return 'NUMBER_TAKEN'; end if;
  if exists (select 1 from public.web_phone_changes c
              where c.status = 'pending' and c.user_id <> p_uid and public.vg_phone_key(c.new_phone) = k) then
    return 'NUMBER_TAKEN';
  end if;
  return null;
end
$$;
revoke all on function public._web_phone_change_problem(bigint, text) from public, anon, authenticated;

create or replace function public._web_phone_change_json(c public.web_phone_changes)
returns jsonb
language sql
stable
set search_path = public, pg_temp
as $$
  select jsonb_build_object('id', c.id, 'old_phone', c.old_phone, 'new_phone', c.new_phone, 'status', c.status,
                            'note', c.note, 'created_at', c.created_at, 'decided_at', c.decided_at);
$$;
revoke all on function public._web_phone_change_json(public.web_phone_changes) from public, anon, authenticated;

create or replace function public.web_phone_change_request(p_phone text, p_pin text, p_new_phone text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare a record; prob text; c public.web_phone_changes; cur text;
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  prob := public._web_phone_change_problem(a.uid, p_new_phone);
  if prob is not null then return jsonb_build_object('ok', false, 'code', prob); end if;
  if (select count(*) from public.web_phone_changes where user_id = a.uid and created_at > now() - interval '1 day') >= 5 then
    return jsonb_build_object('ok', false, 'code', 'TOO_MANY');
  end if;
  update public.web_phone_changes set status = 'cancelled', decided_at = now() where user_id = a.uid and status = 'pending';
  select phone into cur from public.web_users where id = a.uid;
  insert into public.web_phone_changes (user_id, old_phone, new_phone)
  values (a.uid, cur, public._web_norm(p_new_phone)) returning * into c;
  return jsonb_build_object('ok', true, 'request', public._web_phone_change_json(c));
end
$$;
revoke all on function public.web_phone_change_request(text, text, text) from public;
grant execute on function public.web_phone_change_request(text, text, text) to anon, authenticated;

create or replace function public.web_phone_change_mine(p_phone text, p_pin text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare a record; c public.web_phone_changes;
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  select * into c from public.web_phone_changes
   where user_id = a.uid and (status = 'pending' or (status in ('approved', 'refused') and decided_at > now() - interval '7 days'))
   order by (status = 'pending') desc, created_at desc limit 1;
  return jsonb_build_object('ok', true, 'request', case when c.id is null then null else public._web_phone_change_json(c) end);
end
$$;
revoke all on function public.web_phone_change_mine(text, text) from public;
grant execute on function public.web_phone_change_mine(text, text) to anon, authenticated;

create or replace function public.web_phone_change_cancel(p_phone text, p_pin text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare a record;
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  update public.web_phone_changes set status = 'cancelled', decided_at = now() where user_id = a.uid and status = 'pending';
  return jsonb_build_object('ok', true);
end
$$;
revoke all on function public.web_phone_change_cancel(text, text) from public;
grant execute on function public.web_phone_change_cancel(text, text) to anon, authenticated;

create or replace function public.web_admin_phone_changes(p_key text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_key);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  return jsonb_build_object('ok', true,
    'open', coalesce((
      select jsonb_agg(public._web_phone_change_json(c) || jsonb_build_object(
               'user_id', u.id, 'name', u.name, 'phone_now', u.phone, 'banned', u.banned,
               'problem', public._web_phone_change_problem(u.id, c.new_phone)) order by c.created_at)
        from public.web_phone_changes c join public.web_users u on u.id = c.user_id
       where c.status = 'pending'), '[]'::jsonb),
    'done', coalesce((
      select jsonb_agg(x.j order by x.decided_at desc) from (
        select c.decided_at, public._web_phone_change_json(c) || jsonb_build_object('user_id', u.id, 'name', u.name) as j
          from public.web_phone_changes c join public.web_users u on u.id = c.user_id
         where c.status in ('approved', 'refused') and c.decided_at > now() - interval '30 days'
         order by c.decided_at desc limit 30) x), '[]'::jsonb));
end
$$;
revoke all on function public.web_admin_phone_changes(text) from public;
grant execute on function public.web_admin_phone_changes(text) to anon, authenticated;

create or replace function public.web_admin_phone_change_decide(p_key text, p_id bigint, p_action text, p_note text default '')
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  k text := public._web_admin_ok(p_key);
  c public.web_phone_changes; o text; n text; ok text; nk text; prob text;
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  if p_action not in ('approve', 'refuse') then return jsonb_build_object('ok', false, 'code', 'BAD_ACTION'); end if;
  select * into c from public.web_phone_changes where id = p_id for update;
  if not found then return jsonb_build_object('ok', false, 'code', 'NOT_FOUND'); end if;
  if c.status <> 'pending' then return jsonb_build_object('ok', false, 'code', 'ALREADY_DONE', 'status', c.status); end if;

  if p_action = 'refuse' then
    update public.web_phone_changes set status = 'refused', decided_at = now(), note = left(coalesce(p_note, ''), 300) where id = c.id;
    return jsonb_build_object('ok', true, 'status', 'refused');
  end if;

  prob := public._web_phone_change_problem(c.user_id, c.new_phone);
  if prob is not null then return jsonb_build_object('ok', false, 'code', prob); end if;
  select phone into o from public.web_users where id = c.user_id for update;
  n := public._web_norm(c.new_phone);
  ok := public.vg_phone_key(o); nk := public.vg_phone_key(n);

  -- The account.
  update public.web_users set phone = n where id = c.user_id;
  -- What other members were given: point to the new number (a member who somehow has both keeps one row).
  delete from public.web_received r
   where public.vg_phone_key(r.phone) = ok
     and exists (select 1 from public.web_received r2 where r2.user_id = r.user_id and public.vg_phone_key(r2.phone) = nk);
  update public.web_received set phone = n where public.vg_phone_key(phone) = ok;
  -- Saved day lists, so contact files made from now on carry the new number.
  update public.web_snapshot_contacts set phone = n where public.vg_phone_key(phone) = ok;
  -- Contact files handed out but not confirmed yet.
  update public.web_downloads d set picked = (
      select jsonb_agg(case when x ? 'phone' and public.vg_phone_key(x->>'phone') = ok
                            then jsonb_set(x, '{phone}', to_jsonb(n)) else x end order by ord)
        from jsonb_array_elements(d.picked) with ordinality t(x, ord))
   where d.status = 'pending' and jsonb_typeof(d.picked) = 'array' and jsonb_array_length(d.picked) > 0
     and exists (select 1 from jsonb_array_elements(d.picked) y where public.vg_phone_key(y->>'phone') = ok);
  -- The member's own purchases, and reports about them.
  update public.web_purchases set phone = n where user_id = c.user_id;
  update public.web_reports set target_phone = n, target_key = nk where target_key = ok;
  -- The new number must not be taken off phones as someone who left.
  delete from public.web_gone where public.vg_phone_key(phone) = nk;
  -- Tell other members' apps to swap the saved contact.
  insert into public.web_phone_swaps (old_phone, new_phone) values (o, n);
  -- Log in again with the new number (same password).
  delete from public.web_sessions where user_id = c.user_id;
  update public.web_phone_changes set status = 'approved', decided_at = now(), old_phone = o, new_phone = n,
         note = left(coalesce(p_note, ''), 300) where id = c.id;
  return jsonb_build_object('ok', true, 'status', 'approved', 'old_phone', o, 'new_phone', n);
end
$$;
revoke all on function public.web_admin_phone_change_decide(text, bigint, text, text) from public;
grant execute on function public.web_admin_phone_change_decide(text, bigint, text, text) to anon, authenticated;

-- For the logged-in member: changes for people they were given (also a chain, like A to B then B to C), oldest first.
create or replace function public.web_swaps_for_me(p_phone text, p_pin text, p_since timestamptz default null)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare a record; v_now timestamptz := now();
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  return jsonb_build_object('ok', true, 'now', v_now, 'swaps', coalesce((
    with recursive mine as (
      select s.id, s.old_phone, s.new_phone, s.swapped_at
        from public.web_phone_swaps s
       where exists (select 1 from public.web_received r
                      where r.user_id = a.uid and public.vg_phone_key(r.phone) = public.vg_phone_key(s.new_phone))
      union
      select s.id, s.old_phone, s.new_phone, s.swapped_at
        from public.web_phone_swaps s join mine m
          on public.vg_phone_key(s.new_phone) = public.vg_phone_key(m.old_phone) and s.id < m.id
    )
    select jsonb_agg(jsonb_build_object('old', m.old_phone, 'new', m.new_phone) order by m.id)
      from (select distinct * from mine where p_since is null or swapped_at > p_since - interval '5 minutes' limit 2000) m), '[]'::jsonb));
end
$$;
revoke all on function public.web_swaps_for_me(text, text, timestamptz) from public;
grant execute on function public.web_swaps_for_me(text, text, timestamptz) to anon, authenticated;

commit;
