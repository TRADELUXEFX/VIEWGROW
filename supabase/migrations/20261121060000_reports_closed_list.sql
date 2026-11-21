-- Reports: the "Closed" tab. Lists people whose reports the admin already closed (dismissed or actioned),
-- newest first, up to 100. A person with open reports again shows under To check instead, not here.
-- Safe to run twice (create or replace). Nothing is changed or deleted.
do $$
begin
  if to_regclass('public.web_reports') is null or to_regprocedure('public._web_admin_ok(text)') is null
     or to_regprocedure('public.vg_phone_key(text)') is null then
    raise exception 'Stopped, nothing changed: web_reports, _web_admin_ok or vg_phone_key is missing. Run 20261121050000_reports_and_deleted_list.sql first.';
  end if;
end $$;

create or replace function public.web_admin_reports_closed(p_key text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare k text := public._web_admin_ok(p_key);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  return jsonb_build_object('ok', true, 'rows', coalesce((
    select jsonb_agg(g.j order by g.closed_at desc) from (
      select max(r.closed_at) as closed_at,
             jsonb_build_object(
               'key', r.target_key,
               'phone', min(r.target_phone),
               'user', (select jsonb_build_object('id', u.id, 'name', u.name, 'phone', u.phone, 'banned', u.banned, 'ban_reason', u.ban_reason)
                          from public.web_users u where public.vg_phone_key(u.phone) = r.target_key limit 1),
               'count', count(distinct r.reporter_id),
               'last_at', max(r.created_at),
               'closed_at', max(r.closed_at),
               'status', (array_agg(r.status order by r.closed_at desc))[1],
               'reports', jsonb_agg(jsonb_build_object('reason', r.reason, 'blocked', r.blocked, 'at', r.created_at,
                            'by_name', (select u2.name from public.web_users u2 where u2.id = r.reporter_id),
                            'by_phone', (select u2.phone from public.web_users u2 where u2.id = r.reporter_id))
                          order by r.created_at desc)) as j
        from public.web_reports r
       where r.status in ('dismissed', 'actioned')
         and not exists (select 1 from public.web_reports o where o.target_key = r.target_key and o.status = 'open')
       group by r.target_key
       order by max(r.closed_at) desc
       limit 100) g), '[]'::jsonb));
end
$$;
revoke all on function public.web_admin_reports_closed(text) from public;
grant execute on function public.web_admin_reports_closed(text) to anon, authenticated;
