-- ViewGrow: let people sign up with a number from any country.
-- Before: web_register_core refused every number that was not Nigerian (13 digits starting with 234).
-- Now: any number _web_norm accepts is allowed (Nigerian as before; other countries need + and the country code,
-- 11 to 15 digits, stored as digits). The only line that changes is the INVALID_PHONE check. Everything else is
-- exactly the function that is live today (read from the database on 20 Nov 2026).
-- Safe to run twice. Grants are kept (create or replace).

create or replace function public.web_register_core(p_name text, p_phone text, p_consent boolean, p_pin text)
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'public', 'extensions', 'pg_temp'
as $function$
declare nm text; n text; v_id bigint;
  v_ip text := public._web_client_ip();
  v_key text := coalesce(public._web_client_ip(), 'unknown');
  v_lim int; v_cap int; v_count int;
begin
  if coalesce((select value from public.web_settings where key = 'signup_open'), 'true') <> 'true' then
    return jsonb_build_object('ok', false, 'code', 'SIGNUP_CLOSED');
  end if;
  nm := public._web_clean(p_name, 60);
  if length(nm) < 2 or public._web_name_key(nm) is null then return jsonb_build_object('ok', false, 'code', 'NAME_REQUIRED'); end if;
  n := public._web_norm(p_phone);
  if n is null then
    return jsonb_build_object('ok', false, 'code', 'INVALID_PHONE');
  end if;
  if not coalesce(p_consent, false) then return jsonb_build_object('ok', false, 'code', 'CONSENT_REQUIRED'); end if;
  if p_pin is null or p_pin !~ '^[A-Za-z0-9]{6,30}$' then return jsonb_build_object('ok', false, 'code', 'PIN_INVALID'); end if;
  if exists (select 1 from public.web_users where phone = n) then
    return jsonb_build_object('ok', false, 'code', 'CHECK_DETAILS');
  end if;
  if exists (select 1 from public.web_users where public._web_name_key(name) = public._web_name_key(nm)) then
    return jsonb_build_object('ok', false, 'code', 'NAME_TAKEN', 'suggestions', to_jsonb(public._web_name_suggestions(nm)));
  end if;

  select coalesce(nullif(value, '')::int, 3) into v_lim from public.web_settings where key = 'signup_limit';
  v_cap := case when v_ip is null then 300 else coalesce(v_lim, 3) end;
  if v_cap > 0 then
    select count(*) into v_count from public.web_signup_log
     where ip = v_key and created_at > now() - interval '24 hours';
    if v_count >= v_cap then return jsonb_build_object('ok', false, 'code', 'SIGNUP_LIMIT'); end if;
  end if;

  begin
    insert into public.web_users (name, phone, pin_hash)
    values (nm, n, crypt(p_pin, gen_salt('bf', 10))) returning id into v_id;
  exception when unique_violation then
    if exists (select 1 from public.web_users where phone = n) then
      return jsonb_build_object('ok', false, 'code', 'CHECK_DETAILS');
    end if;
    return jsonb_build_object('ok', false, 'code', 'NAME_TAKEN', 'suggestions', to_jsonb(public._web_name_suggestions(nm)));
  end;

  insert into public.web_signup_log (ip) values (v_key);
  return jsonb_build_object('ok', true, 'user_id', v_id, 'name', nm, 'phone', n);
end
$function$;
