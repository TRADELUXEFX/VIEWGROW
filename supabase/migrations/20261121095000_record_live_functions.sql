-- 20261121095000_record_live_functions.sql
-- RECORD ONLY: these 4 functions were improved in the dashboard without a file. This file holds the live code
-- exactly as exported on 21 Nov 2026 (check_part2_followup.sql), so the files match the database again.
-- It is already live: there is no need to run it. Running it changes nothing.
--   _web_auth_pin     password check: tells a banned member (right password only) BANNED, and anyone LOCKED.
--   web_admin_user    member pop-up for the admin (the old unused tracer_phone field is gone).
--   web_day_contacts  a day's people for the member: skips people who are no longer members, marks banned ones.
--   web_people        everyone the member was given: same rules as web_day_contacts.

CREATE OR REPLACE FUNCTION public._web_auth_pin(p_phone text, p_pin text, OUT uid bigint, OUT err jsonb)
 RETURNS record
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'extensions', 'pg_temp'
AS $function$
declare n text; u public.web_users; v_fails int; v_locked boolean; v_pin_ok boolean;
begin
  uid := null; err := null;
  n := public._web_norm(p_phone);
  if n is null then err := jsonb_build_object('ok', false, 'code', 'INVALID_PHONE'); return; end if;

  select * into u from public.web_users where phone = n for update;
  if not found then
    err := jsonb_build_object('ok', false, 'code', 'WRONG_PIN', 'attempts_left', 0);
    return;
  end if;

  v_pin_ok := case
    when p_pin is not null and p_pin ~ '^[A-Za-z0-9]{6,30}$' then (u.pin_hash = crypt(p_pin, u.pin_hash))
    else false
  end;

  -- Banned: say so only to someone who knows the password. Anyone else gets the usual answer.
  if u.banned then
    if v_pin_ok then
      err := jsonb_build_object('ok', false, 'code', 'BANNED', 'reason', coalesce(u.ban_reason, ''));
    else
      err := jsonb_build_object('ok', false, 'code', 'WRONG_PIN', 'attempts_left', 0);
    end if;
    return;
  end if;

  -- Locked (5 wrong tries): say so to anyone, so a member who forgot the password knows what to do.
  if u.locked then
    err := jsonb_build_object('ok', false, 'code', 'LOCKED', 'attempts_left', 0);
    return;
  end if;

  if not v_pin_ok then
    update public.web_users set pin_fails = pin_fails + 1, locked = (pin_fails + 1 >= 5)
     where id = u.id returning pin_fails, locked into v_fails, v_locked;
    err := jsonb_build_object('ok', false, 'code', 'WRONG_PIN',
                              'attempts_left', case when v_locked then 0 else greatest(5 - v_fails, 0) end);
    return;
  end if;

  update public.web_users
     set pin_fails = 0,
         pin_hash  = case when split_part(u.pin_hash, '$', 3) = '10'
                          then u.pin_hash
                          else crypt(p_pin, gen_salt('bf', 10)) end
   where id = u.id;
  uid := u.id;
end
$function$;

CREATE OR REPLACE FUNCTION public.web_admin_user(p_key text, p_id bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare k text := public._web_admin_ok(p_key); u public.web_users; t date := public._web_today();
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  select * into u from public.web_users where id = p_id;
  if not found then return jsonb_build_object('ok', false, 'code', 'NOT_FOUND'); end if;
  return jsonb_build_object('ok', true,
    'user', jsonb_build_object('id', u.id, 'name', u.name, 'phone', u.phone, 'registered_on', u.registered_on,
              'locked', u.locked, 'banned', u.banned, 'ban_reason', u.ban_reason, 'banned_at', u.banned_at,
              'pin_fails', u.pin_fails, 'created_at', u.created_at, 'pin_reset_at', u.pin_reset_at),
    'verified_days', (select coalesce(jsonb_agg(v.day order by v.day desc), '[]'::jsonb)
                        from (select day from public.web_verifications where user_id = u.id order by day desc limit 60) v),
    'downloads', (select coalesce(jsonb_agg(jsonb_build_object('id', d.id, 'kind', d.kind, 'day', d.dl_day,
                        'status', d.status, 'count', d.contact_count, 'days', d.days, 'confirmed_at', d.confirmed_at)
                        order by d.id desc), '[]'::jsonb)
                    from (select * from public.web_downloads where user_id = u.id and status <> 'abandoned'
                           order by id desc limit 30) d),
    'grant_open',  exists (select 1 from public.web_redownload_grants g where g.user_id = u.id and g.used_at is null),
    'grant_month', exists (select 1 from public.web_redownload_grants g where g.user_id = u.id and g.month_key = to_char(t, 'YYYY-MM')),
    'received', public._web_received_count(u.id),
    'limit', public._web_limit(u.id),
    'personal', u.limit_override,
    'capped', public._web_received_count(u.id) >= public._web_limit(u.id));
end
$function$;

CREATE OR REPLACE FUNCTION public.web_day_contacts(p_phone text, p_pin text, p_day date)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare a record; v_items jsonb;
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  if p_day is null then
    return jsonb_build_object('ok', false, 'code', 'BAD_DAY');
  end if;
  select coalesce(jsonb_agg(jsonb_build_object('name', x.name, 'phone', x.phone,
                                               'banned', coalesce((select m.banned from public.web_users m where m.phone = x.phone limit 1), false))
                            order by x.received_at, x.phone), '[]'::jsonb)
    into v_items
    from public.web_received x
   where x.user_id = a.uid and x.day = p_day
     and exists (select 1 from public.web_users m where m.phone = x.phone);
  return jsonb_build_object('ok', true, 'day', p_day, 'items', v_items);
end
$function$;

CREATE OR REPLACE FUNCTION public.web_people(p_phone text, p_pin text, p_offset integer DEFAULT 0, p_limit integer DEFAULT 50)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare a record; v_items jsonb; v_total int;
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  select count(*)::int into v_total from public.web_received r
   where r.user_id = a.uid
     and exists (select 1 from public.web_users m where m.phone = r.phone);
  select coalesce(jsonb_agg(jsonb_build_object('name', t.name, 'phone', t.phone, 'day', t.day, 'banned', t.banned)
                            order by t.received_at desc, t.phone), '[]'::jsonb)
    into v_items
    from (
      select r.name, r.phone, r.day, r.received_at,
             coalesce((select m.banned from public.web_users m where m.phone = r.phone limit 1), false) as banned
        from public.web_received r
       where r.user_id = a.uid
         and exists (select 1 from public.web_users m where m.phone = r.phone)
       order by r.received_at desc, r.phone
       limit greatest(least(coalesce(p_limit, 50), 100), 1)
      offset greatest(coalesce(p_offset, 0), 0)
    ) t;
  return jsonb_build_object('ok', true, 'total', v_total, 'items', v_items);
end
$function$;
