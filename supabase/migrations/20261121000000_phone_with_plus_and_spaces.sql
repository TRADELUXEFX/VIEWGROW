-- ViewGrow: phone numbers are stored the way they are shown, with + and spaces: +234 803 123 4567.
-- Requested by the owner on 20 Nov 2026. Written against the live function code exported on 20 Nov 2026
-- (check_phone_format_1_export.sql). Run check_phone_format_2_before.sql first, then this file, then
-- check_phone_format_3_after.sql. To undo: rollback/phone_format_rollback.sql.
--
-- What changes:
-- 1. New _web_fmt(p): digits -> + country code + the national number in that country's groups (Google's phone
--    number data, the same as the app and the website). Nigeria: +234 803 123 4567. US: +1 201 555 0123.
-- 2. _web_norm returns that form (same rules as before for which numbers are accepted). Every sign-up, login and
--    lookup goes through _web_norm, so 0803 123 4567, +2348031234567 and +234 8031234567 are all the same
--    "+234 803 123 4567" and can never make two accounts.
-- 3. Every stored number is rewritten once to that form: web_users, deleted_numbers, web_purchases, web_received,
--    web_snapshot_contacts, web_vip_contacts, the admin WhatsApp number in web_settings, and the people picked for
--    contact files not confirmed yet (web_downloads.picked, pending only).
-- 4. Functions that would break with a + and spaces: the three contact files no longer add their own + (it would be
--    ++234), the "ViewGrow 1234" fallback name takes the last 4 digits, admin search by number compares digits, and
--    VIP save stores through _web_norm like every other number.
-- Nothing else changes. Matching by vg_phone_key (last 10 digits) keeps working: it ignores + and spaces.
-- One transaction: if anything fails, nothing is changed. Safe to run twice.

begin;

-- Stop if a rule on a table would refuse the new form (none was in the export; this makes sure).
do $$
declare bad text;
begin
  select string_agg(conrelid::regclass || '.' || conname, ', ') into bad
    from pg_constraint
   where contype = 'c' and connamespace = 'public'::regnamespace
     and conrelid::regclass::text in ('web_users', 'deleted_numbers', 'web_purchases', 'web_received', 'web_snapshot_contacts', 'web_vip_contacts')
     and pg_get_constraintdef(oid) ilike '%phone%';
  if bad is not null then
    raise exception 'Stopped, nothing changed: these table rules check the phone format and must be looked at first: %', bad;
  end if;
end $$;

