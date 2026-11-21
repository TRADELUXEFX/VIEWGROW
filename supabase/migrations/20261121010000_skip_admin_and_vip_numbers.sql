-- ViewGrow: the admin's own number and the VIP numbers are never handed out as member contacts.
-- APPLIED by the owner on 21 Nov 2026 in the SQL Editor. Kept here as the record of what is live.
--
-- Why: members already get the admin and the VIP contacts at the top of every contact file (exact name, no company).
-- If the admin or a VIP also had a member account, the same person was handed out a second time as "<name> VG"
-- and used up one place of the member's limit.
--
-- What changes:
-- 1. New internal _web_is_admin_number(p): true when p is (by vg_phone_key, last 10 digits) the admin WhatsApp
--    number (web_settings admin_wa_phone), a whatsapp_dm entry in web_settings contacts, or a web_vip_contacts phone.
--    Blocked for public, anon and authenticated.
-- 2. _web_candidates: one line added after the banned check: "and not public._web_is_admin_number(s.phone)".
--    Patched from the live function text; stops without changing anything if the live text is not the expected one.
--    Does nothing if it is already in place.
-- 3. web_received rows for those numbers are deleted, so the places they used are given back to members.
-- One transaction. Safe to run twice.
--
-- Read-only check (true = applied):
--   select to_regprocedure('public._web_is_admin_number(text)') is not null as fn_ok,
--          position('_web_is_admin_number' in pg_get_functiondef('public._web_candidates(bigint)'::regprocedure)) > 0 as candidates_ok,
--          not exists (select 1 from public.web_received r where public._web_is_admin_number(r.phone)) as received_clean;

begin;

create or replace function public._web_is_admin_number(p text)
 returns boolean
 language sql
 stable
 security definer
 set search_path to 'public', 'pg_temp'
as $function$
  select coalesce(public.vg_phone_key(p), '') <> '' and exists (
    select 1
      from (
        select s.value as v from public.web_settings s where s.key = 'admin_wa_phone'
        union all
        select x->>'value'
          from public.web_settings s,
               jsonb_array_elements(case when s.value like '[%' then s.value::jsonb else '[]'::jsonb end) x
         where s.key = 'contacts' and x->>'channel' = 'whatsapp_dm'
        union all
        select v.phone from public.web_vip_contacts v
      ) a
     where coalesce(a.v, '') <> '' and public.vg_phone_key(a.v) = public.vg_phone_key(p)
  )
$function$;

revoke all on function public._web_is_admin_number(text) from public, anon, authenticated;

do $$
declare
  def text := pg_get_functiondef('public._web_candidates(bigint)'::regprocedure);
  old_line constant text := 'and not exists (select 1 from public.web_users b where b.phone = s.phone and b.banned)';
begin
  if position('_web_is_admin_number' in def) > 0 then
    return; -- already in place
  end if;
  if position(old_line in def) = 0 then
    raise exception 'Stopped, nothing changed: _web_candidates is not the expected version.';
  end if;
  def := replace(def, old_line, old_line || E'\n       and not public._web_is_admin_number(s.phone)');
  execute def;
end $$;

-- Give back the places the admin number and the VIP numbers already used.
with d as (
  delete from public.web_received r
   where public._web_is_admin_number(r.phone)
  returning 1
)
select count(*) as admin_and_vip_rows_removed from d;

commit;
