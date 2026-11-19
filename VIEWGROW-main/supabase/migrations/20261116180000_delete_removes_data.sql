-- ViewGrow: "Delete my account" now removes the member's data, not just blocks the number. Safe to run twice.
-- Run once in the Supabase SQL Editor. Replaces web_delete_account.
--
-- A logged-in member (right number and password) taps Delete my account in the app. This function:
--   1. puts the number on the deleted list (it can no longer log in or sign up),
--   2. deletes the member's purchase records and their saved contact entry,
--   3. deletes the member row. Downloads, received, re-download grants and verifications go with it automatically.
-- No approval step.

create or replace function public.web_delete_account(p_phone text, p_pin text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  st jsonb;
  u public.web_users;
begin
  st := (public.web_status_core(p_phone, p_pin))::jsonb;
  if st is null or coalesce(st->>'ok', 'false') <> 'true' then
    return jsonb_build_object('ok', false, 'code', coalesce(st->>'code', 'ERROR'));
  end if;

  select * into u from public.web_users where phone = coalesce(public._web_norm(p_phone), p_phone);
  if not found then
    return jsonb_build_object('ok', false, 'code', 'NOT_FOUND');
  end if;

  if not exists (select 1 from public.deleted_numbers where public.vg_phone_key(phone) = public.vg_phone_key(u.phone)) then
    insert into public.deleted_numbers (phone) values (u.phone);
  end if;

  delete from public.web_purchases        where user_id = u.id;
  delete from public.web_snapshot_contacts where phone = u.phone;
  delete from public.web_users            where id = u.id;

  return jsonb_build_object('ok', true);
end;
$$;

revoke all on function public.web_delete_account(text, text) from public;
grant execute on function public.web_delete_account(text, text) to anon, authenticated;