CREATE OR REPLACE FUNCTION public._web_fmt(p text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$
-- A full number in digits (with its country code) -> the one way it is stored and shown: + country code, then the
-- national number in the country's own groups (from Google's phone number data, the same as the app and website).
-- 2348031234567 -> +234 803 123 4567, 12015550123 -> +1 201 555 0123, 447400123456 -> +44 7400 123456.
-- Calling codes never start with another code, so the first code that fits is the only one.
declare
  m constant jsonb := '{"1":[3,3,4],"7":[3,3,2,2],"20":[2,8],"27":[2,3,4],"30":[3,3,4],"31":[1,8],"32":[3,2,2,2],"33":[1,2,2,2,2],"34":[3,2,2,2],"36":[2,3,4],"39":[3,3,4],"40":[3,3,3],"41":[2,3,2,2],"43":[3,6],"44":[4,6],"45":[2,2,2,2],"46":[2,3,2,2],"47":[3,2,3],"48":[3,3,3],"49":[4,7],"51":[3,3,3],"52":[3,3,4],"53":[1,7],"54":[3,3,4],"55":[2,5,4],"56":[1,4,4],"57":[3,7],"58":[3,7],"60":[2,3,4],"61":[3,3,3],"62":[3,3,3],"63":[3,3,4],"64":[2,3,4],"65":[4,4],"66":[2,3,4],"81":[2,4,4],"82":[2,4,4],"84":[3,3,3],"86":[3,4,4],"90":[3,3,2,2],"91":[5,5],"92":[3,7],"93":[2,3,4],"94":[2,3,4],"95":[1,3,4],"98":[3,3,4],"211":[3,3,3],"212":[1,2,2,2,2],"213":[3,2,2,2],"216":[2,3,3],"218":[2,7],"220":[3,4],"221":[2,3,2,2],"222":[2,2,2,2],"223":[2,2,2,2],"224":[3,2,2,2],"225":[2,2,2,4],"226":[2,2,2,2],"227":[2,2,2,2],"228":[2,2,2,2],"229":[2,2,2,2,2],"230":[4,4],"231":[2,3,4],"232":[2,6],"233":[2,3,4],"234":[3,3,4],"235":[2,2,2,2],"236":[2,2,2,2],"237":[1,2,2,2,2],"238":[3,2,2],"239":[3,4],"240":[3,3,3],"241":[2,2,2,2],"242":[2,3,4],"243":[3,3,3],"244":[3,3,3],"245":[3,3,3],"246":[3,4],"247":[5],"248":[1,3,3],"249":[2,3,4],"250":[3,3,3],"251":[2,3,4],"252":[1,7],"253":[2,2,2,2],"254":[3,6],"255":[3,3,3],"256":[3,6],"257":[2,2,2,2],"258":[2,3,4],"260":[2,7],"261":[2,2,3,2],"262":[3,2,2,2],"263":[2,3,4],"264":[2,3,4],"265":[3,2,2,2],"266":[4,4],"267":[2,3,3],"268":[4,4],"269":[3,2,2],"290":[5],"291":[1,3,3],"297":[3,4],"298":[2,2,2],"299":[2,2,2],"350":[8],"351":[3,3,3],"352":[3,3,3],"353":[2,3,4],"354":[3,4],"355":[2,3,4],"356":[4,4],"357":[2,6],"358":[2,7],"359":[2,3,3],"370":[3,5],"371":[2,3,3],"372":[4,4],"373":[3,2,3],"374":[2,6],"375":[2,3,2,2],"376":[3,3],"377":[1,2,2,2,2],"378":[2,2,2,2],"380":[2,3,4],"381":[2,7],"382":[2,3,3],"383":[2,3,3],"385":[2,3,4],"386":[2,3,3],"387":[2,3,3],"389":[2,3,3],"420":[3,3,3],"421":[3,3,3],"423":[3,3,3],"500":[5],"501":[3,4],"502":[4,4],"503":[4,4],"504":[4,4],"505":[4,4],"506":[4,4],"507":[4,4],"508":[2,2,2],"509":[2,2,4],"590":[3,2,2,2],"591":[8],"592":[3,4],"593":[2,3,4],"594":[3,2,2,2],"595":[3,6],"596":[3,2,2,2],"597":[3,4],"598":[2,3,3],"599":[1,3,4],"670":[4,4],"672":[1,5],"673":[3,4],"674":[3,4],"675":[4,4],"676":[3,4],"677":[2,5],"678":[3,4],"679":[3,4],"680":[3,4],"681":[2,2,2],"682":[2,3],"683":[3,4],"685":[2,5],"686":[8],"687":[2,2,2],"688":[2,4],"689":[2,2,2,2],"690":[4],"691":[3,4],"692":[3,4],"850":[3,3,4],"852":[4,4],"853":[4,4],"855":[2,3,3],"856":[2,2,3,3],"880":[4,6],"886":[3,3,3],"960":[3,4],"961":[2,3,3],"962":[1,4,4],"963":[3,3,3],"964":[3,3,4],"965":[3,5],"966":[2,3,4],"967":[3,3,3],"968":[4,4],"970":[3,3,3],"971":[2,3,4],"972":[2,3,4],"973":[4,4],"974":[4,4],"975":[2,2,2,2],"976":[4,4],"977":[3,7],"992":[2,3,4],"993":[2,6],"994":[2,3,2,2],"995":[3,2,2,2],"996":[3,3,3],"998":[2,3,2,2]}'::jsonb;
  d text := regexp_replace(coalesce(p, ''), '\D', '', 'g');
  cc text; g jsonb; rest text; o text; i int := 1; k int; n int; len int;
begin
  if d = '' then return null; end if;
  for k in reverse 3..1 loop
    if m ? left(d, k) then cc := left(d, k); exit; end if;
  end loop;
  if cc is null then return '+' || d; end if;
  rest := substr(d, length(cc) + 1);
  g := m -> cc; n := jsonb_array_length(g); o := '+' || cc;
  for k in 0 .. n - 1 loop
    exit when i > length(rest);
    len := case when k = n - 1 then length(rest) - i + 1 else least((g ->> k)::int, length(rest) - i + 1) end;
    o := o || ' ' || substr(rest, i, len);
    i := i + len;
  end loop;
  return o;
end $function$;

