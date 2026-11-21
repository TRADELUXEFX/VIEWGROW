-- 20261121096000_record_live_functions_2.sql
-- RECORD ONLY: these 14 functions were made or improved in the Supabase dashboard without a code file. This file holds
-- the live code exactly as exported on 21 Nov 2026 (the owner pasted the result of a read-only query), so the files
-- match the database again.
-- It is already live: there is no need to run it. Running it changes nothing (same code, create or replace).
--   _web_clean, _web_client_ip, _web_name_key, _web_name_suggestions, _web_pin_new, _web_vcf_esc   small helpers
--   web_admin_ban, web_admin_contacts_set, web_admin_delete_user, web_admin_grant_redownload,
--   web_admin_pool_days, web_admin_run_snapshot, web_admin_unlog_verified                           admin page
--   web_public_config                                                                                public settings for the site
-- Note: web_admin_delete_user does not delete purchases itself; since 20261121110000 the link on web_purchases
-- (ON DELETE CASCADE) removes them with the member.

CREATE OR REPLACE FUNCTION public._web_clean(p text, p_max integer)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$ select left(btrim(regexp_replace(regexp_replace(coalesce(p, ''), '[\x00-\x1f\x7f]', ' ', 'g'), '\s+', ' ', 'g')), p_max) $function$;


CREATE OR REPLACE FUNCTION public._web_client_ip()
 RETURNS text
 LANGUAGE plpgsql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare h json; ip text;
begin
  begin
    h := nullif(current_setting('request.headers', true), '')::json;
    ip := btrim(split_part(coalesce(h->>'x-forwarded-for', ''), ',', 1));
  exception when others then
    ip := null;
  end;
  return nullif(ip, '');
end $function$;


