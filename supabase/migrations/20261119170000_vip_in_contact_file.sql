-- ViewGrow: the admin's VIP contacts are in EVERY contacts file a member downloads from the website.
-- Run once in the Supabase SQL Editor, AFTER 20261119160000_vip_contacts.sql and 20261119150000_remove_marker_contact.sql.
-- Safe to run twice.
--
-- WHAT IT DOES
--  * Every VIP (name exactly as typed, no VG tag, no ViewGrow note) is put in every file: daily files and full
--    re-downloads, for every member. Nothing is tracked. A VIP added later is simply in the next file.
--  * The VIPs come first in the file, then the member's people (names ending in " VG", as now).
--  * VIPs are not "people received": they never count toward the limit and are not in web_received.
--  * If a member imports two files, the phone may show a VIP more than once. That is accepted.
--
-- WHAT IT CHANGES
--  Only web_prepare_download (the VIP lines are the only new ones). Based on the LATEST version
--  (20261119150000_remove_marker_contact.sql: names end in " VG", no marker contact). web_confirm_download is not
--  touched. Grants and owner stay as they are.
--  It also removes the tracking objects of an earlier draft (web_vip_given, vip_picked, trigger) if they exist.
--  They are new and hold nothing that matters.

do $$
declare def text := pg_get_functiondef('public.web_prepare_download(text,text)'::regprocedure);
begin
  if to_regclass('public.web_vip_contacts') is null then
    raise exception 'web_vip_contacts is missing. Run 20261119160000_vip_contacts.sql first. Nothing was changed.';
  end if;
  if not ((position('_web_pick' in def) > 0 and position('ORG:VG' in def) > 0 and position('NOTE:ViewGrow-' in def) > 0
           and position('+234000' in def) = 0) or position('web_vip_contacts' in def) > 0) then
    raise exception 'web_prepare_download is not the expected version (it must be the one from 20261119150000_remove_marker_contact.sql). Nothing was changed.';
  end if;
end $$;

create or replace function public.web_prepare_download(p_phone text, p_pin text)
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'public', 'pg_temp'
 set statement_timeout to '120s'
as $function$
declare
  a record; s record; d public.web_downloads; v_today date := public._web_today();
  v_kind text; v_vcf text; v_cnt int; v_id bigint; v_fname text; v_picked jsonb;
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  select * into s from public._web_state(a.uid);
  if s.state <> 'READY' then
    return jsonb_build_object('ok', true, 'status', 'nothing', 'state', s.state);
  end if;
  v_kind  := case when s.is_full then 'full' else 'daily' end;
  v_fname := 'ViewGrow-' || a.uid || '.vcf';

  -- an unconfirmed file from today: hand out exactly the same file again
  select * into d from public.web_downloads
   where user_id = a.uid and status = 'pending' and kind = v_kind and dl_day = v_today
   order by id desc limit 1;
  if found then
    return jsonb_build_object('ok', true, 'status', 'ready', 'download_id', d.id, 'file_name', v_fname,
                              'vcf', d.vcf, 'count', d.contact_count, 'kind', d.kind);
  end if;

  -- older unconfirmed downloads are closed, not deleted
  update public.web_downloads set status = 'abandoned', vcf = ''
   where user_id = a.uid and status = 'pending';

  -- choose who goes in this file (nothing is recorded as received until confirmed)
  if v_kind = 'full' then
    select coalesce(jsonb_agg(jsonb_build_object('phone', x.phone, 'name', x.name, 'day', x.day)
                              order by x.day, x.phone), '[]'::jsonb)
      into v_picked from public.web_received x where x.user_id = a.uid;
  else
    select coalesce(jsonb_agg(jsonb_build_object('phone', p.phone, 'name', p.name, 'day', p.day)
                              order by p.day, p.id), '[]'::jsonb)
      into v_picked
      from public._web_pick(a.uid, public._web_limit(a.uid) - public._web_received_count(a.uid)) p;
  end if;
  v_cnt := jsonb_array_length(v_picked);

  with e as (
    select coalesce(nullif(x.name, ''), 'ViewGrow ' || right(x.phone, 4)) as nm, x.phone
      from jsonb_to_recordset(v_picked) as x(phone text, name text, day date)
  ),
  cards as (
    select 1 as o, row_number() over (order by e.nm, e.phone) as r,
           'BEGIN:VCARD' || E'\r\n' || 'VERSION:3.0' || E'\r\n' ||
           'FN:' || public._web_vcf_esc(case when e.nm ~ ' VG$' then e.nm else e.nm || ' VG' end) || E'\r\n' ||
           'N:;' || public._web_vcf_esc(case when e.nm ~ ' VG$' then e.nm else e.nm || ' VG' end) || ';;;' || E'\r\n' ||
           'ORG:VG' || E'\r\n' ||
           'TEL;TYPE=CELL:+' || e.phone || E'\r\n' ||
           'NOTE:ViewGrow-' || a.uid || E'\r\n' || 'END:VCARD' || E'\r\n' as card
      from e
    union all
    select 0 as o, row_number() over (order by v.name, v.phone) as r,
           'BEGIN:VCARD' || E'\r\n' || 'VERSION:3.0' || E'\r\n' ||
           'FN:' || public._web_vcf_esc(v.name) || E'\r\n' ||
           'N:;' || public._web_vcf_esc(v.name) || ';;;' || E'\r\n' ||
           'TEL;TYPE=CELL:+' || v.phone || E'\r\n' || 'END:VCARD' || E'\r\n' as card
      from public.web_vip_contacts v
  )
  select coalesce(string_agg(cards.card, '' order by cards.o, cards.r), '') into v_vcf from cards;

  insert into public.web_downloads (user_id, kind, dl_day, status, days, contact_count, vcf, picked)
  values (a.uid, v_kind, v_today, 'pending', '{}', v_cnt, v_vcf, v_picked)
  returning id into v_id;

  return jsonb_build_object('ok', true, 'status', 'ready', 'download_id', v_id, 'file_name', v_fname,
                            'vcf', v_vcf, 'count', v_cnt, 'kind', v_kind);
end
$function$;

-- remove the tracking objects of the earlier draft, if they were ever created
drop trigger if exists web_downloads_vip_given on public.web_downloads;
drop function if exists public._web_vip_given_trg();
alter table public.web_downloads drop column if exists vip_picked;
drop table if exists public.web_vip_given;