CREATE OR REPLACE FUNCTION public._web_norm(p text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$
declare d text; plus boolean;
begin
  if p is null then return null; end if;
  plus := btrim(p) like '+%';
  d := regexp_replace(p, '\D', '', 'g');
  if d like '00%' and length(d) >= 12 then d := substr(d, 3); end if;
  if d like '234%' and length(d) = 13 and substr(d, 4, 1) in ('7','8','9') then return public._web_fmt(d); end if;
  if d like '0%' and length(d) = 11 and substr(d, 2, 1) in ('7','8','9') then return public._web_fmt('234' || substr(d, 2)); end if;
  if length(d) = 10 and substr(d, 1, 1) in ('7','8','9') then return public._web_fmt('234' || d); end if;
  if plus and length(d) between 11 and 15 then return public._web_fmt(d); end if;
  return null;
end $function$;

CREATE OR REPLACE FUNCTION public.web_admin_group(p_key text, p_group text, p_search text DEFAULT ''::text, p_limit integer DEFAULT 40, p_offset integer DEFAULT 0)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  k text := public._web_admin_ok(p_key);
  q text := lower(trim(coalesce(p_search, '')));
  d text := regexp_replace(coalesce(p_search, ''), '\D', '', 'g');
  res jsonb;
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  if p_group not in ('all', 'verified', 'verified_today', 'pending', 'locked', 'banned', 'never', 'limit_full', 'joined_today', 'got_file_today') then
    return jsonb_build_object('ok', false, 'code', 'BAD_GROUP');
  end if;

  with g as (
    select u.*, m.received, m.lim
      from public._web_group_members(p_group) m
      join public.web_users u on u.id = m.id
     where q = ''
        or lower(coalesce(u.name, '')) like '%' || q || '%'
        or (d <> '' and regexp_replace(u.phone, '\D', '', 'g') like '%' || d || '%')
        or u.id::text = q
  ),
  page as (
    select * from g order by g.id desc limit greatest(1, least(coalesce(p_limit, 40), 200)) offset greatest(0, coalesce(p_offset, 0))
  )
  select jsonb_build_object(
    'ok', true,
    'total', (select count(*) from g),
    'app', (select count(*) from g where g.app_seen_at is not null),
    'web', (select count(*) from g where g.app_seen_at is null),
    'rows', coalesce((select jsonb_agg(jsonb_build_object(
              'id', p.id, 'name', p.name, 'phone', p.phone, 'banned', coalesce(p.banned, false), 'locked', coalesce(p.locked, false),
              'verified_today', exists (select 1 from public.web_verifications v where v.user_id = p.id and v.day = public._web_today()),
              'verified_ever', exists (select 1 from public.web_verifications v where v.user_id = p.id),
              'app', p.app_seen_at is not null, 'registered_on', p.registered_on, 'received', p.received, 'lim', p.lim)
              order by p.id desc) from page p), '[]'::jsonb))
    into res;
  return res;
end;
$function$;

CREATE OR REPLACE FUNCTION public.web_admin_users(p_key text, p_search text DEFAULT ''::text, p_filter text DEFAULT 'all'::text, p_limit integer DEFAULT 50, p_offset integer DEFAULT 0)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  k text := public._web_admin_ok(p_key); t date := public._web_today();
  s text := lower(btrim(coalesce(p_search, '')));
  dg text := regexp_replace(coalesce(p_search, ''), '\D', '', 'g');
  alt text; v_total int; v_rows jsonb;
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  alt := case when dg like '0%' then substr(dg, 2) else dg end;
  with f as (
    select u.*,
      exists (select 1 from public.web_verifications v where v.user_id = u.id and v.day = t) as vt,
      exists (select 1 from public.web_downloads d where d.user_id = u.id and d.kind = 'daily' and d.status = 'done' and d.dl_day = t) as dt,
      exists (select 1 from public.web_redownload_grants g where g.user_id = u.id and g.used_at is null) as hg
    from public.web_users u
   where (s = '' or u.id::text = s or lower(u.name) like '%' || s || '%'
          or (alt <> '' and (regexp_replace(u.phone, '\D', '', 'g') like '%' || alt || '%' or ('234000' || lpad(u.id::text, 7, '0')) = dg)))),
  g as (
    select * from f w
     where case p_filter when 'pending'  then not w.banned and not w.vt
                         when 'verified' then w.vt
                         when 'locked'   then w.locked and not w.banned
                         when 'banned'   then w.banned
                         else true end)
  select (select count(*) from g),
         (select coalesce(jsonb_agg(jsonb_build_object('id', q.id, 'name', q.name, 'phone', q.phone,
                   'registered_on', q.registered_on, 'locked', q.locked, 'banned', q.banned, 'pin_fails', q.pin_fails,
                   'verified_today', q.vt, 'downloaded_today', q.dt, 'has_grant', q.hg) order by q.id desc), '[]'::jsonb)
            from (select * from g order by id desc
                   limit greatest(least(coalesce(p_limit, 50), 200), 1) offset greatest(coalesce(p_offset, 0), 0)) q)
    into v_total, v_rows;
  return jsonb_build_object('ok', true, 'total', v_total, 'rows', v_rows);
end $function$;

CREATE OR REPLACE FUNCTION public.web_admin_new_members(p_key text, p_from_id bigint DEFAULT NULL::bigint, p_upto_id bigint DEFAULT NULL::bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  k text := public._web_admin_ok(p_key);
  v_mark bigint;
  v_from bigint;
  v_upto bigint;
  v_cnt int;
  v_vcf text;
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;

  v_mark := coalesce(nullif((select value from public.web_settings where key = 'members_file_upto'), '')::bigint, 0);
  v_from := coalesce(p_from_id, v_mark);
  v_upto := coalesce(p_upto_id, (select max(id) from public.web_users), 0);

  with m as (
    select u.id, u.phone,
           case
             when coalesce(trim(u.name), '') = '' then 'Member ' || u.id || ' VG'
             when trim(u.name) ~* '\sVG$' then trim(u.name)
             else trim(u.name) || ' VG'
           end as nm
      from public.web_users u
     where u.id > v_from and u.id <= v_upto and not coalesce(u.banned, false)
  )
  select count(*),
         coalesce(string_agg(
           'BEGIN:VCARD' || E'\r\n' || 'VERSION:3.0' || E'\r\n' ||
           'FN:' || public._web_vcf_esc(m.nm) || E'\r\n' ||
           'N:;' || public._web_vcf_esc(m.nm) || ';;;' || E'\r\n' ||
           'ORG:VG' || E'\r\n' ||
           'TEL;TYPE=CELL:' || m.phone || E'\r\n' ||
           'END:VCARD' || E'\r\n', '' order by m.id), '')
    into v_cnt, v_vcf
    from m;

  return jsonb_build_object('ok', true, 'count', v_cnt, 'from_id', v_from, 'upto_id', v_upto,
                            'first_time', v_mark = 0 and p_from_id is null,
                            'file_name', 'ViewGrow-new-members-' || to_char(public._web_today(), 'YYYY-MM-DD') || '.vcf',
                            'vcf', v_vcf);
end
$function$;

CREATE OR REPLACE FUNCTION public.web_app_restore(p_phone text, p_pin text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
 SET statement_timeout TO '120s'
AS $function$
declare
  a record; v_vcf text; v_cnt int;
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;

  select count(*) into v_cnt from public.web_received x where x.user_id = a.uid;

  with e as (
    select coalesce(nullif(x.name, ''), 'ViewGrow ' || right(regexp_replace(x.phone, '\D', '', 'g'), 4)) as nm, x.phone
      from public.web_received x where x.user_id = a.uid
  ),
  cards as (
    select 1 as o, row_number() over (order by e.nm, e.phone) as r,
           'BEGIN:VCARD' || E'\r\n' || 'VERSION:3.0' || E'\r\n' ||
           'FN:' || public._web_vcf_esc(case when e.nm ~ ' VG$' then e.nm else e.nm || ' VG' end) || E'\r\n' ||
           'N:;' || public._web_vcf_esc(case when e.nm ~ ' VG$' then e.nm else e.nm || ' VG' end) || ';;;' || E'\r\n' ||
           'ORG:VG' || E'\r\n' ||
           'TEL;TYPE=CELL:' || e.phone || E'\r\n' || 'END:VCARD' || E'\r\n' as card
      from e
  )
  select coalesce(string_agg(cards.card, '' order by cards.o, cards.r), '') into v_vcf from cards;

  return jsonb_build_object('ok', true, 'vcf', v_vcf, 'count', v_cnt);
end
$function$;

CREATE OR REPLACE FUNCTION public.web_prepare_download(p_phone text, p_pin text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
 SET statement_timeout TO '120s'
AS $function$
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
    select coalesce(nullif(x.name, ''), 'ViewGrow ' || right(regexp_replace(x.phone, '\D', '', 'g'), 4)) as nm, x.phone
      from jsonb_to_recordset(v_picked) as x(phone text, name text, day date)
  ),
  cards as (
    select 1 as o, row_number() over (order by e.nm, e.phone) as r,
           'BEGIN:VCARD' || E'\r\n' || 'VERSION:3.0' || E'\r\n' ||
           'FN:' || public._web_vcf_esc(case when e.nm ~ ' VG$' then e.nm else e.nm || ' VG' end) || E'\r\n' ||
           'N:;' || public._web_vcf_esc(case when e.nm ~ ' VG$' then e.nm else e.nm || ' VG' end) || ';;;' || E'\r\n' ||
           'ORG:VG' || E'\r\n' ||
           'TEL;TYPE=CELL:' || e.phone || E'\r\n' || 'END:VCARD' || E'\r\n' as card
      from e
    union all
    select 0 as o, row_number() over (order by v.name, v.phone) as r,
           'BEGIN:VCARD' || E'\r\n' || 'VERSION:3.0' || E'\r\n' ||
           'FN:' || public._web_vcf_esc(v.name) || E'\r\n' ||
           'N:;' || public._web_vcf_esc(v.name) || ';;;' || E'\r\n' ||
           'TEL;TYPE=CELL:' || v.phone || E'\r\n' || 'END:VCARD' || E'\r\n' as card
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

CREATE OR REPLACE FUNCTION public.web_admin_vip_save(p_key text, p_name text, p_phone text, p_id bigint DEFAULT NULL::bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  k text := public._web_admin_ok(p_key);
  n text := btrim(regexp_replace(coalesce(p_name, ''), '[[:cntrl:]]+', ' ', 'g'));
  d text := regexp_replace(coalesce(p_phone, ''), '\D', '', 'g');
  v_id bigint;
begin
  if k <> 'OK' then return jsonb_build_object('ok', false, 'code', k); end if;
  if char_length(n) < 1 or char_length(n) > 60 then
    return jsonb_build_object('ok', false, 'code', 'BAD_NAME');
  end if;
  if d ~ '^0\d{10}$' then d := '234' || substr(d, 2);
  elsif d ~ '^[789]\d{9}$' then d := '234' || d;
  end if;
  if d !~ '^\d{10,15}$' then
    return jsonb_build_object('ok', false, 'code', 'BAD_PHONE');
  end if;
  -- Stored the same way as every other number: +234 803 123 4567.
  d := public._web_norm('+' || d);
  if d is null then
    return jsonb_build_object('ok', false, 'code', 'BAD_PHONE');
  end if;
  if p_id is null then
    insert into public.web_vip_contacts (name, phone) values (n, d) returning id into v_id;
  else
    update public.web_vip_contacts set name = n, phone = d where id = p_id returning id into v_id;
    if v_id is null then return jsonb_build_object('ok', false, 'code', 'NOT_FOUND'); end if;
  end if;
  return jsonb_build_object('ok', true, 'id', v_id);
exception when unique_violation then
  return jsonb_build_object('ok', false, 'code', 'DUPLICATE');
end
$function$;

revoke all on function public._web_fmt(text) from public, anon, authenticated;

-- Every stored number, once.
update public.web_users set phone = public._web_fmt(phone) where phone !~ '^\+';
update public.deleted_numbers set phone = public._web_fmt(phone) where phone !~ '^\+';
update public.web_purchases set phone = public._web_fmt(phone) where phone !~ '^\+';
update public.web_received set phone = public._web_fmt(phone) where phone !~ '^\+';
update public.web_snapshot_contacts set phone = public._web_fmt(phone) where phone !~ '^\+';
update public.web_vip_contacts set phone = public._web_fmt(phone) where phone !~ '^\+';
update public.web_settings set value = public._web_fmt(value)
 where key = 'admin_wa_phone' and coalesce(value, '') <> '';
update public.web_settings set value = public._web_fmt(value)
 where key = 'contact_value' and coalesce(value, '') <> ''
   and (select s.value from public.web_settings s where s.key = 'contact_channel') = 'whatsapp_dm';
update public.web_settings set value = coalesce((
    select jsonb_agg(case when x->>'channel' = 'whatsapp_dm' and coalesce(x->>'value', '') <> ''
                          then jsonb_set(x, '{value}', to_jsonb(public._web_fmt(x->>'value'))) else x end order by ord)
      from jsonb_array_elements(value::jsonb) with ordinality t(x, ord)), '[]'::jsonb)::text
 where key = 'contacts' and value like '[%';
-- Files handed out but not confirmed yet: the people picked for them are recorded on confirm, so their numbers change too.
update public.web_downloads set picked = (
    select jsonb_agg(case when x ? 'phone' then jsonb_set(x, '{phone}', to_jsonb(public._web_fmt(x->>'phone'))) else x end order by ord)
      from jsonb_array_elements(picked) with ordinality t(x, ord))
 where status = 'pending' and jsonb_typeof(picked) = 'array' and jsonb_array_length(picked) > 0;

commit;
