-- 20261121040000: take members who left or were banned off other members' phones (app)
-- Requested by the owner on 21 Nov 2026.
--
-- Before: when a member deleted their account, was deleted by the admin, or was banned, their number stayed saved
-- (as "Name VG") on every phone that had received it.
--
-- What changes:
-- 1. web_gone: numbers that are no longer members (deleted) or are banned, with when. Kept in step by triggers on
--    web_users: deleted -> added; banned -> added; unbanned -> removed; the same number joins again -> removed.
--    Filled once now with: every banned member, and every number members received that is no longer a member
--    (this covers both Delete my account and the admin's Delete user from before today).
-- 2. web_gone_for_me(p_phone, p_pin, p_since): for the logged-in member only, the numbers in web_gone THAT THIS
--    MEMBER WAS GIVEN (web_received), changed since p_since. So nobody learns a number they did not already have.
--    Answers {ok, now, numbers[]}; the app keeps "now" and sends it as p_since next time.
-- 3. The app (new build) asks during its background check and removes those numbers from the phone, but only
--    contacts ViewGrow saved (company VG). Contacts the member saved themselves, VIP contacts and "ViewGrow Admin"
--    are never touched. A banned member who is later unbanned is not put back on phones already cleaned.
-- Not covered: the website (members import a file; the website cannot reach their phone), and copies the
-- phone already made elsewhere (Google Contacts backup, WhatsApp).
-- Nothing existing is edited. One transaction. Safe to run twice. Undo: rollback/remove_departed_rollback.sql.

begin;

do $$
begin
  if to_regclass('public.web_received') is null or to_regprocedure('public.vg_phone_key(text)') is null
     or to_regprocedure('public._web_auth(text,text)') is null then
    raise exception 'Stopped, nothing changed: web_received, vg_phone_key or _web_auth is missing.';
  end if;
end $$;

create table if not exists public.web_gone (
  phone   text        primary key,
  reason  text        not null check (reason in ('deleted', 'banned')),
  gone_at timestamptz not null default now()
);
create index if not exists web_gone_at_idx on public.web_gone(gone_at);
alter table public.web_gone enable row level security;
revoke all on public.web_gone from public, anon, authenticated;

create or replace function public._web_gone_track()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if tg_op = 'DELETE' then
    if coalesce(old.phone, '') <> '' then
      insert into public.web_gone (phone, reason) values (old.phone, 'deleted')
      on conflict (phone) do update set reason = 'deleted', gone_at = now();
    end if;
    return old;
  elsif tg_op = 'INSERT' then
    delete from public.web_gone g where public.vg_phone_key(g.phone) = public.vg_phone_key(new.phone);
    return new;
  else
    if coalesce(new.banned, false) and not coalesce(old.banned, false) then
      insert into public.web_gone (phone, reason) values (new.phone, 'banned')
      on conflict (phone) do update set reason = 'banned', gone_at = now();
    elsif not coalesce(new.banned, false) and coalesce(old.banned, false) then
      delete from public.web_gone where phone = new.phone and reason = 'banned';
    end if;
    return new;
  end if;
end
$$;
revoke all on function public._web_gone_track() from public, anon, authenticated;
drop trigger if exists web_users_gone_del on public.web_users;
create trigger web_users_gone_del after delete on public.web_users for each row execute function public._web_gone_track();
drop trigger if exists web_users_gone_ins on public.web_users;
create trigger web_users_gone_ins after insert on public.web_users for each row execute function public._web_gone_track();
drop trigger if exists web_users_gone_ban on public.web_users;
create trigger web_users_gone_ban after update of banned on public.web_users for each row execute function public._web_gone_track();

-- Fill once: banned members, and received numbers that are no longer members.
insert into public.web_gone (phone, reason)
select u.phone, 'banned' from public.web_users u where u.banned and coalesce(u.phone, '') <> ''
on conflict (phone) do nothing;
insert into public.web_gone (phone, reason)
select distinct on (public.vg_phone_key(r.phone)) r.phone, 'deleted'
  from public.web_received r
 where coalesce(r.phone, '') <> ''
   and not exists (select 1 from public.web_users u where public.vg_phone_key(u.phone) = public.vg_phone_key(r.phone))
on conflict (phone) do nothing;

-- For the logged-in member: numbers they were given that have since left or been banned.
create or replace function public.web_gone_for_me(p_phone text, p_pin text, p_since timestamptz default null)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare a record; v_now timestamptz := now();
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  return jsonb_build_object('ok', true, 'now', v_now, 'numbers', coalesce((
    select jsonb_agg(x.phone) from (
      select distinct g.phone
        from public.web_gone g
       where (p_since is null or g.gone_at > p_since - interval '5 minutes')
         and exists (select 1 from public.web_received r
                      where r.user_id = a.uid and public.vg_phone_key(r.phone) = public.vg_phone_key(g.phone))
       limit 5000) x), '[]'::jsonb));
end
$$;
revoke all on function public.web_gone_for_me(text, text, timestamptz) from public;
grant execute on function public.web_gone_for_me(text, text, timestamptz) to anon, authenticated;

commit;
