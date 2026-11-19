-- ViewGrow: contacts in the downloaded file now carry the company tag VG, the same as the app saves them.
-- Run once in the Supabase SQL Editor. Safe to run twice. Changes ONLY web_prepare_download (adds one line, ORG:VG,
-- to each contact card, including the marker card). The person's name (FN) stays the person's own name, so the app
-- and the website both show "Name" with company "VG". Nothing is deleted. Grants and owner stay as they are.
--
-- Based on the LIVE function text read on 16 Nov 2026 (check_live_matches_claims.sql QUERY 8).
-- The check below stops with an error, changing nothing, if the live function is not the one this file expects.

do $$
declare def text := pg_get_functiondef('public.web_prepare_download(text,text)'::regprocedure);
begin
  if position('NOTE:ViewGrow-' in def) = 0 or position('_web_pick' in def) = 0 then
    raise exception 'web_prepare_download is not the expected version. Nothing was changed. Run QUERY 8 in check_live_matches_claims.sql and send it back.';
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

  insert into public.web_downloads (user_id, kind, dl_day, status, days, contact_count, vcf, picked)
  values (a.uid, v_kind, v_today, 'pending', '{}', v_cnt, v_vcf, v_picked)
  returning id into v_id;

  return jsonb_build_object('ok', true, 'status', 'ready', 'download_id', v_id, 'file_name', v_fname,
                            'vcf', v_vcf, 'count', v_cnt, 'kind', v_kind);
end
$function$;
