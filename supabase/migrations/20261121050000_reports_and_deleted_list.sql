-- 20261121050000: report and block members, automatic ban after reports, and the admin's Deleted numbers list
-- Requested by the owner on 21 Nov 2026.
--
-- REPORTS
-- 1. A member can report a person they were given (only someone in their own web_received). Reasons: spam, scam,
--    harassment, sexual, under18, other. One report per member per person (reporting again updates it).
--    "Block" = the app also removes that person from the member's phone and does not put them back.
-- 2. Automatic ban: when the number of different members who reported the same person reaches the limit in
--    web_settings 'report_ban_count' (3 to start; 0 = never automatic), that member is banned with reason "reported".
--    A report of "under 18" bans at once with reason "under18_review" until the admin looks at it.
--    A ban here works like any ban: their phone is banned (device ban) and they come off other members' phones.
-- 3. Admin: web_admin_reports (open reports, grouped by person), web_admin_report_close (dismiss: closes the reports
--    and lifts a ban that reports caused; ban: bans now; keep: closes the reports and leaves things as they are),
--    web_admin_report_limit (change the automatic-ban number).
-- DELETED NUMBERS (people who deleted their own account; that number cannot sign up again)
-- 4. Admin: web_admin_deleted_list (the list, newest first, search by number) and web_admin_deleted_restore
--    ("Let back in": takes the number off the list so it can sign up again as a new member).
--
-- Nothing existing is edited. One transaction. Safe to run twice. Undo: rollback/reports_and_deleted_list_rollback.sql.

begin;

do $$
begin
  if to_regclass('public.web_received') is null or to_regclass('public.deleted_numbers') is null
     or to_regprocedure('public._web_auth(text,text)') is null or to_regprocedure('public._web_admin_ok(text)') is null
     or to_regprocedure('public.vg_phone_key(text)') is null
     or not exists (select 1 from information_schema.columns where table_schema = 'public' and table_name = 'web_users' and column_name = 'ban_reason')
     or not exists (select 1 from information_schema.columns where table_schema = 'public' and table_name = 'web_users' and column_name = 'banned_at') then
    raise exception 'Stopped, nothing changed: web_received, deleted_numbers, _web_auth, _web_admin_ok, vg_phone_key, web_users.ban_reason or web_users.banned_at is missing.';
  end if;
end $$;

create table if not exists public.web_reports (
  id          bigserial   primary key,
  reporter_id bigint      not null references public.web_users(id) on delete cascade,
  target_key  text        not null,                -- vg_phone_key of the reported number
  target_phone text       not null,
  reason      text        not null check (reason in ('spam', 'scam', 'harassment', 'sexual', 'under18', 'other')),
  blocked     boolean     not null default false,
  status      text        not null default 'open' check (status in ('open', 'dismissed', 'actioned')),
  created_at  timestamptz not null default now(),
  closed_at   timestamptz,
  unique (reporter_id, target_key)
);
create index if not exists web_reports_target_idx on public.web_reports(target_key, status);
alter table public.web_reports enable row level security;
revoke all on public.web_reports from public, anon, authenticated;

insert into public.web_settings (key, value)
select 'report_ban_count', '3'
 where not exists (select 1 from public.web_settings where key = 'report_ban_count');

create or replace function public._web_report_limit()
returns int
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select coalesce((select nullif(regexp_replace(value, '\D', '', 'g'), '')::int from public.web_settings where key = 'report_ban_count'), 3)
$$;
revoke all on function public._web_report_limit() from public, anon, authenticated;

-- Member: report (and optionally block) someone they were given.
create or replace function public.web_report(p_phone text, p_pin text, p_target text, p_reason text, p_block boolean default false)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare a record; k text := public.vg_phone_key(p_target); v_target text; t public.web_users; n int; lim int := public._web_report_limit();
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  if coalesce(k, '') = '' then return jsonb_build_object('ok', false, 'code', 'BAD_NUMBER'); end if;
  if p_reason is null or p_reason not in ('spam', 'scam', 'harassment', 'sexual', 'under18', 'other') then
    return jsonb_build_object('ok', false, 'code', 'BAD_REASON');
  end if;
  select r.phone into v_target from public.web_received r
   where r.user_id = a.uid and public.vg_phone_key(r.phone) = k limit 1;
  if v_target is null then return jsonb_build_object('ok', false, 'code', 'NOT_YOUR_CONTACT'); end if;

  insert into public.web_reports (reporter_id, target_key, target_phone, reason, blocked)
  values (a.uid, k, v_target, p_reason, coalesce(p_block, false))
  on conflict (reporter_id, target_key) do update
     set reason = excluded.reason, blocked = public.web_reports.blocked or excluded.blocked,
         status = case when public.web_reports.status = 'dismissed' then 'open' else public.web_reports.status end,
         created_at = now(), closed_at = null;

  select * into t from public.web_users u where public.vg_phone_key(u.phone) = k limit 1;
  if found and not coalesce(t.banned, false) then
    if p_reason = 'under18' then
      update public.web_users set banned = true, ban_reason = 'under18_review', banned_at = now() where id = t.id;
    else
      select count(distinct reporter_id) into n from public.web_reports where target_key = k and status <> 'dismissed';
      if lim > 0 and n >= lim then
        update public.web_users set banned = true, ban_reason = 'reported', banned_at = now() where id = t.id;
      end if;
    end if;
  end if;
  return jsonb_build_object('ok', true);
