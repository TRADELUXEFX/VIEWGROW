-- ViewGrow: deleted-numbers list. Safe to run twice. Run it once in the Supabase SQL Editor, then run 20261116180000_delete_removes_data.sql.
--
-- When a member deletes their account (20261116180000), the number goes on the deleted list. From then on that number
-- cannot log in (ACCOUNT_DELETED) and cannot sign up (ACCOUNT_DELETED). No approval step.
-- To allow a number back, the admin removes it from the list:
--   delete from public.deleted_numbers where public.vg_phone_key(phone) = public.vg_phone_key('THE_NUMBER');
--
-- Check first (2 rows): select proname, pg_get_function_arguments(oid) from pg_proc
--   where pronamespace = 'public'::regnamespace and proname in ('web_status','web_register');
--   web_status: p_phone text, p_pin text    web_register: p_name text, p_phone text, p_consent boolean, p_pin text

create table if not exists public.deleted_numbers (
  phone      text        not null,
  deleted_at timestamptz not null default now()
);
alter table public.deleted_numbers enable row level security;

create or replace function public.vg_phone_key(p text)
returns text language sql immutable as $$
  select right(regexp_replace(coalesce(p, ''), '\D', '', 'g'), 10)
$$;

do $$
begin
  if not exists (select 1 from pg_proc where pronamespace = 'public'::regnamespace and proname = 'web_status_core') then
    alter function public.web_status(text, text) rename to web_status_core;
  end if;
  if not exists (select 1 from pg_proc where pronamespace = 'public'::regnamespace and proname = 'web_register_core') then
    alter function public.web_register(text, text, boolean, text) rename to web_register_core;
  end if;
end $$;
revoke all on function public.web_status_core(text, text) from public, anon, authenticated;
revoke all on function public.web_register_core(text, text, boolean, text) from public, anon, authenticated;

create or replace function public.web_status(p_phone text, p_pin text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if exists (select 1 from public.deleted_numbers d where public.vg_phone_key(d.phone) = public.vg_phone_key(p_phone)) then
    return jsonb_build_object('ok', false, 'code', 'ACCOUNT_DELETED');
  end if;
  return (public.web_status_core(p_phone, p_pin))::jsonb;
end;
$$;
revoke all on function public.web_status(text, text) from public;
grant execute on function public.web_status(text, text) to anon, authenticated;

create or replace function public.web_register(p_name text, p_phone text, p_consent boolean, p_pin text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if exists (select 1 from public.deleted_numbers d where public.vg_phone_key(d.phone) = public.vg_phone_key(p_phone)) then
    return jsonb_build_object('ok', false, 'code', 'ACCOUNT_DELETED');
  end if;
  return (public.web_register_core(p_name, p_phone, p_consent, p_pin))::jsonb;
end;
$$;
revoke all on function public.web_register(text, text, boolean, text) from public;
grant execute on function public.web_register(text, text, boolean, text) to anon, authenticated;

-- web_delete_account is NOT in this file. It lives in 20261116180000_delete_removes_data.sql (run that one after this).
