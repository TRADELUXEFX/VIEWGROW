-- 20261121090000_tidy_table_grants.sql
-- Extra lock: remove leftover table permissions from the public key.
-- Row security already blocks these tables, so nothing in the app changes.
revoke all on table public.deleted_numbers, public.web_day_releases, public.web_signup_log,
                    public.web_snapshot_contacts, public.web_snapshot_meta
  from anon, authenticated;
