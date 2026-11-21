-- ViewGrow: check everything (21 Nov 2026). Changes NOTHING in your database.
-- The first statement makes a small helper that exists only while this query runs (pg_temp), then it is gone.
-- Shows only what needs a look (PROBLEM / WARN / MISSING), plus INFO counts and a SUMMARY line.
-- Member ID numbers only, never phone numbers, so the result is safe to share.
create or replace function pg_temp.vg_run(q text, kind text) returns text language plpgsql as $f$
declare r text;
begin
  if kind in ('bad', 'warn') then
    execute format('select count(*)::text || ''|'' || coalesce(string_agg(x, '', '') filter (where rn <= 8), '''') from (select x, row_number() over () as rn from (%s) t) s', q) into r;
  else
    execute q into r;
  end if;
  return coalesce(r, '');
exception when others then
  return 'ERROR|' || sqlerrm;
end $f$;

with called(fn) as (values ('web_admin_backup'), ('web_admin_ban'), ('web_admin_change_key'), ('web_admin_contacts_set'), ('web_admin_delete_user'), ('web_admin_deleted_list'), ('web_admin_deleted_restore'), ('web_admin_device_flag_allow'), ('web_admin_device_flag_dismiss'), ('web_admin_device_flags'), ('web_admin_grant_redownload'), ('web_admin_group'), ('web_admin_limit_set'), ('web_admin_log_verified'), ('web_admin_log_verified_day'), ('web_admin_new_members'), ('web_admin_new_members_done'), ('web_admin_phone_change_decide'), ('web_admin_phone_changes'), ('web_admin_pool_days'), ('web_admin_pool_list'), ('web_admin_release_get'), ('web_admin_release_now'), ('web_admin_release_set'), ('web_admin_report_close'), ('web_admin_report_limit'), ('web_admin_reports'), ('web_admin_reports_closed'), ('web_admin_reset_pin'), ('web_admin_run_snapshot'), ('web_admin_saved_add'), ('web_admin_saved_delete'), ('web_admin_saved_list'), ('web_admin_settings_get'), ('web_admin_settings_set'), ('web_admin_stats'), ('web_admin_unlog_verified'), ('web_admin_user'), ('web_admin_users'), ('web_admin_vip_delete'), ('web_admin_vip_list'), ('web_admin_vip_save'), ('web_app_restore'), ('web_app_seen'), ('web_boot'), ('web_confirm_download'), ('web_day_contacts'), ('web_delete_account'), ('web_device_seen'), ('web_gone_for_me'), ('web_history'), ('web_last_release'), ('web_login'), ('web_logout'), ('web_next_release'), ('web_people'), ('web_phone_change_cancel'), ('web_phone_change_mine'), ('web_phone_change_request'), ('web_prepare_download'), ('web_public_config'), ('web_register'), ('web_register_device'), ('web_report'), ('web_set_name'), ('web_status'), ('web_swaps_for_me'), ('web_vip_numbers')),
fns as (
  select 2 as n, '2 Functions the code calls' as area, c.fn as item,
         case when not exists (select 1 from pg_proc p where p.pronamespace = 'public'::regnamespace and p.proname = c.fn) then 'MISSING'
              when not exists (select 1 from pg_proc p where p.pronamespace = 'public'::regnamespace and p.proname = c.fn and has_function_privilege('anon', p.oid, 'execute')) then 'PROBLEM'
              else 'OK' end as status,
         'website/admin/app calls this' as detail
    from called c),
tbls(t) as (values ('web_users'), ('web_verifications'), ('web_day_releases'), ('web_snapshot_contacts'), ('web_snapshot_meta'), ('web_received'), ('web_downloads'), ('web_redownload_grants'), ('web_settings'), ('web_release_config'), ('web_signup_log'), ('web_admin_attempts'), ('web_sessions'), ('web_purchases'), ('deleted_numbers'), ('admin_saved_messages'), ('web_vip_contacts'), ('web_devices'), ('web_banned_devices'), ('web_device_flags'), ('web_device_allow'), ('web_gone'), ('web_reports'), ('web_phone_changes'), ('web_phone_swaps')),
tbl as (
  select 4, '4 Tables', t.t,
         case when c.oid is null then 'MISSING' when not c.relrowsecurity then 'PROBLEM'
              when exists (select 1 from pg_policies p where p.schemaname = 'public' and p.tablename = t.t) then 'PROBLEM'
              when has_table_privilege('anon', c.oid, 'select') or has_table_privilege('anon', c.oid, 'insert,update,delete') then 'WARN' else 'OK' end,
         case when c.oid is null then 'table missing' when not c.relrowsecurity then 'row security is OFF'
              when exists (select 1 from pg_policies p where p.schemaname = 'public' and p.tablename = t.t) then 'has access policies'
              when has_table_privilege('anon', c.oid, 'select') or has_table_privilege('anon', c.oid, 'insert,update,delete') then 'public key has table permissions (blocked by row security)' else 'ok' end
    from tbls t left join pg_class c on c.oid = to_regclass('public.' || t.t)),
trgs(tb, tg) as (values ('web_users', 'web_users_gone_del'), ('web_users', 'web_users_gone_ins'), ('web_users', 'web_users_gone_ban'), ('web_users', 'web_users_sessions_revoke'), ('web_users', 'web_users_ban_devices'), ('web_devices', 'web_devices_ban_new'), ('web_sessions', 'web_sessions_device')),
trg as (
  select 5, '5 Triggers', g.tg || ' on ' || g.tb,
         case when not exists (select 1 from pg_trigger x where not x.tgisinternal and x.tgname = g.tg and x.tgrelid = to_regclass('public.' || g.tb)) then 'MISSING'
              when exists (select 1 from pg_trigger x where x.tgname = g.tg and x.tgenabled = 'D') then 'PROBLEM' else 'OK' end,
         'keeps bans, logins and phones in step'
    from trgs g),
checks(n, area, item, kind, q) as (values
  (100, '1 Updates', '20261116180000 delete account', 'yes', 'select (to_regprocedure(''public.web_delete_account(text,text)'') is not null)::text'),
  (101, '1 Updates', '20261117100000 saved messages', 'yes', 'select (to_regclass(''public.admin_saved_messages'') is not null)::text'),
  (102, '1 Updates', '20261117140000 login tokens', 'yes', 'select (to_regprocedure(''public._web_auth_pin(text,text)'') is not null)::text'),
  (103, '1 Updates', '20261119160000 VIP contacts', 'yes', 'select (to_regclass(''public.web_vip_contacts'') is not null)::text'),
  (104, '1 Updates', '20261121000000 phone format', 'yes', 'select (to_regprocedure(''public._web_fmt(text)'') is not null)::text'),
  (105, '1 Updates', '20261121010000 skip admin/VIP numbers', 'yes', 'select (position(''_web_is_admin_number'' in pg_get_functiondef(''public._web_candidates(bigint)''::regprocedure)) > 0)::text'),
  (106, '1 Updates', '20261121020000 device ban', 'yes', 'select (to_regclass(''public.web_banned_devices'') is not null)::text'),
  (107, '1 Updates', '20261121030000 one account per phone', 'yes', 'select (to_regclass(''public.web_device_flags'') is not null)::text'),
  (108, '1 Updates', '20261121040000 departed off phones', 'yes', 'select (to_regclass(''public.web_gone'') is not null)::text'),
  (109, '1 Updates', '20261121050000 reports + deleted list', 'yes', 'select (to_regclass(''public.web_reports'') is not null)::text'),
  (110, '1 Updates', '20261121060000 reports Closed tab', 'yes', 'select (to_regprocedure(''public.web_admin_reports_closed(text)'') is not null)::text'),
  (111, '1 Updates', '20261121070000 admin change key', 'yes', 'select (to_regprocedure(''public.web_admin_change_key(text,text)'') is not null)::text'),
  (112, '1 Updates', '20261121080000 phone change', 'yes', 'select (to_regclass(''public.web_phone_swaps'') is not null)::text'),
  (113, '3 Security', 'Internal functions (_web_*) the public key can run', 'bad', 'select proname as x from pg_proc where pronamespace = ''public''::regnamespace and proname like ''\_web\_%'' and has_function_privilege(''anon'', oid, ''execute'')'),
  (114, '3 Security', 'Blocked functions the public key can run', 'bad', 'select proname as x from pg_proc where pronamespace = ''public''::regnamespace and proname in (''web_register_core'',''web_status_core'',''web_purchase_precheck'',''web_apply_purchase'',''web_nightly_snapshot'',''web_take_snapshot'') and has_function_privilege(''anon'', oid, ''execute'')'),
  (115, '3 Security', 'SECURITY DEFINER functions with no fixed search_path', 'warn', 'select proname as x from pg_proc where pronamespace = ''public''::regnamespace and prosecdef and not exists (select 1 from unnest(coalesce(proconfig, ''{}'')) c where c like ''search_path=%'')'),
  (116, '3 Security', 'Old web_admin_set_key still present', 'bad', 'select proname as x from pg_proc where pronamespace = ''public''::regnamespace and proname = ''web_admin_set_key'''),
  (117, '3 Security', 'Admin key is set', 'yes', 'select exists (select 1 from public.web_settings where key = ''admin_key_hash'' and coalesce(value, '''') <> '''')::text'),
  (118, '3 Security', 'Admin connections locked right now', 'warn', 'select ''locked'' as x from public.web_admin_attempts where locked_until > now()'),
  (119, '6 Versions', 'web_ functions in more than one version', 'warn', 'select proname || '' x'' || count(*) as x from pg_proc where pronamespace = ''public''::regnamespace and proname ~ ''^_?web_'' group by proname having count(*) > 1'),
  (120, '7 Nightly job', 'viewgrow-snapshot job is active', 'yes', 'select coalesce(bool_and(active), false)::text from cron.job where jobname = ''viewgrow-snapshot'''),
  (121, '7 Nightly job', 'Last 5 runs (Lagos time)', 'info', 'select coalesce(string_agg(to_char(start_time at time zone ''Africa/Lagos'', ''DD Mon HH24:MI'') || '' '' || status, '' · '' order by start_time desc), ''none'') from (select r.start_time, r.status from cron.job_run_details r join cron.job j on j.jobid = r.jobid where j.jobname = ''viewgrow-snapshot'' order by r.start_time desc limit 5) z'),
  (122, '7 Nightly job', 'Failed runs in the last 7 days', 'bad', 'select to_char(r.start_time at time zone ''Africa/Lagos'', ''DD Mon'') as x from cron.job_run_details r join cron.job j on j.jobid = r.jobid where j.jobname = ''viewgrow-snapshot'' and r.status <> ''succeeded'' and r.start_time > now() - interval ''7 days'''),
  (123, '8 Settings', 'report_ban_count, default_limit, signup_limit, signup_open have valid values', 'bad', 'select k as x from unnest(array[''report_ban_count'',''default_limit'',''signup_limit'',''signup_open'']) k where not exists (select 1 from public.web_settings s where s.key = k and (s.value ~ ''^[0-9]+$'' or (k = ''signup_open'' and s.value in (''true'',''false''))))'),
  (124, '8 Settings', 'Current values', 'info', 'select string_agg(key || ''='' || value, '' · '' order by key) from public.web_settings where key in (''report_ban_count'',''default_limit'',''signup_limit'',''signup_open'')'),
  (125, '9 Members', 'Members in total · banned · locked', 'info', 'select count(*) || '' · '' || count(*) filter (where banned) || '' · '' || count(*) filter (where locked) from public.web_users'),
  (126, '9 Members', 'Two accounts with the same number', 'bad', 'select string_agg(id::text, ''+'') as x from public.web_users group by public.vg_phone_key(phone) having count(*) > 1'),
  (127, '9 Members', 'Numbers not in the +234 … form', 'bad', 'select id::text as x from public.web_users where phone !~ ''^\+'''),
  (128, '9 Members', 'Members whose number is on the deleted list', 'bad', 'select u.id::text as x from public.web_users u join public.deleted_numbers d on public.vg_phone_key(d.phone) = public.vg_phone_key(u.phone)'),
  (129, '9 Members', 'Members whose number is an admin or VIP number', 'warn', 'select id::text as x from public.web_users where public._web_is_admin_number(phone)'),
  (130, '9 Members', 'Personal limits above 100,000', 'warn', 'select id::text as x from public.web_users where limit_override > 100000'),
  (131, '10 Contacts given', 'Admin or VIP numbers handed out as normal contacts', 'bad', 'select distinct user_id::text as x from public.web_received where public._web_is_admin_number(phone)'),
  (132, '10 Contacts given', 'Given numbers not in the +234 … form', 'bad', 'select distinct user_id::text as x from public.web_received where phone !~ ''^\+'''),
  (133, '10 Contacts given', 'The same person given twice to one member', 'bad', 'select user_id::text as x from public.web_received group by user_id, public.vg_phone_key(phone) having count(*) > 1'),
  (134, '10 Contacts given', 'Members given their own number', 'warn', 'select r.user_id::text as x from public.web_received r join public.web_users u on u.id = r.user_id where public.vg_phone_key(r.phone) = public.vg_phone_key(u.phone)'),
  (135, '10 Contacts given', 'Saved day lists with numbers not in the +234 … form', 'bad', 'select distinct to_char(day, ''DD Mon'') as x from public.web_snapshot_contacts where phone !~ ''^\+'''),
  (136, '10 Contacts given', 'Contact files handed out but not confirmed for 2+ days', 'warn', 'select id::text as x from public.web_downloads where status = ''pending'' and created_at < now() - interval ''2 days'''),
  (137, '11 Left or banned', 'Banned members missing from the remove-from-phones list', 'bad', 'select u.id::text as x from public.web_users u where u.banned and not exists (select 1 from public.web_gone g where public.vg_phone_key(g.phone) = public.vg_phone_key(u.phone))'),
  (138, '11 Left or banned', 'Active members wrongly on the remove-from-phones list', 'bad', 'select u.id::text as x from public.web_users u join public.web_gone g on public.vg_phone_key(g.phone) = public.vg_phone_key(u.phone) where not u.banned'),
  (139, '12 Logins', 'Login tokens of members who no longer exist', 'bad', 'select s.id::text as x from public.web_sessions s where not exists (select 1 from public.web_users u where u.id = s.user_id)'),
  (140, '12 Logins', 'Members with more than 10 login tokens', 'warn', 'select user_id::text as x from public.web_sessions group by user_id having count(*) > 10'),
  (141, '13 Reports', 'People waiting for your decision', 'info', 'select count(distinct target_key)::text from public.web_reports where status = ''open'''),
  (142, '13 Reports', 'Unknown report status values', 'bad', 'select distinct status as x from public.web_reports where status not in (''open'',''dismissed'',''actioned'')'),
  (143, '13 Reports', 'Banned by reports with fewer reports than the auto-ban number', 'warn', 'select u.id::text as x from public.web_users u where u.banned and u.ban_reason = ''reported'' and (select count(distinct r.reporter_id) from public.web_reports r where r.target_key = public.vg_phone_key(u.phone)) < (select coalesce(max(value)::int, 3) from public.web_settings where key = ''report_ban_count'' and value ~ ''^[0-9]+$'')'),
  (144, '14 Phone changes', 'Waiting for your approval', 'info', 'select count(*)::text from public.web_phone_changes where status = ''pending'''),
  (145, '14 Phone changes', 'Waiting requests that can no longer be approved', 'warn', 'select c.id::text as x from public.web_phone_changes c where c.status = ''pending'' and public._web_phone_change_problem(c.user_id, c.new_phone) is not null'),
  (146, '14 Phone changes', 'Approved changes where the account lacks the new number', 'bad', 'select c.id::text as x from public.web_phone_changes c join public.web_users u on u.id = c.user_id where c.status = ''approved'' and public.vg_phone_key(u.phone) <> public.vg_phone_key(c.new_phone) and not exists (select 1 from public.web_phone_changes c2 where c2.user_id = c.user_id and c2.status = ''approved'' and c2.decided_at > c.decided_at)'),
  (147, '15 Devices', 'Flagged sign-ups waiting', 'info', 'select count(*)::text from public.web_device_flags where status = ''open'''),
  (148, '15 Devices', 'Deleted numbers not in the +234 … form', 'bad', 'select ''row'' as x from public.deleted_numbers where phone !~ ''^\+'''),
  (149, '16 Purchases', 'Purchases by members who no longer exist', 'warn', 'select p.user_id::text as x from public.web_purchases p where not exists (select 1 from public.web_users u where u.id = p.user_id)')
),
ran as (select n, area, item, kind, pg_temp.vg_run(q, kind) as v from checks),
dat as (
  select n, area, item,
         case when v like 'ERROR|%' then 'MISSING'
              when kind = 'yes' then case when v = 'true' then 'OK' else 'PROBLEM' end
              when kind = 'info' then 'INFO'
              when split_part(v, '|', 1) = '0' then 'OK'
              when kind = 'bad' then 'PROBLEM' else 'WARN' end as status,
         case when v like 'ERROR|%' then 'cannot check: ' || substr(v, 7)
              when kind in ('yes', 'info') then v
              when split_part(v, '|', 1) = '0' then 'none'
              else split_part(v, '|', 1) || coalesce(nullif(' · e.g. ' || split_part(v, '|', 2), ' · e.g. '), '') end as detail
    from ran),
alls as (select * from fns union all select * from tbl union all select * from trg union all select * from dat)
select n, area, item, status, detail from alls where status <> 'OK'
union all
select 9999, 'SUMMARY', count(*) || ' checks', 'OK ' || count(*) filter (where status = 'OK'),
       'PROBLEM ' || count(*) filter (where status = 'PROBLEM') || ' · MISSING ' || count(*) filter (where status = 'MISSING')
       || ' · WARN ' || count(*) filter (where status = 'WARN') || ' · INFO ' || count(*) filter (where status = 'INFO')
  from alls
order by 1, 2, 3;
