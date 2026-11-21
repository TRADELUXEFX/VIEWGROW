-- rollback/tidy_leftovers_rollback.sql: puts back the 5 unused functions removed by 20261121100000_tidy_leftovers.sql,
-- exactly as they were live on 21 Nov 2026. (The deleted_numbers id column can stay; nothing depends on it being absent.)
begin;
CREATE OR REPLACE FUNCTION public._web_contacts(p_uid bigint, p_days date[])
 RETURNS TABLE(phone text, name text, day date)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select t.phone, t.name, t.day from (
    select distinct on (s.phone) s.phone, s.name, s.day, s.id
      from public.web_snapshot_contacts s, public.web_users u
     where u.id = p_uid and s.day = any (p_days) and s.phone <> u.phone
       and exists (select 1 from public.web_day_releases r where r.day = s.day)
       and not exists (select 1 from public.web_users b where b.phone = s.phone and b.banned)
     order by s.phone, s.day, s.id
  ) t order by t.day, t.id
$function$;
revoke all on function public._web_contacts(bigint, date[]) from public, anon, authenticated;

CREATE OR REPLACE FUNCTION public._web_days(p_uid bigint, p_full boolean)
 RETURNS date[]
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select coalesce(array_agg(v.day order by v.day), '{}'::date[])
    from public.web_verifications v
    join public.web_users u on u.id = v.user_id
    join public.web_day_releases r on r.day = v.day
   where v.user_id = p_uid
     and v.day >= u.registered_on
     and v.day <= public._web_today()
     and (p_full or not exists (
           select 1 from public.web_downloads d
            where d.user_id = p_uid and d.status = 'done' and v.day = any (d.days)))
$function$;
revoke all on function public._web_days(bigint, boolean) from public, anon, authenticated;

CREATE OR REPLACE FUNCTION public.web_admin_contact_set(p_key text, p_channel text, p_value text, p_label text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare k text := public._web_admin_ok(p_key); n text;
  v text := trim(coalesce(p_value, ''));
  lb text := left(trim(coalesce(p_label, '')), 40);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  if p_channel not in ('whatsapp_dm', 'whatsapp_group', 'telegram_dm', 'telegram_channel', 'other_dm') then
    return jsonb_build_object('ok', false, 'code', 'INVALID_CHANNEL');
  end if;
  if p_channel = 'whatsapp_dm' then
    n := public._web_norm(v);
    if n is null then return jsonb_build_object('ok', false, 'code', 'INVALID_PHONE'); end if;
    v := n;
    update public.web_settings set value = n where key = 'admin_wa_phone';
  else
    if length(v) > 300 or v !~ '^https://[^[:space:]]+$' then
      return jsonb_build_object('ok', false, 'code', 'INVALID_LINK');
    end if;
  end if;
  if p_channel = 'other_dm' and lb = '' then
    return jsonb_build_object('ok', false, 'code', 'LABEL_REQUIRED');
  end if;
  insert into public.web_settings (key, value) values
    ('contact_channel', p_channel), ('contact_value', v), ('contact_label', case when p_channel = 'other_dm' then lb else '' end)
  on conflict (key) do update set value = excluded.value;
  return jsonb_build_object('ok', true);
end $function$;
revoke all on function public.web_admin_contact_set(text, text, text, text) from public, anon, authenticated;
grant execute on function public.web_admin_contact_set(text, text, text, text) to anon, authenticated;

CREATE OR REPLACE FUNCTION public.web_admin_user_limit(p_key text, p_id bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare k text := public._web_admin_ok(p_key); u public.web_users;
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  select * into u from public.web_users where id = p_id;
  if not found then return jsonb_build_object('ok', false, 'code', 'NOT_FOUND'); end if;
  return jsonb_build_object('ok', true,
    'received', public._web_received_count(p_id),
    'limit', public._web_limit(p_id),
    'personal', u.limit_override,
    'capped', public._web_received_count(p_id) >= public._web_limit(p_id));
end
$function$;
revoke all on function public.web_admin_user_limit(text, bigint) from public, anon, authenticated;
grant execute on function public.web_admin_user_limit(text, bigint) to anon, authenticated;

CREATE OR REPLACE FUNCTION public.web_setup(p_admin_key text, p_wa_phone text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'extensions', 'pg_temp'
AS $function$
declare n text := public._web_norm(p_wa_phone);
begin
  if coalesce((select value from public.web_settings where key = 'admin_key_hash'), '') <> '' then
    raise exception 'Setup already done. Use web_admin_set_key to change the key.';
  end if;
  if p_admin_key is null or length(p_admin_key) < 10 or upper(p_admin_key) like '%CHANGE%' then
    raise exception 'Admin key must be at least 10 characters and must not be the placeholder';
  end if;
  if n is null then raise exception 'WhatsApp number is not valid'; end if;
  update public.web_settings set value = crypt(p_admin_key, gen_salt('bf', 10)) where key = 'admin_key_hash';
  update public.web_settings set value = n where key = 'admin_wa_phone';
  update public.web_settings set value = '0' where key = 'admin_fail_count';
  update public.web_settings set value = '' where key = 'admin_locked_until';
  return 'Setup done. Admin WhatsApp: ' || n;
end $function$;
revoke all on function public.web_setup(text, text) from public, anon, authenticated;

commit;
