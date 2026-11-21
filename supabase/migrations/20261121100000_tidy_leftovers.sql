-- 20261121100000_tidy_leftovers.sql (from check_everything_part2 on 21 Nov 2026)
-- 1. deleted_numbers gets an id column as its primary key (the only table without one). Nothing in the app changes.
-- 2. Removes 5 functions nothing uses (not the website, apps, Edge Functions, other functions, triggers or the nightly job):
--      _web_contacts, _web_days       old helpers, replaced by _web_candidates
--      web_admin_contact_set          old version of web_admin_contacts_set (the one the admin page uses)
--      web_admin_user_limit           replaced by web_admin_user, which returns the same numbers
--      web_setup                      first-time setup; it refuses to run once the admin password is set
--    Their code is kept in rollback/tidy_leftovers_rollback.sql to put them back if ever needed.
-- One transaction: if anything still used one of them, it stops with an error and changes nothing.
begin;

alter table public.deleted_numbers add column if not exists id bigserial;
do $$
begin
  if not exists (select 1 from pg_constraint where conrelid = 'public.deleted_numbers'::regclass and contype = 'p') then
    alter table public.deleted_numbers add primary key (id);
  end if;
end $$;

drop function if exists public._web_contacts(bigint, date[]);
drop function if exists public._web_days(bigint, boolean);
drop function if exists public.web_admin_contact_set(text, text, text, text);
drop function if exists public.web_admin_user_limit(text, bigint);
drop function if exists public.web_setup(text, text);

commit;

-- Check (should say: true | 0)
select exists (select 1 from pg_constraint where conrelid = 'public.deleted_numbers'::regclass and contype = 'p') as deleted_numbers_has_id,
       (select count(*) from pg_proc where pronamespace = 'public'::regnamespace
         and proname in ('_web_contacts', '_web_days', 'web_admin_contact_set', 'web_admin_user_limit', 'web_setup')) as leftovers_left;
