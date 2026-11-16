-- ViewGrow: web_last_release, for the app's 8am "41 people joined last night" message. Safe to run twice.
-- Run once in the Supabase SQL Editor. Adds one read-only function. Changes no table and no other function.
--
-- A logged-in member (right number and password) asks: what was the last release, how many people were in it,
-- and was I in it? The app uses the answer only when the release was last night and the member was not in it.
--
-- Answer: {ok, batch, last_night, people, you_in}
--   batch      the last released batch (the release night's day), from web_day_releases
--   last_night true when that batch is yesterday's day (the release at 10pm Lagos last night)
--   people     how many people were in that batch (sum of web_snapshot_meta.contact_count for its days)
--   you_in     true when this member has a counted verification (web_verifications) on one of its days
-- Wrong number or password: the same {ok:false, code} as web_status_core.
--
-- BEFORE RUNNING (STATE.md rule 2): run the read-only check in changelog.md for 20261116220000 and make sure
-- the four columns it lists exist. If any is missing, do not run this file; send the result instead.

create or replace function public.web_last_release(p_phone text, p_pin text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  st jsonb;
  u public.web_users;
  b date;
  n bigint;
  mine boolean;
begin
  st := (public.web_status_core(p_phone, p_pin))::jsonb;
  if st is null or coalesce(st->>'ok', 'false') <> 'true' then
    return jsonb_build_object('ok', false, 'code', coalesce(st->>'code', 'ERROR'));
  end if;

  select * into u from public.web_users where phone = coalesce(public._web_norm(p_phone), p_phone);
  if not found then
    return jsonb_build_object('ok', false, 'code', 'NOT_FOUND');
  end if;

  select max(coalesce(r.batch, r.day)) into b from public.web_day_releases r;
  if b is null then
    return jsonb_build_object('ok', true, 'batch', null, 'last_night', false, 'people', 0, 'you_in', false);
  end if;

  select coalesce(sum(m.contact_count), 0) into n
    from public.web_snapshot_meta m
    join public.web_day_releases r on r.day = m.day
   where coalesce(r.batch, r.day) = b;

  select exists (
    select 1 from public.web_verifications v
      join public.web_day_releases r on r.day = v.day
     where v.user_id = u.id and coalesce(r.batch, r.day) = b
  ) into mine;

  return jsonb_build_object(
    'ok', true,
    'batch', b,
    'last_night', b = public._web_today() - 1,
    'people', n,
    'you_in', mine);
end;
$$;

revoke all on function public.web_last_release(text, text) from public;
grant execute on function public.web_last_release(text, text) to anon, authenticated;
