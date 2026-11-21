# ViewGrow database check: what was found, removed and why (21 Nov 2026)

## Where things stand
Your database is healthy. Two big checks (about 190 checks plus 40 rule tests) found no real bugs.
Everything the website, the apps and the admin page need exists and works, and your data agrees with itself.

## What was checked
- Part 1: all updates are applied, every function the code calls exists, nothing internal is exposed,
  tables are locked, the nightly job runs every night (10pm), members, contacts, bans, logins, reports,
  number changes and purchases all line up.
- Part 2: live code compared with the code files, unused leftovers, database guards, rules in your real data,
  and 40 rule tests with pretend members (sign up, log in, wrong passwords, reports, auto-ban, unban,
  number change, delete account). Everything the tests did was thrown away.

## What was changed, and why
1. Removed the leftover permissions the public key had on 5 tables. They were already blocked by row security,
   but removing the permission is a second lock. Nothing in the apps changed.
2. Added an ID column to deleted_numbers (the only table without one). It keeps the table tidy and safe.
3. Removed 5 functions nobody uses, so there is less to look after and less to go wrong:
   - _web_contacts, _web_days: old helpers, replaced by a newer one.
   - web_admin_contact_set: old version of the one the admin page really uses.
   - web_admin_user_limit: replaced by web_admin_user, which gives the same numbers.
   - web_setup: first-time setup. It only worked before an admin password existed, so it was never a risk.
   Their code is saved in rollback/tidy_leftovers_rollback.sql in case you ever want them back.

## What was only written down (nothing changed in your database)
4 functions had been improved directly in Supabase without a code file: _web_auth_pin, web_admin_user,
web_day_contacts, web_people. The live versions are the better ones, so I saved them into the project
(migrations/20261121095000_record_live_functions.sql). Do not run that file; it is only a record.

## Not bugs
- 29 released days with no saved list: days when nobody was verified.
- One test failed in the last run (Login token is accepted). The test was the problem: 12 log ins inside
  one moment share the same clock time, so the clean-up that keeps 10 tokens could drop the one being tested.
  Real log ins happen at different times, so the app is fine. I fixed the test.

## Re-run result (21 Nov 2026)
Part 2 was run again with the fixed test: 155 checks, OK 150, PROBLEM 0, MISSING 0, WARN 0, INFO 5.
Rule tests: passed 40, failed 0. The "Login token is accepted" test now passes.

The 5 INFO rows are notes, not problems:
- The 14 dashboard-only functions are now saved in the project (migrations/20261121096000_record_live_functions_2.sql,
  record only, do not run). Re-run part 2 once: that INFO row should now show 0.
- web_banned_devices and web_purchases have a user_id that is not linked to web_users, so their rows stay when a
  user is deleted. For banned devices that is probably wanted. For purchases the owner decided: remove with the
  user. Run migrations/20261121110000_purchases_follow_user.sql once (it links purchases to the member and cleans
  up leftovers). Check after: expect true | 0.
- 50 contacts given in total, 0 members at their limit.
- 0 expired login tokens stored (the clean-up works).
- Last saved list was 20 Nov (checked on 21 Nov, before the 10pm job). Check on 22 Nov that it says 21 Nov. If it
  still says 20 Nov, the nightly job missed a night.

## Still on your side
- Upload website-updated_45.zip.
- Change the admin password (Settings > Admin password).
- Build the apps from VIEWGROW-full-updated_53.zip and test Report a person, Account deleted and Change phone number.
- Google Play Data safety form; check the practice crash report in Crashlytics.
- Later: CAC registration and WhatsApp codes (waiting on money).

## Latest re-run (21 Nov 2026, after the purchases link and the 14 recorded functions)
169 checks, OK 164, PROBLEM 0, MISSING 0, WARN 0, INFO 5. Rule tests: passed 40, failed 0.
- Purchases link applied (check: true | 0). Purchases now go with the member on every delete route.
- The 14 dashboard-only functions: 0 left (all recorded).
- Only web_banned_devices is still not linked to web_users, on purpose.
- Only open item here: on 22 Nov check that the last saved list says 21 Nov.
