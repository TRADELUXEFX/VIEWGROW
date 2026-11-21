-- Change admin password (admin page, Settings, "Admin password").
-- 1. Adds web_admin_change_key(p_key, p_new_key): checks the current key with _web_admin_ok (same 10-tries lockout),
--    then saves the new key scrambled (bcrypt, cost 10, same as member passwords). New key: 10 to 72 characters,
--    not the same as the current one. Returns {ok:true} or {ok:false, code: UNAUTHORIZED | LOCKED | TOO_SHORT | TOO_LONG | SAME}.
-- 2. Removes the old web_admin_set_key (no screen ever used it; its arguments were never written down), so there is
--    only one way to change the key.
-- Nothing else changes. Members are not affected. Safe to run twice.
-- After running it: every place that still has the OLD key stops working until it signs in with the new one
-- (other browsers, the admin app on another phone, the "Notify members" button on another device).
do $$
begin
  if to_regprocedure('public._web_admin_ok(text)') is null then
    raise exception 'Stopped, nothing changed: _web_admin_ok is missing.';
  end if;
  if not exists (select 1 from public.web_settings where key = 'admin_key_hash') then
    raise exception 'Stopped, nothing changed: web_settings has no admin_key_hash row.';
  end if;
  if to_regprocedure('extensions.crypt(text,text)') is null and to_regprocedure('public.crypt(text,text)') is null then
    raise exception 'Stopped, nothing changed: pgcrypto (crypt) is missing.';
  end if;
end $$;

-- Remove every version of the old, unused function, whatever its arguments.
do $$
declare f record;
begin
  for f in select p.oid::regprocedure as sig from pg_proc p join pg_namespace n on n.oid = p.pronamespace
            where n.nspname = 'public' and p.proname = 'web_admin_set_key' loop
    execute 'drop function ' || f.sig;
  end loop;
end $$;

create or replace function public.web_admin_change_key(p_key text, p_new_key text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $$
declare
  k text := public._web_admin_ok(p_key);
  n text := coalesce(p_new_key, '');
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  if length(n) < 10 then return jsonb_build_object('ok', false, 'code', 'TOO_SHORT'); end if;
  if length(n) > 72 then return jsonb_build_object('ok', false, 'code', 'TOO_LONG'); end if;   -- bcrypt reads 72 bytes at most
  if n = p_key then return jsonb_build_object('ok', false, 'code', 'SAME'); end if;
  update public.web_settings set value = crypt(n, gen_salt('bf', 10)) where key = 'admin_key_hash';
  return jsonb_build_object('ok', true);
end
$$;
revoke all on function public.web_admin_change_key(text, text) from public;
grant execute on function public.web_admin_change_key(text, text) to anon, authenticated;

-- Check (read-only), expect: change_key_exists = true, old_set_key_left = 0
-- select to_regprocedure('public.web_admin_change_key(text,text)') is not null as change_key_exists,
--        (select count(*) from pg_proc where proname = 'web_admin_set_key') as old_set_key_left;
