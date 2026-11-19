-- 20261117140000: login tokens (members stop sending their password on every call)
--
-- What changes for members: the password is sent once, at log in. The database answers with a long random
-- login token. The website and the app keep the token and send it in place of the password on every call
-- (same p_pin argument, so no other function changes). A token:
--   - starts with "vgs_" (a password can never contain "_", so the two can't be mixed up);
--   - is stored here only as a SHA-256 hash, so this table can't be used to log in;
--   - lasts 90 days from its last use, and is renewed at most once an hour when used;
--   - is removed at log out and when the password changes (admin reset);
--   - while the member is banned or locked, answers BANNED / LOCKED (so the app shows its Banned / Blocked screen);
--   - goes when the member is deleted (ON DELETE CASCADE).
-- Old apps and pages that still send the password keep working: a password goes to the old check, unchanged.
--
-- How: the live _web_auth is renamed _web_auth_pin (not edited), and a new _web_auth sits in front of it.
-- Tokens are checked by the new one; anything else goes to _web_auth_pin exactly as before.
-- STEP 2 of 3. Run supabase/check_login_tokens_1_before.sql first and supabase/check_login_tokens_3_after.sql after.
-- Undo: supabase/rollback/login_tokens_rollback.sql.

create table if not exists public.web_sessions (
  id           bigserial   primary key,
  user_id      bigint      not null references public.web_users(id) on delete cascade,
  token_hash   text        not null unique,
  device       text        not null default '',
  created_at   timestamptz not null default now(),
  last_used_at timestamptz not null default now(),
  expires_at   timestamptz not null default now() + interval '90 days'
);
create index if not exists web_sessions_user_idx on public.web_sessions(user_id);
alter table public.web_sessions enable row level security;   -- no policies: only the functions below touch it
revoke all on public.web_sessions from public, anon, authenticated;

-- 1. Keep the live password check as it is, under a new name (only once).
do $$
begin
  if not exists (select 1 from pg_proc where pronamespace = 'public'::regnamespace and proname = '_web_auth_pin') then
    alter function public._web_auth(text, text) rename to _web_auth_pin;
  end if;
end $$;
revoke all on function public._web_auth_pin(text, text) from public, anon, authenticated;

-- 2. The new front door: a token is checked here; a password goes to the old check.
create or replace function public._web_auth(p_phone text, p_pin text, out uid bigint, out err jsonb)
returns record
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $$
declare n text; u public.web_users; s public.web_sessions;
begin
  uid := null; err := null;
  if p_pin is null or left(p_pin, 4) <> 'vgs_' then
    select a.uid, a.err into uid, err from public._web_auth_pin(p_phone, p_pin) a;
    return;
  end if;

  n := public._web_norm(p_phone);
  if n is null then err := jsonb_build_object('ok', false, 'code', 'INVALID_PHONE'); return; end if;
  select * into u from public.web_users where phone = n;
  if not found then err := jsonb_build_object('ok', false, 'code', 'SESSION_EXPIRED'); return; end if;

  select * into s from public.web_sessions
   where token_hash = encode(digest(p_pin, 'sha256'), 'hex') and user_id = u.id and expires_at > now();
  if not found then err := jsonb_build_object('ok', false, 'code', 'SESSION_EXPIRED'); return; end if;

  -- Banned or locked: the token is kept, so the app keeps getting this answer (it shows the Banned or Blocked
  -- screen and removes the saved contacts). An unban makes the same token work again; a new password ends it.
  if u.banned then
    err := jsonb_build_object('ok', false, 'code', 'BANNED', 'reason', coalesce(to_jsonb(u)->>'ban_reason', ''));
    return;
  end if;
  if u.locked then
    err := jsonb_build_object('ok', false, 'code', 'LOCKED');
    return;
  end if;

  if s.last_used_at < now() - interval '1 hour' then
    update public.web_sessions set last_used_at = now(), expires_at = now() + interval '90 days' where id = s.id;
  end if;
  uid := u.id;
end
$$;
revoke all on function public._web_auth(text, text) from public, anon, authenticated;

-- 3. Log in: checks the number and password exactly like web_status (deleted list, bans, wrong tries),
--    and on success also returns a new token. Keeps at most 10 tokens per member (oldest removed).
create or replace function public.web_login(p_phone text, p_pin text, p_device text default '')
returns jsonb
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $$
declare st jsonb; v_uid bigint; v_tok text;
begin
  if p_pin is null or p_pin !~ '^[A-Za-z0-9]{6,30}$' then
    return jsonb_build_object('ok', false, 'code', 'WRONG_PIN', 'attempts_left', 0);
  end if;
  st := public.web_status(p_phone, p_pin);
  if not coalesce((st->>'ok')::boolean, false) then return st; end if;
  v_uid := (st->>'user_id')::bigint;
  if v_uid is null then
    select id into v_uid from public.web_users where phone = public._web_norm(p_phone);
  end if;
  v_tok := 'vgs_' || encode(gen_random_bytes(24), 'hex');
  insert into public.web_sessions (user_id, token_hash, device)
  values (v_uid, encode(digest(v_tok, 'sha256'), 'hex'), left(coalesce(p_device, ''), 40));
  delete from public.web_sessions
   where user_id = v_uid
     and id not in (select id from public.web_sessions where user_id = v_uid order by last_used_at desc limit 10);
  return st || jsonb_build_object('token', v_tok);
end
$$;
revoke all on function public.web_login(text, text, text) from public;
grant execute on function public.web_login(text, text, text) to anon, authenticated;

-- 4. Log out: removes this token only (other phones stay logged in).
create or replace function public.web_logout(p_phone text, p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions, pg_temp
as $$
begin
  if p_token is not null and left(p_token, 4) = 'vgs_' then
    delete from public.web_sessions s
     using public.web_users u
     where s.user_id = u.id and u.phone = public._web_norm(p_phone)
       and s.token_hash = encode(digest(p_token, 'sha256'), 'hex');
  end if;
  return jsonb_build_object('ok', true);
end
$$;
revoke all on function public.web_logout(text, text) from public;
grant execute on function public.web_logout(text, text) to anon, authenticated;

-- 5. New password (admin reset, or a stronger hash at log in): every token for that member stops working at once.
create or replace function public._web_sessions_revoke()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if new.pin_hash is distinct from old.pin_hash then
    delete from public.web_sessions where user_id = new.id;
  end if;
  return new;
end
$$;
revoke all on function public._web_sessions_revoke() from public, anon, authenticated;
drop trigger if exists web_users_sessions_revoke on public.web_users;
create trigger web_users_sessions_revoke
  after update of pin_hash on public.web_users
  for each row execute function public._web_sessions_revoke();
