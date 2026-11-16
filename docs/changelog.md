# Database changelog

Each change is one migration file. Newest first.

## 20261116260000: saved list shows the time logged and app / website
- Status: APPLIED 16 Nov 2026 by the owner. Pre-check passed (logged_at and app_seen_at, both timestamp with time zone). Confirmed: the function has app_seen_at, the key works, and the 15 Nov list's first row has at, id and app.
- Re-creates web_admin_pool_list (same arguments, total, order and cap) with three more fields per row: id, at (web_verifications.logged_at) and app (web_users.app_seen_at is not null). The old definition was read from the live database on 16 Nov 2026 and kept as is otherwise.
- BEFORE applying, run this read-only check (rule 2). Expect 2 rows: logged_at (timestamp with time zone) and app_seen_at:
  select table_name, column_name, data_type from information_schema.columns where table_schema = 'public' and ((table_name = 'web_verifications' and column_name = 'logged_at') or (table_name = 'web_users' and column_name = 'app_seen_at'));
- Confirm after applying: select public.web_admin_pool_list('YOUR_ADMIN_KEY', public._web_today() - 1) -> 'rows' -> 0;  (expect id, at and app in the row)

## 20261116250000: "Signed up today" and "Got file today" lists
- Status: APPLIED 16 Nov 2026 by the owner. Pre-check passed (0, 0, 0, 0 just after the 10pm turnover; no column errors). Confirmed: web_admin_group answers ok for joined_today and got_file_today.
- Re-creates _web_group_members and web_admin_group (same arguments and answer) with two more groups, using exactly the rules web_admin_stats uses for the Today numbers (read from the live database on 16 Nov 2026): joined_today = registered_on = _web_today() and not banned; got_file_today = a web_downloads row with status 'done', confirmed_at set, and (confirmed_at in Lagos + 2 hours)::date = _web_today(). web_downloads has no "day" column (it has "days"), which the first pre-check caught. The box counts downloads; the list shows each person once (a member with two downloads today is one row). web_admin_group_ids is unchanged.
- BEFORE applying, run this read-only check (rule 2), with the admin key. Expect each pair to match (the got-file pair can differ only if someone downloaded twice today):
  select (s->>'signups_today') as stats_signed_up, (select count(*) from public.web_users where registered_on = public._web_today() and not banned) as list_signed_up, (s->>'downloads_today') as stats_got_file, (select count(distinct user_id) from public.web_downloads where status = 'done' and confirmed_at is not null and ((confirmed_at at time zone 'Africa/Lagos') + interval '2 hours')::date = public._web_today()) as list_got_file from (select public.web_admin_stats('YOUR_ADMIN_KEY') as s) x;
- Confirm after applying: select public.web_admin_group('YOUR_ADMIN_KEY', 'joined_today', '', 1, 0);  (expect ok true)

