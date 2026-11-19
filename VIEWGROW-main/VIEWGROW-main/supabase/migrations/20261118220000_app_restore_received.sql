-- APPLIED 18 Nov 2026 by the owner (SQL Editor).
-- ViewGrow: lets the Android app put back contacts the member already received but deleted from the phone.
-- Run once in the Supabase SQL Editor. Safe to run twice. Adds ONE new function, web_app_restore. Nothing existing is changed.
--
-- Why: the website limits full re-downloads (one a month, admin approves) so the file is not handed out again and again.
-- The app saves straight into the phone and skips numbers already there, so it can safely restore everything the member
-- was already given. This returns ONLY people already in web_received for this member. It gives nothing new, changes no
-- rows and does not use up the monthly re-download permission. Same login check and ban check as web_prepare_download.

create or replace function public.web_app_restore(p_phone text, p_pin text)
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'public', 'pg_temp'
 set statement_timeout to '120s'
as $function$
declare
  a record; v_vcf text; v_cnt int;
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;

  select count(*) into v_cnt from public.web_received x where x.user_id = a.uid;

  with e as (
    select coalesce(nullif(x.name, ''), 'ViewGrow ' || right(x.phone, 4)) as nm, x.phone
      from public.web_received x where x.user_id = a.uid
  ),
  cards as (
    select 1 as o, row_number() over (order by e.nm, e.phone) as r,
           'BEGIN:VCARD' || E'\r\n' || 'VERSION:3.0' || E'\r\n' ||
           'FN:' || public._web_vcf_esc(e.nm) || E'\r\n' ||
           'N:;' || public._web_vcf_esc(e.nm) || ';;;' || E'\r\n' ||
           'ORG:VG' || E'\r\n' ||
           'TEL;TYPE=CELL:+' || e.phone || E'\r\n' ||
           'NOTE:ViewGrow-' || a.uid || E'\r\n' || 'END:VCARD' || E'\r\n' as card
      from e
    union all
    select 2, 0,
           'BEGIN:VCARD' || E'\r\n' || 'VERSION:3.0' || E'\r\n' ||
           'FN:ViewGrow ' || a.uid || E'\r\n' ||
           'N:;ViewGrow ' || a.uid || ';;;' || E'\r\n' ||
           'ORG:VG' || E'\r\n' ||
           'TEL;TYPE=CELL:+234000' || lpad(a.uid::text, 7, '0') || E'\r\n' ||
           'NOTE:ViewGrow-' || a.uid || E'\r\n' || 'END:VCARD' || E'\r\n'
  )
  select coalesce(string_agg(cards.card, '' order by cards.o, cards.r), '') into v_vcf from cards;

  return jsonb_build_object('ok', true, 'vcf', v_vcf, 'count', v_cnt);
end
$function$;

revoke all on function public.web_app_restore(text, text) from public;
grant execute on function public.web_app_restore(text, text) to anon, authenticated;
