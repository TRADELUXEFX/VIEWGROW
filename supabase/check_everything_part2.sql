-- ViewGrow: check everything, PART 2 (21 Nov 2026). Leaves your data exactly as it was.
-- 1) Is every function the same as in the code files?  2) Leftovers nobody uses.  3) Database guards.
-- 4) Rules checked against your real data.  5) Rule tests: it makes pretend members (fake 0816 numbers),
--    signs them up, logs in, reports, bans, changes numbers and deletes, then THROWS ALL OF IT AWAY.
--    A real member never sees any of it. Shows member ID numbers only, never phone numbers.
-- Helpers below exist only while this query runs (pg_temp).
create or replace function pg_temp.vg_run(q text, kind text) returns text language plpgsql as $f$
declare r text;
begin
  if kind in ('bad', 'warn') then
    execute format('select count(*)::text || ''|'' || coalesce(string_agg(x, '', '') filter (where rn <= 10), '''') from (select x, row_number() over () as rn from (%s) t) s', q) into r;
  else
    execute q into r;
  end if;
  return coalesce(r, '');
exception when others then
  return 'ERROR|' || sqlerrm;
end $f$;

-- Rule tests. Everything they do is undone at the end (the last line throws it all away), even if a test fails.
create or replace function pg_temp.vg_r(item text, pass boolean, got text) returns text language sql as $f$
  select item || '|' || case when pass then 'OK' else 'PROBLEM' end || '|' || left(coalesce(got, ''), 160)
$f$;
create or replace function pg_temp.vg_c(r jsonb) returns text language sql as $f$
  select case when r is null then 'no answer' when r ? 'code' then 'answer ' || (r->>'code') else 'answer ok=' || coalesce(r->>'ok', '?') end
$f$;
create or replace function pg_temp.vg_tests() returns text[] language plpgsql as $f$
declare
  res text[] := '{}'; nums text[] := '{}'; nms text[] := '{}'; n text; i int;
  pin text := 'Chk' || substr(md5(random()::text), 1, 9);
  akey text := 'vgtest' || md5(random()::text);
  r jsonb; ua bigint; ub bigint; uc bigint; ud bigint; ue bigint; tok text; cnt int; cid bigint; a record;
begin
  begin
    update public.web_settings set value = '100000' where key = 'signup_limit';
    update public.web_settings set value = 'true'   where key = 'signup_open';
    update public.web_settings set value = '3'      where key = 'report_ban_count';
    update public.web_settings set value = extensions.crypt(akey, extensions.gen_salt('bf')) where key = 'admin_key_hash';
    delete from public.web_admin_attempts;
    while coalesce(array_length(nums, 1), 0) < 8 loop
      n := '0816' || lpad(floor(random() * 10000000)::int::text, 7, '0');
      if not exists (select 1 from public.web_users where public.vg_phone_key(phone) = public.vg_phone_key(n))
         and not exists (select 1 from public.deleted_numbers where public.vg_phone_key(phone) = public.vg_phone_key(n))
         and not public._web_is_admin_number(n) and not (n = any(nums)) then
        nums := nums || n;
        nms := nms || ('Zq' || (select string_agg(chr(97 + floor(random() * 26)::int), '') from generate_series(1, 7)) || ' Test');
      end if;
    end loop;

    -- 1. Sign up
    begin
      r := public.web_register(nms[1], nums[1], true, pin);
      select id into ua from public.web_users where public.vg_phone_key(phone) = public.vg_phone_key(nums[1]);
      res := res || pg_temp.vg_r('Sign up works', ua is not null, pg_temp.vg_c(r));
      res := res || pg_temp.vg_r('Number is saved in the +234 … form', (select phone from public.web_users where id = ua) = public._web_norm(nums[1]), 'checked');
      res := res || pg_temp.vg_r('New member starts with the default limit', public._web_limit(ua) = (select value::int from public.web_settings where key = 'default_limit'), 'limit ' || public._web_limit(ua));
      r := public.web_register(nms[2], '+234' || substr(nums[1], 2), true, pin);
      res := res || pg_temp.vg_r('Same number cannot sign up twice (written another way)', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r));
      r := public.web_register(nms[1], nums[8], true, pin);
      res := res || pg_temp.vg_r('Same name cannot be used twice', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r));
      r := public.web_register(nms[2], nums[8], true, 'abc');
      res := res || pg_temp.vg_r('Too-short password is refused', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r));
      update public.web_settings set value = 'false' where key = 'signup_open';
      r := public.web_register(nms[2], nums[8], true, pin);
      res := res || pg_temp.vg_r('Sign-up closed means no new accounts', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r));
      update public.web_settings set value = 'true' where key = 'signup_open';
    exception when others then res := res || ('Sign up tests|PROBLEM|stopped: ' || sqlerrm);
    end;

    -- 2. Log in and login tokens
    begin
      r := public.web_login(nums[1], pin, 'web'); tok := r->>'token';
      res := res || pg_temp.vg_r('Log in gives a login token', tok like 'vgs_%', pg_temp.vg_c(r - 'token'));
      select * into a from public._web_auth(nums[1], tok);
      res := res || pg_temp.vg_r('Login token is accepted', a.uid = ua, coalesce(pg_temp.vg_c(a.err), 'accepted'));
      select * into a from public._web_auth(nums[1], 'vgs_00000000');
      res := res || pg_temp.vg_r('Made-up login token is refused', a.uid is null, coalesce(pg_temp.vg_c(a.err), 'accepted'));
      r := public.web_logout(nums[1], tok);
      select * into a from public._web_auth(nums[1], tok);
      res := res || pg_temp.vg_r('Log out ends that login token', a.uid is null, pg_temp.vg_c(r));
      for i in 1..11 loop r := public.web_login(nums[1], pin, 'web'); end loop;
      select count(*) into cnt from public.web_sessions where user_id = ua;
      res := res || pg_temp.vg_r('At most 10 login tokens per member', cnt <= 10, cnt || ' tokens after 11 log ins');
    exception when others then res := res || ('Log in tests|PROBLEM|stopped: ' || sqlerrm);
    end;

    -- 3. Wrong passwords lock, admin reset unlocks
    begin
      r := public.web_register(nms[2], nums[2], true, pin);
      select id into ub from public.web_users where public.vg_phone_key(phone) = public.vg_phone_key(nums[2]);
      for i in 1..5 loop r := public.web_status(nums[2], 'Wrong' || i || 'pass'); end loop;
      res := res || pg_temp.vg_r('5 wrong passwords lock the account', (select locked from public.web_users where id = ub), pg_temp.vg_c(r));
      r := public.web_status(nums[2], pin);
      res := res || pg_temp.vg_r('Locked account cannot log in with the right password', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r));
      r := public.web_admin_reset_pin(p_key => akey, p_id => ub);
      res := res || pg_temp.vg_r('Admin Reset password unlocks the account', not (select locked from public.web_users where id = ub), pg_temp.vg_c(r));
      r := public.web_admin_phone_changes(p_key => 'not-the-key');
      res := res || pg_temp.vg_r('Wrong admin password is refused', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r));
    exception when others then res := res || ('Password tests|PROBLEM|stopped: ' || sqlerrm);
    end;

    -- 4. Reports and auto-ban (auto-ban number set to 3 for the test)
    begin
      r := public.web_register(nms[3], nums[3], true, pin);
      r := public.web_register(nms[4], nums[4], true, pin);
      r := public.web_register(nms[5], nums[5], true, pin);
      select id into uc from public.web_users where public.vg_phone_key(phone) = public.vg_phone_key(nums[3]);
      select id into ud from public.web_users where public.vg_phone_key(phone) = public.vg_phone_key(nums[4]);
      select id into ue from public.web_users where public.vg_phone_key(phone) = public.vg_phone_key(nums[5]);
      insert into public.web_received (user_id, phone, name, day)
      select x, (select phone from public.web_users where id = ua), nms[1], public._web_today() from unnest(array[uc, ud, ue]) x;
      r := public.web_report(nums[3], pin, nums[5], 'spam');
      res := res || pg_temp.vg_r('Cannot report someone you were not given', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r));
      r := public.web_report(nums[3], pin, nums[1], 'made-up');
      res := res || pg_temp.vg_r('Unknown report reason is refused', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r));
      r := public.web_report(nums[3], pin, nums[1], 'spam');
      res := res || pg_temp.vg_r('Reporting a contact works', coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r));
      r := public.web_report(nums[3], pin, nums[1], 'spam');
      r := public.web_report(nums[4], pin, nums[1], 'scam');
      res := res || pg_temp.vg_r('2 people reporting (one twice) does not ban', not (select banned from public.web_users where id = ua), 'reports from 2 people');
      r := public.web_report(nums[5], pin, nums[1], 'spam');
      res := res || pg_temp.vg_r('3rd person reporting bans automatically', (select banned and ban_reason = 'reported' from public.web_users where id = ua), pg_temp.vg_c(r));
      res := res || pg_temp.vg_r('Banned member goes on the remove-from-phones list', exists (select 1 from public.web_gone g where public.vg_phone_key(g.phone) = public.vg_phone_key(nums[1])), 'checked');
      r := public.web_login(nums[1], pin, 'web');
      res := res || pg_temp.vg_r('Banned member cannot log in', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r - 'token'));
      r := public.web_admin_ban(p_key => akey, p_id => ua, p_banned => false);
      res := res || pg_temp.vg_r('Admin Unban works', not (select banned from public.web_users where id = ua), pg_temp.vg_c(r));
      res := res || pg_temp.vg_r('Unbanned member comes off the remove-from-phones list', not exists (select 1 from public.web_gone g where public.vg_phone_key(g.phone) = public.vg_phone_key(nums[1])), 'checked');
      r := public.web_report(nums[3], pin, nums[1], 'under18');
      res := res || pg_temp.vg_r('Under-18 report bans at once for your review', (select banned and ban_reason = 'under18_review' from public.web_users where id = ua), pg_temp.vg_c(r));
    exception when others then res := res || ('Report tests|PROBLEM|stopped: ' || sqlerrm);
    end;

    -- 5. Change phone number
    begin
      r := public.web_login(nums[3], pin, 'web');
      r := public.web_phone_change_request(nums[3], pin, nums[5]);
      res := res || pg_temp.vg_r('Cannot change to another member''s number', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r));
      r := public.web_phone_change_request(nums[3], pin, nums[6]);
      cid := (r->'request'->>'id')::bigint;
      res := res || pg_temp.vg_r('Asking for a number change works', cid is not null, pg_temp.vg_c(r));
      select count(*) into cnt from public.web_phone_swaps;
      r := public.web_admin_phone_change_decide(p_key => akey, p_id => cid, p_action => 'approve');
      res := res || pg_temp.vg_r('Approving moves the account to the new number', (select public.vg_phone_key(phone) from public.web_users where id = uc) = public.vg_phone_key(nums[6]), pg_temp.vg_c(r));
      res := res || pg_temp.vg_r('Approving records the swap for other phones', (select count(*) from public.web_phone_swaps) = cnt + 1, 'checked');
      res := res || pg_temp.vg_r('Approving logs the member out everywhere', not exists (select 1 from public.web_sessions where user_id = uc), 'checked');
      r := public.web_login(nums[6], pin, 'web');
      res := res || pg_temp.vg_r('Member logs in with the new number', coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r - 'token'));
      r := public.web_login(nums[3], pin, 'web');
      res := res || pg_temp.vg_r('Old number no longer logs in', not coalesce((r->>'ok')::boolean, false), pg_temp.vg_c(r - 'token'));
    exception when others then res := res || ('Phone change tests|PROBLEM|stopped: ' || sqlerrm);
    end;

    -- 6. Contacts for an unverified member
    begin
      r := public.web_prepare_download(nums[5], pin);
      res := res || pg_temp.vg_r('Unverified member gets no contact file', coalesce(r->>'status', '') = 'nothing' or not coalesce((r->>'ok')::boolean, true), coalesce(r->>'state', pg_temp.vg_c(r)));
    exception when others then res := res || ('Download test|PROBLEM|stopped: ' || sqlerrm);
    end;

    -- 7. Delete my account
    begin
      r := public.web_delete_account(nums[4], pin);
      res := res || pg_temp.vg_r('Delete my account removes the account', not exists (select 1 from public.web_users where id = ud), pg_temp.vg_c(r));
      res := res || pg_temp.vg_r('Deleted number goes on the deleted list', exists (select 1 from public.deleted_numbers where public.vg_phone_key(phone) = public.vg_phone_key(nums[4])), 'checked');
      res := res || pg_temp.vg_r('Deleted member goes on the remove-from-phones list', exists (select 1 from public.web_gone g where public.vg_phone_key(g.phone) = public.vg_phone_key(nums[4])), 'checked');
      res := res || pg_temp.vg_r('Their received contacts are removed too', not exists (select 1 from public.web_received where user_id = ud), 'checked');
      r := public.web_register(nms[7], nums[4], true, pin);
      res := res || pg_temp.vg_r('Deleted number cannot sign up again', coalesce(r->>'code', '') = 'ACCOUNT_DELETED', pg_temp.vg_c(r));
      r := public.web_login(nums[4], pin, 'web');
      res := res || pg_temp.vg_r('Deleted number cannot log in', coalesce(r->>'code', '') = 'ACCOUNT_DELETED', pg_temp.vg_c(r - 'token'));
    exception when others then res := res || ('Delete tests|PROBLEM|stopped: ' || sqlerrm);
    end;

    raise exception 'VG_UNDO';
  exception when others then
    if sqlerrm <> 'VG_UNDO' then res := res || ('Tests|PROBLEM|stopped early: ' || sqlerrm); end if;
  end;
  return res;
end $f$;


with
code(fn, file, h) as (values ('_web_admin_ok', '20261022120000', '672abfefbdb153b4712098b2c2b39081'), ('_web_auth', '20261117140000', 'f11a94441c3627fbc28282a64688fa89'), ('_web_auth_pin', '20261121095000', '8adfeb8647e4e1d09875f64f8ca88e67'), ('_web_day_rows', '20261023100000', 'c236210a13d0e016c96acf105f7e11d3'), ('_web_device_banned', '20261121020000', 'a9f9b448276f566c013b263b7a4dc48c'), ('_web_device_note', '20261121020000', 'e6f950702431de56ece7c0b0acfc25ad'), ('_web_devices_ban_new', '20261121020000', '2374812f7ac6bb6b2e25637200c4b329'), ('_web_devices_from_session', '20261121020000', 'c5054f2b9b1bc3db238375308f7ab7f2'), ('_web_fmt', '20261121000000', 'f0c3e9a751e68bfb5ab4e4a1ac9a9ef4'), ('_web_gone_track', '20261121040000', 'a17efa03f2a97fbfdcebf65da3b7886a'), ('_web_group_members', '20261118240000', '9aa855d3865782dd01a1142d7bca0ca5'), ('_web_is_admin_number', '20261121010000', '3b4606c97c5bdbf76d909f1e223ea74d'), ('_web_is_release_day', '20261030100000', '9c26f161e34d3401d05bd4d3fb1d6ffa'), ('_web_limit', '20261022130000', '2a186e4677ff0e7ec9155d059d589e6c'), ('_web_next_release', '20261030100000', 'ef90a4f377c510327034bb4b7ea40f16'), ('_web_norm', '20261121000000', 'b6b1f9c5673050af2251b52f83f14234'), ('_web_phone_change_json', '20261121080000', 'bb68bba6b11946cf7fe2dd3e4732d94d'), ('_web_phone_change_problem', '20261121080000', '84a4b22d71b1dc647b5ac14af296b247'), ('_web_pick', '20261022130000', '2a530705ad77303c5faf6df6053e4c26'), ('_web_received_count', '20261022130000', 'ee8a36c6466c36cd77432cda3ab4dfb6'), ('_web_release_pending', '20261030100000', '977841a9a22b549e74ba71244f392204'), ('_web_report_limit', '20261121050000', '4ecba0b87f9113f09dc39e4358bf042e'), ('_web_sessions_revoke', '20261117140000', '2bf4aa6d683111e455eb9e703d25a70c'), ('_web_state', '20261022130000', '7592d7dd3be7d7ac2a4417f9e2a3d687'), ('_web_today', '20261029210000', '166d9fd350003aa30e6551541315ccf1'), ('_web_users_ban_devices', '20261121020000', '0028120bc72cc0f2415f801dd4063459'), ('vg_phone_key', '20261116140000', 'adf5821d818000ce392542f65c98bf3c'), ('web_admin_backup', '20261022130000', '477d688b1b9ee08d031d7e873e0fe5ed'), ('web_admin_change_key', '20261121070000', '62851c0d3e16d40f585ceb2af0261b78'), ('web_admin_deleted_list', '20261121050000', '449316bf88e6b926eef573f38ffc7102'), ('web_admin_deleted_restore', '20261121050000', 'df6930966bdc332cc5d056c312d5bff8'), ('web_admin_device_flag_allow', '20261121030000', 'a0daeac59a8e9d72b2d10b04de85ebec'), ('web_admin_device_flag_dismiss', '20261121030000', '5af0372c34f619631c31a78225b8a3ab'), ('web_admin_device_flags', '20261121030000', 'cb356e312f7d70e9a369ff7c93af108d'), ('web_admin_devices', '20261121020000', 'f79b85d71813563b5a2b760ff08015be'), ('web_admin_group', '20261121000000', 'cc21c5ab9e5cfb3d482be8fc2d9310cd'), ('web_admin_group_ids', '20261116240000', '3421ad9c38cf6feb8768d6c6676a9fdf'), ('web_admin_limit_set', '20261022130000', '7f76709c577b0d843fb99b073c821d1e'), ('web_admin_log_verified', '20261022130000', '61e2c834991c7b4f91c74d482a34ce22'), ('web_admin_log_verified_day', '20261027233000', '25b5dcf75bee26d36e222315febd2e26'), ('web_admin_new_members', '20261121000000', 'c4727ac75df1880ad3b183b69ac0f9c3'), ('web_admin_new_members_done', '20261116230000', '9c1410e2973679b92a9d7e992b1e11c7'), ('web_admin_phone_change_decide', '20261121080000', '65b4544e6104e0b315ac718fc96bcc01'), ('web_admin_phone_changes', '20261121080000', '18d41912d70f228108efb2adebd98acc'), ('web_admin_pool_list', '20261116260000', 'bc6793b7a4084d5d45b7a11f7ebe2a3f'), ('web_admin_release_get', '20261030100000', '05a8c0788ba2e6e76fb08a62f269777d'), ('web_admin_release_now', '20261119140000', 'bef783590c81cacf4d1be6f03f7103ab'), ('web_admin_release_set', '20261030100000', '9769526333c8f158b9d903e5b06d7944'), ('web_admin_report_close', '20261121050000', '483d8457a4feb61b48c96d340735d4f6'), ('web_admin_report_limit', '20261121050000', '71650206e48edd1236916b21f0ae0b54'), ('web_admin_reports', '20261121050000', '5921892d749a16b28724bd69698d7ced'), ('web_admin_reports_closed', '20261121060000', '4d943f6e888b8c86c2cdcf9d2c49d929'), ('web_admin_reset_pin', '20261022120000', '1c5eedb9957b01b5db29e4e8dc9039f5'), ('web_admin_saved_add', '20261117100000', '1befe6f5c18d5ef13d54d2355377ff96'), ('web_admin_saved_delete', '20261117100000', '5fb1099e19c8a69e8b5750f5a594f6a8'), ('web_admin_saved_list', '20261117100000', 'bf6dc12d89cd9f1da16ff74c8d066959'), ('web_admin_settings_get', '20261022130000', 'ec8204d68a6a30a64df6852af0998daa'), ('web_admin_settings_set', '20261022130000', '1f74b801afbcfe18f537e88237b7a386'), ('web_admin_user', '20261121095000', '95cfe4564ca720b3e341a067c47cd150'), ('web_admin_users', '20261121000000', '5033f0e8b1e3cb8f8a593446b3d4567b'), ('web_admin_vip_delete', '20261119160000', '078d89c2d475869167f221638f07fc84'), ('web_admin_vip_list', '20261119160000', 'a80678f0c8cb9e7b8a1280fddef676a8'), ('web_admin_vip_save', '20261121000000', '6a8391a3e6542845866062befa31223a'), ('web_app_restore', '20261121000000', 'e59e5aeed067627c4f7972f7eb6a7bac'), ('web_app_seen', '20261116240000', '72cf8fe62aff254fc8c59e6e4ebe3975'), ('web_apply_purchase', '20261116150000', 'd4e7235b1e2aba328da03089d257e7de'), ('web_boot', '20261022140000', 'a8a81c5cfd540ed90b6012229db30bed'), ('web_confirm_download', '20261022130000', 'ce4ca28dcb208ec08ea5b3f254a447af'), ('web_day_contacts', '20261121095000', 'ffcb8e7a346f92dfc6357485748ad9a0'), ('web_delete_account', '20261116180000', '8063d68c7bbec37f91f08d384f5902dd'), ('web_device_seen', '20261121020000', '2fc3915e7c7e605ff2da6c84ddc76869'), ('web_gone_for_me', '20261121040000', 'af011f09c00b81d0ba85fe4723fd1a61'), ('web_history', '20261023100000', '49f25e6c1e218ecc15b51e84641e3e81'), ('web_last_release', '20261116220000', '215f74d4f3032ce833ca1a403ebf3421'), ('web_login', '20261117140000', 'bbbaa1ca585f614ead06d3e60e461c61'), ('web_logout', '20261117140000', 'f6503c2b10b626dd3cb583c53aeb5a20'), ('web_next_release', '20261030100000', 'd518d566dace53eb9047e7016d7c3c61'), ('web_nightly_snapshot', '20261119140000', '86d1b17b10d00fe6dbc00dfaed794934'), ('web_people', '20261121095000', '666ad53591685d0b3478376f3929938f'), ('web_phone_change_cancel', '20261121080000', '77aab85fd0c1172a4d756369f63fbddf'), ('web_phone_change_mine', '20261121080000', 'c2267134127cdf046886da4403e10e64'), ('web_phone_change_request', '20261121080000', '9d1b408d83a17af07521661692b75fa6'), ('web_prepare_download', '20261121000000', '97df63aae3736a00a167073a64aeccdb'), ('web_purchase_precheck', '20261116150000', 'df9e8bdf4997939b2d72650d85aa36aa'), ('web_register', '20261116140000', 'ba60a1cf82ef5bf4fb88e8cfcbac407d'), ('web_register_core', '20261120230000', '6c86031d5e47195fba8061488cd6b44d'), ('web_register_device', '20261121030000', '959de18d561badf25510c78acdff3976'), ('web_report', '20261121050000', '99c2d5880996cfecba3440f742883f6a'), ('web_set_name', '20261023160000', '8310c0d5d65a395e670c16b7dcb662ae'), ('web_status', '20261116140000', '86ae7805fbb44c0330ede6afb4e38123'), ('web_status_core', '20261023100000', '3446cd4e3bcce27d9cb91b85d5b75e7c'), ('web_swaps_for_me', '20261121080000', 'b1f2eaad53d000be72cd4c5d35b298b2'), ('web_take_snapshot', '20261119140000', '7a9f5a2605aa9bba4b2db52a40e8b68f'), ('web_vip_numbers', '20261119160000', 'd5e4b57b803eeae89b21fef764f1a89e'), ('_web_clean', '20261121096000', 'f2776c4b7db63bcb02d5efe1fcace0fa'), ('_web_client_ip', '20261121096000', 'ef4c6fac8548b6eb9dec00fe7b74b0d4'), ('_web_name_key', '20261121096000', '9210ae38dc965a6b08702fac07411aaa'), ('_web_name_suggestions', '20261121096000', '1340df5e16dc7726abc8b60837ac1df4'), ('_web_pin_new', '20261121096000', 'bd596fa1cbf508dda871f5fb199cbaad'), ('_web_vcf_esc', '20261121096000', 'f952e86c0c384d1245184759870154f4'), ('web_admin_ban', '20261121096000', 'dc6a57236bb00cd75d0a433d2a7806ac'), ('web_admin_contacts_set', '20261121096000', '85a0153e19754df30c09501751bf2a50'), ('web_admin_delete_user', '20261121096000', '4812a37d90ae378a020b768d7a9edd9e'), ('web_admin_grant_redownload', '20261121096000', 'e026ade9fa2f0cc5890852d1c26b3b94'), ('web_admin_pool_days', '20261121096000', 'f7660af738105f88fb8f87413e3cc738'), ('web_admin_run_snapshot', '20261121096000', 'aeb5e6deb98b83aeea28b128c8c3d915'), ('web_admin_unlog_verified', '20261121096000', '31bf032f7d3753209a6b6d3d402bb976'), ('web_public_config', '20261121096000', '7a5202ea42c7cae089caa7d0aca9c7ce')),
called(fn) as (values ('web_admin_backup'), ('web_admin_ban'), ('web_admin_change_key'), ('web_admin_contacts_set'), ('web_admin_delete_user'), ('web_admin_deleted_list'), ('web_admin_deleted_restore'), ('web_admin_device_flag_allow'), ('web_admin_device_flag_dismiss'), ('web_admin_device_flags'), ('web_admin_grant_redownload'), ('web_admin_group'), ('web_admin_limit_set'), ('web_admin_log_verified'), ('web_admin_log_verified_day'), ('web_admin_new_members'), ('web_admin_new_members_done'), ('web_admin_phone_change_decide'), ('web_admin_phone_changes'), ('web_admin_pool_days'), ('web_admin_pool_list'), ('web_admin_release_get'), ('web_admin_release_now'), ('web_admin_release_set'), ('web_admin_report_close'), ('web_admin_report_limit'), ('web_admin_reports'), ('web_admin_reports_closed'), ('web_admin_reset_pin'), ('web_admin_run_snapshot'), ('web_admin_saved_add'), ('web_admin_saved_delete'), ('web_admin_saved_list'), ('web_admin_settings_get'), ('web_admin_settings_set'), ('web_admin_stats'), ('web_admin_unlog_verified'), ('web_admin_user'), ('web_admin_users'), ('web_admin_vip_delete'), ('web_admin_vip_list'), ('web_admin_vip_save'), ('web_app_restore'), ('web_app_seen'), ('web_boot'), ('web_confirm_download'), ('web_day_contacts'), ('web_delete_account'), ('web_device_seen'), ('web_gone_for_me'), ('web_history'), ('web_last_release'), ('web_login'), ('web_logout'), ('web_next_release'), ('web_people'), ('web_phone_change_cancel'), ('web_phone_change_mine'), ('web_phone_change_request'), ('web_prepare_download'), ('web_public_config'), ('web_register'), ('web_register_device'), ('web_report'), ('web_set_name'), ('web_status'), ('web_swaps_for_me'), ('web_verify_purchase'), ('web_vip_numbers')),
known(t) as (values ('web_users'), ('web_verifications'), ('web_day_releases'), ('web_snapshot_contacts'), ('web_snapshot_meta'), ('web_received'), ('web_downloads'), ('web_redownload_grants'), ('web_settings'), ('web_release_config'), ('web_signup_log'), ('web_admin_attempts'), ('web_sessions'), ('web_purchases'), ('deleted_numbers'), ('admin_saved_messages'), ('web_vip_contacts'), ('web_devices'), ('web_banned_devices'), ('web_device_flags'), ('web_device_allow'), ('web_gone'), ('web_reports'), ('web_phone_changes'), ('web_phone_swaps'), ('web_release_backup')),
mine as (
  select p.oid, p.proname, p.prosrc from pg_proc p
   where p.pronamespace = 'public'::regnamespace
     and not exists (select 1 from pg_depend d where d.objid = p.oid and d.deptype = 'e')),
match as (
  select 17 as n, '17 Same as the code files' as area, c.fn as item,
         case when not exists (select 1 from mine m where m.proname = c.fn) then 'MISSING'
              when exists (select 1 from mine m where m.proname = c.fn and md5(regexp_replace(regexp_replace(m.prosrc, '--[^\n]*', '', 'g'), '\s+', '', 'g')) = c.h) then 'OK'
              else 'WARN' end as status,
         'live version differs from file ' || c.file || ' (edited by hand, or an older copy)' as detail
    from code c),
patched as (
  select 17, '17 Same as the code files', '_web_candidates (changed in place)',
         case when bool_and(position(x in coalesce(pg_get_functiondef(to_regprocedure('public._web_candidates(bigint)')), '')) > 0) then 'OK' else 'WARN' end,
         'missing one of the release-batch or admin/VIP lines'
    from unnest(array['join public.web_verifications v on v.user_id = p_uid', 'coalesce(rv.batch, rv.day) = coalesce(r.batch, r.day)',
                      'coalesce(r.batch, r.day) >= u.registered_on', '_web_is_admin_number(s.phone)']) x
  union all
  select 17, '17 Same as the code files', 'web_admin_stats (changed in place)',
         case when bool_or(position('+ interval ''2 hours'')::date = t' in pg_get_functiondef(m.oid)) > 0) then 'OK' else 'WARN' end,
         'the 10pm-day line is missing'
    from mine m where m.proname = 'web_admin_stats'),
used as (
  select m.proname from mine m
   where m.proname in (select fn from called)
      or m.proname in ('web_apply_purchase', 'web_purchase_precheck', 'web_admin_group_ids', 'web_admin_devices') -- Edge Functions; kept for the admin
      or exists (select 1 from mine o where o.oid <> m.oid and position(m.proname in o.prosrc) > 0)
      or exists (select 1 from pg_trigger t where t.tgfoid = m.oid)
      or exists (select 1 from cron.job j where position(m.proname in j.command) > 0)),
left1 as (
  select 18 as n, '18 Leftovers' as area, 'Functions nothing uses (not called by the website/apps, other functions, triggers or the nightly job)' as item,
         case when count(*) = 0 then 'OK' else 'WARN' end as status,
         count(*) || coalesce(' · ' || string_agg(proname, ', ' order by proname), '') as detail
    from (select distinct proname from mine where proname not in (select proname from used)) z),
left2 as (
  select 18, '18 Leftovers', 'Tables the code does not know about',
         case when count(*) = 0 then 'OK' else 'INFO' end,
         count(*) || coalesce(' · ' || string_agg(c.relname, ', ' order by c.relname), '')
    from pg_class c
   where c.relnamespace = 'public'::regnamespace and c.relkind in ('r', 'p')
     and c.relname not in (select t from known)
     and not exists (select 1 from pg_depend d where d.objid = c.oid and d.deptype = 'e')),
left3 as (
  select 18, '18 Leftovers', 'Functions older than the code files (made in the dashboard)',
         'INFO', count(*) || coalesce(' · ' || string_agg(proname, ', ' order by proname), '')
    from (select distinct proname from mine where proname ~ '^_?web_|^vg_' and proname not in (select fn from code)
            and proname not in ('_web_candidates', 'web_admin_stats')) z),
guard as (
  select 19 as n, '19 Guards' as area, 'Tables with no primary key' as item,
         case when count(*) = 0 then 'OK' else 'PROBLEM' end as status,
         count(*) || coalesce(' · ' || string_agg(t, ', ' order by t), '') as detail
    from known k(t) join pg_class c on c.oid = to_regclass('public.' || k.t)
   where not exists (select 1 from pg_constraint x where x.conrelid = c.oid and x.contype = 'p')
  union all
  select 19, '19 Guards', 'web_users: one account per number is enforced by the database',
         case when exists (select 1 from pg_index i join pg_class ic on ic.oid = i.indexrelid
                            where i.indrelid = 'public.web_users'::regclass and i.indisunique
                              and pg_get_indexdef(i.indexrelid) ~ 'phone') then 'OK' else 'PROBLEM' end, 'unique rule on phone'
  union all
  select 19, '19 Guards', 'web_received: a person given once per member is enforced',
         case when exists (select 1 from pg_index i where i.indrelid = 'public.web_received'::regclass and i.indisunique
                              and pg_get_indexdef(i.indexrelid) ~ 'user_id' and pg_get_indexdef(i.indexrelid) ~ 'phone') then 'OK' else 'PROBLEM' end, 'unique rule on member + phone'
  union all
  select 19, '19 Guards', 'user_id columns not linked to web_users (rows can be left behind)',
         'INFO', count(*) || coalesce(' · ' || string_agg(c.table_name, ', ' order by c.table_name), '')
    from information_schema.columns c
   where c.table_schema = 'public' and c.column_name = 'user_id' and c.table_name in (select t from known)
     and not exists (select 1 from pg_constraint x where x.contype = 'f' and x.conrelid = to_regclass('public.' || c.table_name)
                       and x.confrelid = 'public.web_users'::regclass)
  union all
  select 19, '19 Guards', 'Links with no index (slow deletes as members grow)',
         case when count(*) = 0 then 'OK' else 'WARN' end,
         count(*) || coalesce(' · ' || string_agg(x.conrelid::regclass::text, ', '), '')
    from pg_constraint x
   where x.contype = 'f' and x.connamespace = 'public'::regnamespace
     and not exists (select 1 from pg_index i where i.indrelid = x.conrelid and (i.indkey::int2[])[0] = x.conkey[1])),
checks(n, area, item, kind, q) as (values
  (20, '20 Rules in your data', 'Members given more contacts than their limit', 'bad', 'select u.id::text as x from public.web_users u where (select count(*) from public.web_received r where r.user_id = u.id) > public._web_limit(u.id)'),
  (20, '20 Rules in your data', 'Next contacts lined up include admin/VIP, banned or own numbers', 'bad', 'select distinct u.id::text as x from public.web_users u cross join lateral public._web_candidates(u.id) c where public._web_is_admin_number(c.phone) or public.vg_phone_key(c.phone) = public.vg_phone_key(u.phone) or exists (select 1 from public.web_users b where b.banned and public.vg_phone_key(b.phone) = public.vg_phone_key(c.phone)) or exists (select 1 from public.deleted_numbers d where public.vg_phone_key(d.phone) = public.vg_phone_key(c.phone))'),
  (20, '20 Rules in your data', 'Next contacts lined up include people already given', 'bad', 'select distinct u.id::text as x from public.web_users u cross join lateral public._web_candidates(u.id) c where exists (select 1 from public.web_received r where r.user_id = u.id and public.vg_phone_key(r.phone) = public.vg_phone_key(c.phone))'),
  (20, '20 Rules in your data', 'Verified members missing from that day''s saved list', 'warn', 'select distinct v.user_id::text as x from public.web_verifications v join public.web_users u on u.id = v.user_id where not u.banned and v.day < public._web_today() and exists (select 1 from public.web_snapshot_contacts s0 where s0.day = v.day) and not exists (select 1 from public.web_snapshot_contacts s where s.day = v.day and public.vg_phone_key(s.phone) = public.vg_phone_key(u.phone))'),
  (20, '20 Rules in your data', 'Released days with verified members but no saved list', 'warn', 'select to_char(r.day, ''DD Mon'') as x from public.web_day_releases r where exists (select 1 from public.web_verifications v where v.day = r.day) and not exists (select 1 from public.web_snapshot_contacts s where s.day = r.day)'),
  (20, '20 Rules in your data', 'Contacts given from days that were never released', 'bad', 'select distinct to_char(x.day, ''DD Mon'') as x from public.web_received x where x.day is not null and not exists (select 1 from public.web_day_releases r where r.day = x.day)'),
  (20, '20 Rules in your data', 'Contact files with an unknown status', 'bad', 'select distinct status as x from public.web_downloads where status not in (''pending'', ''done'', ''abandoned'')'),
  (20, '20 Rules in your data', 'Banned members who can still be given out', 'bad', 'select distinct s.day::text as x from public.web_snapshot_contacts s join public.web_users b on public.vg_phone_key(b.phone) = public.vg_phone_key(s.phone) where b.banned and s.day >= public._web_today() - 1'),
  (20, '20 Rules in your data', 'Saved lists: last day · last released day', 'info', 'select coalesce(to_char((select max(day) from public.web_snapshot_contacts), ''DD Mon''), ''none'') || '' · '' || coalesce(to_char((select max(day) from public.web_day_releases), ''DD Mon''), ''none'') || '' (today is '' || to_char(public._web_today(), ''DD Mon'') || '')'''),
  (20, '20 Rules in your data', 'Contacts given in total · members limit-full', 'info', 'select (select count(*) from public.web_received) || '' · '' || (select count(*) from public.web_users u where (select count(*) from public.web_received r where r.user_id = u.id) >= public._web_limit(u.id))'),
  (20, '20 Rules in your data', 'Expired login tokens still stored', 'info', 'select count(*)::text from public.web_sessions where expires_at < now()')
),
ran as (select n, area, item, kind, pg_temp.vg_run(q, kind) as v from checks),
dat as (
  select n, area, item,
         case when v like 'ERROR|%' then 'MISSING' when kind = 'info' then 'INFO'
              when split_part(v, '|', 1) = '0' then 'OK' when kind = 'bad' then 'PROBLEM' else 'WARN' end,
         case when v like 'ERROR|%' then 'cannot check: ' || substr(v, 7) when kind = 'info' then v
              when split_part(v, '|', 1) = '0' then 'none'
              else split_part(v, '|', 1) || coalesce(nullif(' · e.g. ' || split_part(v, '|', 2), ' · e.g. '), '') end
    from ran),
tr as (select ord, t from unnest(pg_temp.vg_tests()) with ordinality u(t, ord)),
tests as (
  select 21 as n, '21 Rule tests (all undone)' as area, lpad(ord::text, 2, '0') || ' ' || split_part(t, '|', 1) as item,
         split_part(t, '|', 2) as status, split_part(t, '|', 3) as detail from tr),
alls as (select * from match union all select * from patched union all select * from left1 union all select * from left2
         union all select * from left3 union all select * from guard union all select * from dat union all select * from tests)
select n, area, item, status, detail from alls where status <> 'OK'
union all
select 9998, 'TESTS', count(*) || ' rule tests', 'passed ' || count(*) filter (where status = 'OK'), 'failed ' || count(*) filter (where status <> 'OK') from tests
union all
select 9999, 'SUMMARY', count(*) || ' checks', 'OK ' || count(*) filter (where status = 'OK'),
       'PROBLEM ' || count(*) filter (where status = 'PROBLEM') || ' · MISSING ' || count(*) filter (where status = 'MISSING')
       || ' · WARN ' || count(*) filter (where status = 'WARN') || ' · INFO ' || count(*) filter (where status = 'INFO')
  from alls
order by 1, 3;