## 16 Nov 2026: admin page new layout, no database change
- Website only (admin.html, admin.js, style.css). Same features, moved. Today: one white "Log +" pill in the header (opens: From screenshots in the admin app, Paste or type, See who's logged); a short list (Notify members, Message everyone, New members file; the file row is hidden inside the admin app, which saves members by itself); 6 numbers on top (Verified, Signed up, Got file, Members, Locked, Banned; each opens its list in Users), the list under them; saved-list day screen shows icon, name and number, 30 at a time. No floating button and no Sign out or moon in the header. Pool renamed Lists: tonight's count and the release schedule on top, "Pick date" chip in the header. Users: count for the chosen group on the right of the title with app/web icons, filter button (Unverified, Locked, Banned) next to it, 4 pills, 30 members at a time with the next 30 loading by themselves at the end of the list (Show more is the fallback), Remind fixed above the bottom bar, Undo on today's Verified rows (web_admin_unlog_verified). Settings: a short menu where each row shows its value and opens a sheet with one Save; dark mode, Backup, Contacts sync (app only) and Sign out moved here. Inside the admin app: the member app's header gradient, and the page tells the app when it is signed in (VGAdminApp.ready) so the app's cover lifts.

## 20261116240000: member groups, "uses the app", reminders
- Status: APPLIED 16 Nov 2026 by the owner. Pre-check passed (locked, registered_on; _web_received_count, _web_limit, web_status_core). Confirmed: web_app_seen, web_admin_group, web_admin_group_ids and the column app_seen_at exist. Without it, the admin page's Users tab falls back to the old list for All and Verified (no app/website counts) and says Never reshared and Limit full need this update.
- Adds column web_users.app_seen_at (last time the member's Android app checked in), public web_app_seen(p_phone, p_pin) (the app calls it at most every 6 hours; answers the member's id), admin web_admin_group(p_key, p_group, p_search, p_limit, p_offset) (groups all, verified, pending, locked, banned, never, limit_full, with app and website counts), admin web_admin_group_ids(p_key, p_group) (ids in never or limit_full who use the app, for the Edge Function), and internal _web_group_members(p_group). Changes no existing function.
- "never" = no counted verification ever. "limit_full" = received >= limit (_web_received_count, _web_limit). Banned members are left out of both.
- BEFORE applying, run this read-only check (rule 2). Expect: locked, registered_on present (2 rows); then true, true, true:
  select column_name from information_schema.columns where table_schema = 'public' and table_name = 'web_users' and column_name in ('locked','registered_on');
  select to_regprocedure('public._web_received_count(bigint)') is not null or to_regprocedure('public._web_received_count(integer)') is not null as received_fn, to_regprocedure('public._web_limit(bigint)') is not null or to_regprocedure('public._web_limit(integer)') is not null as limit_fn, to_regprocedure('public.web_status_core(text,text)') is not null as status_core;
- Confirm after applying: select public.web_admin_group('YOUR_ADMIN_KEY', 'all', '', 1, 0);  (expect ok true with total, app, web)

## 16 Nov 2026: notify-status v2 (own messages, group reminders, 10pm wake-up), no database change
- Status: DEPLOYED 16 Nov 2026 by the owner ("Successfully updated edge function"). Scheduled job viewgrow-wake created (job id 4, '5 21 * * *', active). Secret CRON_SECRET added (confirmed by the owner). Not yet tested live (needs the new app on a phone). Replace the function's code with supabase/functions/notify-status/index.ts. Old admin pages keep working (no kind = "status").
- New secret CRON_SECRET (any long random text). Then the scheduled job for the wake-up (pg_cron and pg_net; run in the SQL Editor, with the same secret):
  create extension if not exists pg_net;
  select cron.schedule('viewgrow-wake', '5 21 * * *', $$ select net.http_post(url := 'https://gsronikuyabiiyczjxif.supabase.co/functions/v1/notify-status', headers := jsonb_build_object('Content-Type', 'application/json', 'x-cron-secret', 'PASTE_THE_CRON_SECRET'), body := '{"kind":"wake"}'::jsonb); $$);
  21:05 UTC = 10:05pm Lagos, five minutes after the nightly release job. Check: select jobname, schedule from cron.job where jobname = 'viewgrow-wake';
  To remove it: select cron.unschedule('viewgrow-wake');

## 20261116230000: "Download new members" for the admin
- Status: APPLIED 16 Nov 2026 by the owner. Pre-check passed (web_users id, name, phone, banned; _web_admin_ok, _web_vcf_esc, unique web_settings key). Confirmed: both functions exist. First real use from the admin page still to do.
- Adds web_admin_new_members(p_key, p_from_id, p_upto_id) and web_admin_new_members_done(p_key, p_upto_id), and the settings row members_file_upto (the bookmark, 0 = never downloaded). The file has every member who signed up (website or app) with an id above the bookmark, not banned, named "Name VG", company VG. The bookmark moves only after the admin page has saved the file. Changes no other function and no table.
- BEFORE applying, run this read-only check (rule 2). Expect 4 rows for web_users, true, true, true:
  select column_name from information_schema.columns where table_schema = 'public' and table_name = 'web_users' and column_name in ('id','name','phone','banned');
  select to_regprocedure('public._web_admin_ok(text)') is not null as admin_ok, to_regprocedure('public._web_vcf_esc(text)') is not null as vcf_esc, exists (select 1 from pg_indexes where schemaname = 'public' and tablename = 'web_settings' and indexdef ilike '%unique%(key)%') as settings_key_unique;
- Confirm after applying: select to_regprocedure('public.web_admin_new_members(text,bigint,bigint)') is not null, to_regprocedure('public.web_admin_new_members_done(text,bigint)') is not null;  (expect true, true)

## 16 Nov 2026: Edge Function notify-status ("Today's status is up" alert), no database change
- Status: secret added and function deployed by the owner on 16 Nov 2026 (deployed code checked: same as this file). Not yet tested live.
- supabase/functions/notify-status/index.ts. The admin page's "Notify members" button (Today) calls it with the admin key. It checks the key with web_admin_stats (same lockout as the admin page), then sends one Firebase Cloud Messaging message to the topic "members". App phones join that topic while logged in (StatusPush.kt). No phone tokens are stored.
- To set up: (1) Firebase console, project viewgrow-site: Project settings > Service accounts > Generate new private key. (2) Supabase > Edge Functions > Secrets: add FIREBASE_SERVICE_ACCOUNT_JSON with the whole file. Do not send the file to anyone or put it in the app or website. (3) Deploy: supabase functions deploy notify-status --no-verify-jwt (or paste it in the dashboard as a new function named notify-status with "Verify JWT" off). Verify JWT must be off: the admin page sends the public key, and the function checks the admin key itself.
- Test: press Notify members on Today with a phone that has the new app installed and is logged in. Answers: NOT_SET_UP means the secret is missing; FIREBASE_ERROR means the key is wrong or for another project (see the function's logs).

## 20261116220000: web_last_release (for the app's morning "people joined last night" message)
- Status: APPLIED 16 Nov 2026 by the owner. Pre-check passed (4 columns present; last_batch 2026-11-15, today 2026-11-16). Confirmed with a real member: {"ok":true,"batch":"2026-11-15","people":2,"you_in":true,"last_night":true}.
- Adds one public, read-only function web_last_release(p_phone, p_pin). Checks number and password with web_status_core, then returns the last released batch, whether it was last night, how many people were in it (web_snapshot_meta.contact_count, the verified people saved that night) and whether this member was one of them (web_verifications). Changes no table and no other function.
- BEFORE applying, run this read-only check (rule 2). Expect 4 rows, and the last line's two dates one day apart after a normal nightly release:
  select table_name, column_name from information_schema.columns where table_schema = 'public' and ((table_name = 'web_day_releases' and column_name in ('day','batch')) or (table_name = 'web_snapshot_meta' and column_name = 'contact_count') or (table_name = 'web_verifications' and column_name = 'user_id'));
  select max(coalesce(batch, day)) as last_batch, public._web_today() as today from public.web_day_releases;
- Confirm after applying: select to_regprocedure('public.web_last_release(text,text)') is not null;  (expect true). Then, with a real member's number and password: select public.web_last_release('0801...', 'password');  (expect ok true; last_night true in the morning after a release).

## 20261116200000: contacts in the download file carry the company tag VG
- Status: APPLIED 16 Nov 2026 by the owner. The first run used ORG:VGC; the owner chose VG instead, and the VG version was run over it. Confirmed: has ORG:VG, no ORG:VGC, NOTE:ViewGrow- present. The app (ContactsSaver.TAG) uses VG too.
- Why: the app saves every contact with the company VG. The website file had no company, so members who import the file saw plain names. Now both show the person's own name with company VG.
- Changes only web_prepare_download: one added line, ORG:VG, on each contact card and on the marker card. The name (FN) is unchanged, so the app (which reads FN, TEL and NOTE) keeps working. Based on the live function text read on 16 Nov 2026. The file stops with an error, changing nothing, if the live function is different.
- Applies to files made after it runs. A file already waiting (pending) keeps the old format for that day. Contacts already imported are not changed.
- Confirm: select position('ORG:VG' in pg_get_functiondef('public.web_prepare_download(text,text)'::regprocedure)) > 0;  (expect true)

## 16 Nov 2026: live check against the notes (no database change)
Ran check_live_matches_claims.sql queries 1 to 8. All passed: functions, 10pm clock, nightly job (7 of 7 recent runs succeeded), release schedule installed (Daily), public settings, row-level security, no leftovers, data counts (13 members, 3 verifications, 1 finished download, 0 purchases), table rules and the download code. Not yet tested live: a deleted number being blocked (no account deleted yet), a real purchase.

## 16 Nov 2026: clean-up
- Removed outdated notes, an unapplied draft entry, stale statuses and unused app files (see STATE.md rule 6). No database change.
- Replaced check_app_and_delete.sql and check_reshare_tally.sql (out of date) with check_live_matches_claims.sql.

## 16 Nov 2026: Google Play readiness (account deletion, purchases)
All of this is LIVE (verified against the live database on 16 Nov 2026). Newest first within the day.

### 20261116180000: delete account removes the member's data
- Status: applied by owner and confirmed (a read of the live function text shows the new version).
- web_delete_account(p_phone, p_pin) now: checks number and password (web_status_core), puts the number on deleted_numbers, deletes the member's web_purchases rows and web_snapshot_contacts rows, then deletes the web_users row. web_downloads, web_received, web_redownload_grants and web_verifications go with it automatically (ON DELETE CASCADE). Banned or locked members cannot delete from the app (same as admin Delete user, which refuses banned members).
- web_admin_delete_user is unchanged. It does not add the number to deleted_numbers (on purpose, so the admin can reset someone) and does not delete web_purchases rows.

### Purchases: verify-purchase Edge Function (deployed 16 Nov 2026)
- The Edge Function supabase/functions/verify-purchase/index.ts is deployed. Secret GOOGLE_SERVICE_ACCOUNT_JSON is set. Test with a fake token returned NOT_FOUND_AT_GOOGLE (Google key and permissions work).
- Added one check to it: a purchase Google already marked consumed is refused (ALREADY_USED), so a deleted member's old token cannot be replayed.
- web_purchase_precheck and web_apply_purchase were already live (created earlier by the owner, no file existed). Recorded in 20261116150000_purchases_record_of_live.sql. A second, different web_apply_purchase was drafted by mistake and never run; it was deleted.
- There is no database function web_verify_purchase. The app sends that name and Api.kt routes it to the Edge Function.
- Still to test: a real purchase with a Play test account (needs the three products in Play Console).

### 20261116140000: deleted-numbers list
- Status: applied (live: deleted_numbers, web_status_core, web_register_core, web_delete_account all exist; confirmed 16 Nov 2026).
- 16 Nov 2026 clean-up: the old web_delete_account (the one without data deletion) was removed from this file. The function now lives only in 20261116180000.
- Adds deleted_numbers. web_status and web_register refuse a deleted number (ACCOUNT_DELETED). The originals were renamed web_status_core and web_register_core and are blocked for the public key.
- To let a number back: delete from public.deleted_numbers where public.vg_phone_key(phone) = public.vg_phone_key('THE_NUMBER');

### Website pages
- Privacy, Terms and Delete My Account pages rewritten to match the app (delete in the app, number kept on a deleted list, admin delete for members who cannot log in). Last updated 16 November 2026.

## 20261030100000: release schedule (daily, weekly, monthly)
Status: applied (confirmed 16 Nov 2026: config table, backup table, batch column and all seven functions exist; schedule is Daily, last saved 29 Oct)
- New setting web_release_config (default Daily, so nothing changes until switched) and admin Settings, Release schedule, with Save schedule and Release now. Next release time and days waiting are shown there.
- web_nightly_snapshot still saves each closed day every night, and now releases the saved days only on a release night (Daily: every night; Weekly: the chosen weekday; Monthly: the chosen date, or the last day). Release day is the day that just closed: Sunday 10pm releases when Sunday closes.
- web_take_snapshot only saves; it no longer releases.
- web_day_releases gets a batch column. Days released together share a batch. _web_candidates now matches members by batch, not by single day, and the registered_on check uses the batch day. In Daily mode a batch is one day, so results are the same as before.
- New: web_admin_release_get, web_admin_release_set, web_admin_release_now, web_next_release (public), and internal _web_is_release_day, _web_next_release, _web_release_pending.
- The live versions of web_nightly_snapshot, web_take_snapshot and _web_candidates are saved in web_release_backup first. supabase/rollback/release_schedule_rollback.sql puts them back, sets Daily, and releases anything waiting.
- The app shows the next release time and counts down to it; site and blog wording says "release" instead of a fixed daily time.
- The candidates change edits the live function text and stops with an error, changing nothing, if the expected lines are not found.

## 20261029213000: admin stats follow the 10pm day
Status: applied in SQL Editor by owner (29 Oct 2026); check returned patched = true
- A read-only check of all functions, column defaults and indexes found one place still using its own Lagos clock: web_admin_stats counted downloads confirmed today from the Lagos calendar date. It now shifts confirmed_at by the same 2 hours, so the count matches the 10pm day.
- The migration edits that one expression in the live function and stops without changing anything if the expected text is not found.
- Nothing else in the database uses its own clock for the day.

## 20261029210000: day boundary at 10pm Lagos
Status: applied in SQL Editor by owner (29 Oct 2026); _web_today() shows 2 hours and cron is 0 21 * * *
- _web_today() now returns the Lagos date plus 2 hours, so a "day" runs from 10pm to 10pm. Reshares, verified days, downloads, the Today screen and the nightly list all use it. Nothing else in the database changed.
- Cron job viewgrow-snapshot moves from 23:00 UTC (midnight Lagos) to 21:00 UTC (10pm Lagos). At 10pm the new day starts and the day that just ended is saved and released, so members verified that day download the same evening.
- A reshare verified after 10pm counts for the next day and is in the next 10pm drop.
- Admin shows the next date from 10pm. At 10:30pm Tuesday, Today shows Wednesday.
- Site copy, the app countdown and the admin labels changed from midnight to 10pm.
- If run after 10pm Lagos, press Save list now in admin afterwards, so the day that just ended is saved.
- Check after running: select public._web_today(), (now() at time zone 'Africa/Lagos')::date; and select jobname, schedule from cron.job; (expect 0 21 * * *).
- Nothing is deleted. The live database had no verifications or downloads when this was written.

## 20261027233000: log reshares for a chosen day
Status: applied in SQL Editor by owner (2026-10-28)
- New function web_admin_log_verified_day(key, day, ids, numbers). The old web_admin_log_verified (today only) is not changed and is still used when the chosen day is today.
- Admin, Today, Log reshares: new Day box in the sheet header, beside the title (today by default, no later day). The green button names the day, for example "Log as verified for Tue 27 Oct".
- A day after today is refused. A member is skipped if banned, if the day is before they registered, if the day is on or before their capped day, or if they are at their limit now.
- If that day's list was already saved by the nightly job, the member is also added to it (and the saved count goes up), so the Pool tab, the day's list and other members' downloads agree.
- Nothing is deleted.

## 27 Oct 2026: live database checked again, notes brought up to date
Status: read-only checks run in SQL Editor (supabase/check_how_it_works.sql), nothing changed
- Confirmed applied: 20261024010000 (web_reshares table and reshare functions are gone) and 20261023160000 (name lock).
- Row-level security is on for every table, with no policies and no triggers. The public key cannot read the four tables that still carry read and write permissions (web_snapshot_contacts, web_snapshot_meta, web_day_releases, web_signup_log): checked by reading them as the public role, 0 rows seen where the real counts are 8.
- Cron job viewgrow-snapshot runs at 23:00 UTC daily; the last 5 runs succeeded.
- Live data at the time: 1 member, no verifications, downloads, received contacts or saved contacts.
- Found: web_admin_set_key and web_admin_user_limit exist live (now listed in schema.md). Six unused settings rows remain (listed in schema.md).
- Found: web_admin_unlog_verified's "already downloaded" guard no longer works. No screen calls it. Not changed.
- Found: no screen calls "change password and clear name", or the single verify and undo buttons. Not changed.
- Not yet proven live: manual logging, the nightly release, the download. Live test:
  1. Register a spare number on the site. 2. In admin, Today, press +, paste that member's name (or number), press Log as verified. 3. After the nightly release (10pm Lagos from 20261029210000; midnight before), log in as that member. 4. Press Import contacts.

## 24 Oct 2026: remove all reshare-from-app code
Status: applied by owner (confirmed 27 Oct 2026)
- New migration 20261024010000_remove_reshares.sql. Drops the reshare functions and the web_reshares table.
- Manual logging (web_admin_log_verified, Log reshares on Today) is kept.
- Deleted the unapplied drafts 20261023150000_reshare_status.sql and the earlier 20261024010000 reject-reason draft.
- WARNING: dropping web_reshares deletes all stored reshare rows.

## 23 Oct 2026: live database checked against the repo
Status: read-only checks run in SQL Editor, nothing changed
- Full schema dump matched the docs for all 12 tables, row-level security (on everywhere, no policies or triggers), public and admin functions, and the internal function blocks.
- Confirmed applied: 20261023140000, 20261023130000, 20261023120000, 20261023100000, 20261022140000.
- Found in the database but in no migration file: unique index web_users_name_key_uniq (normalised member name). Owner to confirm its origin before any change.
- web_reshares.name still exists as a column but is always empty.

## 20261023160000: member name is permanent once set
Status: applied (confirmed 27 Oct 2026)
- web_set_name: refuses with NAME_LOCKED if the member already has a name. A blank name can still be entered once. Blank names come only from the admin's "clear name" option.
- No change to existing names. Nothing is deleted.

## 20261023140000: only matched reshares are stored, no names
Status: applied by owner via SQL Editor on 23 Oct 2026 (confirmed by schema check)
- web_admin_reshare_push: stores only rows that are matched ("found") with a valid number. Anything else is skipped and counted in the response. Names are never stored.
- Table check added (NOT VALID, so it applies to new rows only): match_status must be "found" and name must be empty.
- Before this, old app builds could store unmatched rows and names. The owner removed those 7 rows and cleared the names on 23 Oct 2026.
- Duplicates: a unique index allows one stored reshare per number per Lagos day. Repeat rows are skipped, not treated as errors.
- The current app is unaffected. Nothing is deleted by this migration. Duplicates from before this change were removed by hand (the two later rows for one number, 23 Oct 2026).

## 20261023130000: a verified reshare credits the member on its own day
Status: applied by owner via SQL Editor on 23 Oct 2026 (not yet in migration history, see README). Replaces the earlier proposed version of this file.
- web_admin_reshare_set: "Verify" on a reshare with a number credits the member for the reshare's own day (not today). Credit is refused, and the reshare stays unchanged, when: no number, not a registered member, banned, day after today, day before the member registered, day on or before their capped day, or at their contact limit. The response gives the code, and admin.js shows the reason.
- A reshare is marked verified only when the credit happens. Reject and New only record the decision.
- Reshares marked verified before this change were NOT credited. They are not backfilled. Owner to decide separately.
- Nothing is deleted.

## 20261023120000: reshares from the phone app
Status: applied by owner via SQL Editor on 23 Oct 2026 (confirmed by schema check)
- New table web_reshares: one row per reshare the Android app sends. Raw notification text is never sent.
- Each row is stored once (the app's own id), so resending is safe.
- web_admin_reshare_push: used by the app. Admin key required.
- web_admin_reshares_list: today's reshares for the admin Today tab. Admin key required.
- web_admin_reshare_set: marks a reshare new, verified or rejected. Records the decision only. It does not credit contacts or change verified days.
- Nothing is deleted.

## 20261023100000: verified days in history
Status: applied (confirmed by schema check 23 Oct 2026)
- History and the dashboard "Recent" list show each verified day, with the number of contacts that day's snapshot gave the member after duplicates and ineligible numbers are removed.
- The number doesn't depend on logging in or downloading. A member sees it at any login, including after several days.
- Duplicates: a number counts only on the first verified day it appears for that member.
- Nothing is deleted.

## 23 Oct 2026: app-only changes (no database change)
Status: no migration written, nothing run in SQL Editor
- Website changes only: dashboard theme toggle, dark mode, tip wording, stat card and button alignment, and the contact limit button.
- The contact limit button ("UPGRADE CONTACT LIMIT") opens WhatsApp to the admin contact already stored in web_settings (key 'contacts', via web_public_config). If no admin contact is set, the button does not show.
- Viewgrow.netlify.app redirect to viewgrow.site is a Netlify setting in netlify.toml, not a database change.

## Decisions (22 Oct 2026)
- Nothing is deleted automatically. Old data stays until the owner asks for a cleanup, which will be written as a separate, reviewed change.
- Sign-up limit stays at 3 per connection address per day.
- Shared-address risk accepted by the owner. No browser-storage limit added.
- Legacy function web_admin_contact_set and old lock counter rows in web_settings are kept. Removal counts as deletion, so it waits for the owner's request.

## 20261022140000: fewer database calls
Status: applied (confirmed by schema check 23 Oct 2026)
- web_admin_user now also returns the member's received count, limit, personal limit and capped flag. The admin member screen makes one call instead of two or three.
- web_boot is new. It returns the sign-up settings and the member's status in one call. Opening the site with a saved login makes one call instead of two.
- web_admin_user no longer lists abandoned downloads.
- Nothing is deleted.

## 20261022130000: contact limits
Status: applied by owner via SQL Editor on 22 Oct 2026. Not yet in migration history (see README). Pre-check result (members at or over 500 at the time of running): not yet recorded.
- Each member has a lifetime limit of 500 received contacts. Default is set in admin settings (default_limit). A personal limit can be set per member (limit_override).
- A verification is counted only if the member is under their limit when logged. At or over the limit, it is skipped and not recorded.
- Every contact a member receives is recorded in web_received. Nobody is sent the same person twice.
- A download gives only the next contacts up to the member's remaining allowance. The rest are not kept.
- When the limit is hit, capped_day is set. Contacts from days on or before that are never given later.
- After upgrading, only contacts from later days count.
- Unconfirmed downloads are marked 'abandoned' instead of deleted.
- Full re-download restores only what the member already received. It adds nothing new.
- Past confirmed downloads are copied into web_received. Members already at or over 500 are capped immediately.
- Nothing is deleted.

## 20261022120000: admin lockout and PIN hardening
Status: applied by owner via SQL Editor on 22 Oct 2026. Not yet in migration history (see README).
- Admin lockout is per connection instead of global.
- PINs use a stronger hash (cost 10). Old PINs upgrade on next correct login.
- Login errors no longer reveal whether a number is registered.
- Sign-up duplicate check returns a generic message.
- Admin backup no longer includes PIN hashes.
- Nothing deleted. Sign-up limit kept at the existing setting (3).

## 20261022110000: record nightly cron job
Status: record only. The job already exists.
- Records viewgrow-snapshot (23:00 UTC daily) in the migration history.

## Baseline (before migrations)
Status: captured with `supabase db pull` (see README)
- Database state as it was before these records began.

## Font flash fix
- Poppins files are now preloaded in index.html, app.html and admin.html, and font-display is `block`, so text no longer shows in a fallback font before switching to Poppins.
- Admin: Settings has a Dark mode switch (remembered on the device). Dark palette lives in style.css under data-theme="dark".
- Member dashboard: 'Contacts received' always shows received/limit (e.g. 0/500). Admin member sheet shows received/limit too.
- Admin member sheet: 'Contacts received' shows received/limit with source (default or paid plan). Separate Contact limit row removed.
- PWA: manifest.webmanifest, sw.js (network-only, no caching), pwa.js registers the worker on the member app and landing page. Admin not registered. Icon is 192px only; add a 512px icon for best install results.
- Install card: shows on the member dashboard only. iPhone gets Add to Home Screen steps (Safari check). Android/Samsung Internet get an Install button when the browser allows it. Hidden when installed, and for 7 days after Not now / Got it.
- Sign-up: 'Repeat password' field removed. The eye button on the password field shows what was typed.
- Install card: close (×) button added. It dismisses for 7 days, same as Not now / Got it.
- Import tip card (shown after a download): close (×) button added. It hides the tip for the current visit only.
- After a download, a 'Back to dashboard' button returns to the dashboard (it reloads the status). Before, the download screen had no way back.
- Notices in the member app are one sentence each. The bold lead-in now sits inline. Saved notice: 'Saved N contacts to <file>.' Import tip is one line with a close (×).