end
$$;
revoke all on function public.web_report(text, text, text, text, boolean) from public;
grant execute on function public.web_report(text, text, text, text, boolean) to anon, authenticated;

-- Admin: open reports grouped by the reported person, newest first.
create or replace function public.web_admin_reports(p_key text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_key);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  return jsonb_build_object('ok', true, 'limit', public._web_report_limit(), 'rows', coalesce((
    select jsonb_agg(g.j order by g.last_at desc) from (
      select max(r.created_at) as last_at,
             jsonb_build_object(
               'key', r.target_key,
               'phone', min(r.target_phone),
               'user', (select jsonb_build_object('id', u.id, 'name', u.name, 'phone', u.phone, 'banned', u.banned, 'ban_reason', u.ban_reason)
                          from public.web_users u where public.vg_phone_key(u.phone) = r.target_key limit 1),
               'count', count(distinct r.reporter_id),
               'last_at', max(r.created_at),
               'reports', jsonb_agg(jsonb_build_object('reason', r.reason, 'blocked', r.blocked, 'at', r.created_at,
                            'by_name', (select u2.name from public.web_users u2 where u2.id = r.reporter_id),
                            'by_phone', (select u2.phone from public.web_users u2 where u2.id = r.reporter_id))
                          order by r.created_at desc)) as j
        from public.web_reports r
       where r.status = 'open'
       group by r.target_key) g), '[]'::jsonb));
end
$$;
revoke all on function public.web_admin_reports(text) from public;
grant execute on function public.web_admin_reports(text) to anon, authenticated;

-- Admin: close the open reports about one person. p_action: 'dismiss' (lifts a ban that reports caused),
-- 'ban' (bans now, reason "reported"), 'keep' (closes, changes nothing else).
create or replace function public.web_admin_report_close(p_key text, p_target_key text, p_action text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_key); t public.web_users;
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  if p_action not in ('dismiss', 'ban', 'keep') then return jsonb_build_object('ok', false, 'code', 'BAD_ACTION'); end if;
  select * into t from public.web_users u where public.vg_phone_key(u.phone) = p_target_key limit 1;
  if p_action = 'dismiss' then
    update public.web_reports set status = 'dismissed', closed_at = now() where target_key = p_target_key and status = 'open';
    if found and t.id is not null and t.banned and t.ban_reason in ('reported', 'under18_review') then
      update public.web_users set banned = false, ban_reason = null, banned_at = null where id = t.id;
    end if;
  else
    if p_action = 'ban' and t.id is not null and not coalesce(t.banned, false) then
      update public.web_users set banned = true, ban_reason = 'reported', banned_at = now() where id = t.id;
    end if;
    update public.web_reports set status = 'actioned', closed_at = now() where target_key = p_target_key and status = 'open';
  end if;
  return jsonb_build_object('ok', true);
end
$$;
revoke all on function public.web_admin_report_close(text, text, text) from public;
grant execute on function public.web_admin_report_close(text, text, text) to anon, authenticated;

-- Admin: how many different members' reports ban someone automatically (0 = never, 2 to 20).
create or replace function public.web_admin_report_limit(p_key text, p_count int)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_key);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  if p_count is null or not (p_count = 0 or p_count between 2 and 20) then return jsonb_build_object('ok', false, 'code', 'BAD_NUMBER'); end if;
  update public.web_settings set value = p_count::text where key = 'report_ban_count';
  if not found then insert into public.web_settings (key, value) values ('report_ban_count', p_count::text); end if;
  return jsonb_build_object('ok', true, 'limit', p_count);
end
$$;
revoke all on function public.web_admin_report_limit(text, int) from public;
grant execute on function public.web_admin_report_limit(text, int) to anon, authenticated;

-- Admin: the deleted-numbers list (people who deleted their own account), newest first.
create or replace function public.web_admin_deleted_list(p_key text, p_search text default '')
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_key); d text := regexp_replace(regexp_replace(coalesce(p_search, ''), '\D', '', 'g'), '^0', '');
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  return jsonb_build_object('ok', true,
    'total', (select count(*) from public.deleted_numbers),
    'rows', coalesce((select jsonb_agg(jsonb_build_object('phone', x.phone, 'deleted_at', x.deleted_at) order by x.deleted_at desc)
                        from (select phone, deleted_at from public.deleted_numbers
                               where d = '' or regexp_replace(phone, '\D', '', 'g') like '%' || right(d, 10) || '%'
                               order by deleted_at desc limit 200) x), '[]'::jsonb));
end
$$;
revoke all on function public.web_admin_deleted_list(text, text) from public;
grant execute on function public.web_admin_deleted_list(text, text) to anon, authenticated;

-- Admin: "Let back in" takes a number off the deleted list; it can then sign up again as a new member.
create or replace function public.web_admin_deleted_restore(p_key text, p_phone text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_key); n int;
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  if coalesce(public.vg_phone_key(p_phone), '') = '' then return jsonb_build_object('ok', false, 'code', 'BAD_NUMBER'); end if;
  delete from public.deleted_numbers where public.vg_phone_key(phone) = public.vg_phone_key(p_phone);
  get diagnostics n = row_count;
  return jsonb_build_object('ok', true, 'removed', n);
end
$$;
revoke all on function public.web_admin_deleted_restore(text, text) from public;
grant execute on function public.web_admin_deleted_restore(text, text) to anon, authenticated;

commit;