CREATE OR REPLACE FUNCTION public._web_name_key(p text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$
  select nullif(regexp_replace(regexp_replace(lower(btrim(regexp_replace(coalesce(p, ''), '\s+', ' ', 'g'))), '[\s._-]*vg[\s.]*$', ''), '[\s._-]+$', ''), '')
$function$;


CREATE OR REPLACE FUNCTION public._web_name_suggestions(p_name text)
 RETURNS text[]
 LANGUAGE plpgsql
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare i integer; cand text; out_names text[] := '{}';
begin
  -- the first three free "name 2", "name 3", ... options for a name that is already taken
  for i in 2..200 loop
    cand := p_name || ' ' || i;
    if not exists (select 1 from public.web_users where public._web_name_key(name) = public._web_name_key(cand)) then
      out_names := out_names || cand;
      exit when cardinality(out_names) >= 3;
    end if;
  end loop;
  return out_names;
end
$function$;


CREATE OR REPLACE FUNCTION public._web_pin_new()
 RETURNS text
 LANGUAGE sql
 SET search_path TO 'public', 'extensions'
AS $function$
  select lpad(((get_byte(b, 0) * 65536 + get_byte(b, 1) * 256 + get_byte(b, 2)) % 1000000)::text, 6, '0')
    from (select gen_random_bytes(3) as b) t
$function$;


CREATE OR REPLACE FUNCTION public._web_vcf_esc(p text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$ select replace(replace(replace(replace(replace(coalesce(p, ''), E'\\', E'\\\\'), ';', E'\\;'), ',', E'\\,'), E'\r', ''), E'\n', E'\\n') $function$;


CREATE OR REPLACE FUNCTION public.web_admin_ban(p_key text, p_id bigint, p_banned boolean, p_reason text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare k text := public._web_admin_ok(p_key);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  update public.web_users
     set banned = coalesce(p_banned, true),
         ban_reason = case when coalesce(p_banned, true) then public._web_clean(p_reason, 200) else null end,
         banned_at  = case when coalesce(p_banned, true) then now() else null end
   where id = p_id;
  if not found then return jsonb_build_object('ok', false, 'code', 'NOT_FOUND'); end if;
  return jsonb_build_object('ok', true);
end $function$;


CREATE OR REPLACE FUNCTION public.web_admin_contacts_set(p_key text, p_contacts jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare k text := public._web_admin_ok(p_key);
  e jsonb; ch text; v text; lb text; n text;
  out jsonb := '[]'::jsonb;
  seen text[] := '{}';
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  for e in select * from jsonb_array_elements(coalesce(p_contacts, '[]'::jsonb)) loop
    continue when coalesce((e->>'on')::boolean, false) = false;
    ch := e->>'channel';
    v  := trim(coalesce(e->>'value', ''));
    lb := left(trim(coalesce(e->>'label', '')), 40);
    if ch not in ('whatsapp_dm', 'whatsapp_group', 'telegram_dm', 'telegram_channel', 'other_dm') then
      return jsonb_build_object('ok', false, 'code', 'INVALID_CHANNEL');
    end if;
    if ch = any(seen) then return jsonb_build_object('ok', false, 'code', 'DUPLICATE'); end if;
    seen := seen || ch;
    if ch = 'whatsapp_dm' then
      n := public._web_norm(v);
      if n is null then return jsonb_build_object('ok', false, 'code', 'INVALID_PHONE'); end if;
      v := n;
      update public.web_settings set value = n where key = 'admin_wa_phone';
    else
      if length(v) > 300 or v !~ '^https://[^[:space:]]+$' then
        return jsonb_build_object('ok', false, 'code', 'INVALID_LINK');
      end if;
    end if;
    if ch = 'other_dm' and lb = '' then
      return jsonb_build_object('ok', false, 'code', 'LABEL_REQUIRED');
    end if;
    out := out || jsonb_build_array(jsonb_build_object(
      'channel', ch, 'value', v, 'label', case when ch = 'other_dm' then lb else '' end, 'on', true));
  end loop;
  update public.web_settings set value = out::text where key = 'contacts';
  return jsonb_build_object('ok', true);
end $function$;


CREATE OR REPLACE FUNCTION public.web_admin_delete_user(p_key text, p_id bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare k text := public._web_admin_ok(p_key); v_phone text;
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  delete from public.web_users where id = p_id and banned = false returning phone into v_phone;
  if not found then return jsonb_build_object('ok', false, 'code', 'NOT_FOUND_OR_BANNED'); end if;
  delete from public.web_snapshot_contacts where phone = v_phone;
  return jsonb_build_object('ok', true);
end $function$;


CREATE OR REPLACE FUNCTION public.web_admin_grant_redownload(p_key text, p_id bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare k text := public._web_admin_ok(p_key);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  if not exists (select 1 from public.web_users where id = p_id) then
    return jsonb_build_object('ok', false, 'code', 'NOT_FOUND');
  end if;
  begin
    insert into public.web_redownload_grants (user_id, month_key) values (p_id, to_char(public._web_today(), 'YYYY-MM'));
  exception when unique_violation then
    return jsonb_build_object('ok', false, 'code', 'ALREADY_GRANTED_THIS_MONTH');
  end;
  return jsonb_build_object('ok', true);
end $function$;


CREATE OR REPLACE FUNCTION public.web_admin_pool_days(p_key text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare k text := public._web_admin_ok(p_key); t date := public._web_today();
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  return jsonb_build_object('ok', true, 'today', t, 'days', (
    select jsonb_agg(jsonb_build_object('day', g.d,
             'contacts', (select count(*) from public.web_verifications v join public.web_users u on u.id = v.user_id
                           where v.day = g.d and not u.banned),
             'verified', (select count(*) from public.web_verifications v where v.day = g.d)) order by g.d desc)
      from (select t - i as d from generate_series(0, 6) i) g));
end
$function$;


CREATE OR REPLACE FUNCTION public.web_admin_run_snapshot(p_key text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
 SET statement_timeout TO '120s'
AS $function$
declare k text := public._web_admin_ok(p_key);
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  return public.web_nightly_snapshot();
end
$function$;


CREATE OR REPLACE FUNCTION public.web_admin_unlog_verified(p_key text, p_id bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare k text := public._web_admin_ok(p_key); t date := public._web_today();
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  if exists (select 1 from public.web_downloads d where d.user_id = p_id and d.status = 'done' and t = any (d.days)) then
    return jsonb_build_object('ok', false, 'code', 'ALREADY_TAKEN');
  end if;
  delete from public.web_verifications where user_id = p_id and day = t;
  return jsonb_build_object('ok', true);
end $function$;


CREATE OR REPLACE FUNCTION public.web_public_config()
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select jsonb_build_object(
    'ok', true,
    'admin_wa',       (select nullif(value, '') from public.web_settings where key = 'admin_wa_phone'),
    'signup_open',    coalesce((select value = 'true' from public.web_settings where key = 'signup_open'), true),
    'signup_message', (select value from public.web_settings where key = 'signup_message'),
    'contacts', coalesce((
        select jsonb_agg(x)
          from public.web_settings s, jsonb_array_elements(s.value::jsonb) x
         where s.key = 'contacts' and (x->>'on')::boolean),
      '[]'::jsonb),
    'today', public._web_today())
$function$;
