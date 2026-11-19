-- ViewGrow app: "Delete my account" (Profile). Run once in the Supabase SQL Editor.
--
-- The app calls web_delete_account(p_phone, p_pin). The member is checked with the same
-- web_status call the app already uses (right number and password, not banned or locked).
-- The request is written to account_deletion_requests. The admin then deletes the member
-- (Table editor) and sets status to 'done'. The app has already removed the saved contacts
-- from the phone and logged the member out.

create table if not exists public.account_deletion_requests (
  id           bigint generated always as identity primary key,
  phone        text        not null,
  status       text        not null default 'pending',   -- pending | done
  requested_at timestamptz not null default now(),
  handled_at   timestamptz
);

-- Nobody can read or write it from the app. Only the function below and the dashboard can.
alter table public.account_deletion_requests enable row level security;

create or replace function public.web_delete_account(p_phone text, p_pin text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  st jsonb;
begin
  st := (public.web_status(p_phone, p_pin))::jsonb;
  if st is null or coalesce(st->>'ok', 'false') <> 'true' then
    return jsonb_build_object('ok', false, 'code', coalesce(st->>'code', 'ERROR'));
  end if;

  -- One open request per number, even if the button is pressed twice.
  if not exists (
    select 1 from public.account_deletion_requests
    where phone = p_phone and status = 'pending'
  ) then
    insert into public.account_deletion_requests (phone) values (p_phone);
  end if;

  return jsonb_build_object('ok', true);
end;
$$;

revoke all on function public.web_delete_account(text, text) from public;
grant execute on function public.web_delete_account(text, text) to anon, authenticated;
