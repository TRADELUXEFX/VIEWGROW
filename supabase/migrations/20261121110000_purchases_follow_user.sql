-- ViewGrow: purchase records are removed when the member is deleted, by any route (owner's choice, 21 Nov 2026).
-- Run once in the Supabase SQL Editor. One transaction. Safe to run twice.
--
-- Why: "Delete my account" already removed the member's purchases, but the admin's Delete user did not,
-- so purchase rows could be left behind. A link with ON DELETE CASCADE makes every delete path do it.
--
-- What it does:
--   1. stops (changes nothing) if web_users.id and web_purchases.user_id are not the same type,
--   2. deletes purchase rows whose member no longer exists (leftovers from earlier admin deletes),
--   3. links web_purchases.user_id to web_users.id with ON DELETE CASCADE (skipped if already there).
-- Deleted rows cannot be brought back; the rollback only removes the link.
--
-- Optional read-only look first (how many leftovers will go):
--   select count(*) from public.web_purchases p where not exists (select 1 from public.web_users u where u.id = p.user_id);

begin;

do $$
declare
  t_users text;
  t_purch text;
  n bigint;
begin
  select format_type(a.atttypid, a.atttypmod) into t_users
    from pg_attribute a where a.attrelid = 'public.web_users'::regclass and a.attname = 'id' and not a.attisdropped;
  select format_type(a.atttypid, a.atttypmod) into t_purch
    from pg_attribute a where a.attrelid = 'public.web_purchases'::regclass and a.attname = 'user_id' and not a.attisdropped;

  if t_users is null or t_purch is null or t_users <> t_purch then
    raise exception 'Stopped, nothing changed: web_users.id is % but web_purchases.user_id is %', t_users, t_purch;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.web_purchases'::regclass and contype = 'f' and conname = 'web_purchases_user_id_fkey'
  ) then
    delete from public.web_purchases p
     where not exists (select 1 from public.web_users u where u.id = p.user_id);
    get diagnostics n = row_count;
    raise notice 'Removed % leftover purchase rows', n;

    alter table public.web_purchases
      add constraint web_purchases_user_id_fkey
      foreign key (user_id) references public.web_users (id) on delete cascade;
  end if;
end $$;

commit;

-- Check after (expect true | 0):
--   select exists (select 1 from pg_constraint where conname = 'web_purchases_user_id_fkey' and confdeltype = 'c'),
--          (select count(*) from public.web_purchases p where not exists (select 1 from public.web_users u where u.id = p.user_id));
