# ViewGrow: current state (website, database, Android app)

Last updated: 19 Nov 2026 (member-facing wording: no "VIP", plain words). This file is private (the supabase/ folder is blocked by netlify.toml). **Update it with every change.**

## Rules for anyone (human or AI) changing anything
1. **Read this file, `schema.md` and `changelog.md` first.** Search them for the name of anything you are about to create. If it is already there, change it; do not write a second version.
2. **Never assume the database matches these notes.** Before writing any SQL or function, run the read-only check (`check_how_it_works.sql` QUERY 1, or the specific check) and use the live result. The live database is the truth.
3. One change = one migration file in `migrations/`, applied by the owner in the SQL Editor, then recorded in `changelog.md` (status: applied, date, how it was confirmed) and in `schema.md`.
4. A change made in the dashboard with no file must be written down here the same day. Example: `migrations/20261116150000_purchases_record_of_live.sql`.
5. The website is the source of truth for rules (limits, release time, bans). The Android app uses the same database and must say the same thing.
6. When something is replaced, delete the old version from the files and the notes. Do not keep drafts, "suggested" copies or "not yet applied" notes for things that are done or dropped.

## What is live (verified 16 Nov 2026)
| Area | State |
|---|---|
| Member site + admin | As in `readme.md`. Admin tabs: Today, Pool, Users, Settings. |
| Deleted-numbers list | Live. A deleted number cannot log in or sign up (ACCOUNT_DELETED). Admin removes a number from `deleted_numbers` to let it back. |
| In-app Delete my account | Live (`web_delete_account`, file `20261116180000`). Deletes the member's purchase records, saved contacts and account, and puts the number on the list. No approval step. |
| Admin Delete user | Does not add the number to the deleted list. Refuses banned members. Leaves `web_purchases` rows. |
| Release schedule | Installed and set to Daily (10pm Lagos). Nightly job runs every night. Confirmed 16 Nov 2026. |
| Pack purchases | Edge Function `verify-purchase` deployed and tested with a fake token (NOT_FOUND_AT_GOOGLE). `web_purchase_precheck` and `web_apply_purchase` live. A pack ADDS to the limit. A token Google already marked consumed is refused. **A real test purchase has not been done.** |
| Login tokens | Live (applied 17 Nov 2026, `20261117140000_login_tokens.sql`, both checks passed). Website v79+ and app v53+ log in with web_login and keep a token. Older apps and pages still work with the password. See changelog. |
| Legal and help pages | Split on 16 Nov 2026. Website pages `/privacy/`, `/terms/`, `/contact/`, `/delete-account/` cover the website only. App pages `/android/privacy/`, `/android/terms/`, `/android/contact/`, `/android/delete-account/` cover the app only: no menu, no link home, not in the sitemap, noindex. The app opens the `/android/` pages and its `Pages.kt` has the same words. All made by `tools/legal.py`. Deploy the website zip to Netlify, then give Google Play the `/android/` URLs. 17 Nov 2026 (website v82): both Privacy Policies say the phone or browser keeps a login code, not the password, and list login sessions; the app's says step 1 shows the contacts disclosure, the ban removes VG contacts, and there is a limit-full alert. |

## Android app (package com.viewgrow.app)
- Built with GitHub Actions (`.github/workflows/deploy.yml`): Java 17, Gradle 8.11.1, AGP 8.10.1, compileSdk and targetSdk 36, Play Billing 8.0.0. Needs secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD for the Play bundle.
- 47 Kotlin files in `app/src/main/java/com/viewgrow/app/`. No `res/xml` folder, no sync adapter, no battery request, no auto-start code (removed for Google Play).
- Talks to Supabase with the public (anon) key through database functions. Purchases go to the Edge Function (`Api.kt` sends `web_verify_purchase` to `/functions/v1/verify-purchase`).
- Firebase project `viewgrow-site` (Cloud Messaging only, no Analytics). `app/google-services.json` is in the app repo. Phones join the topic `members` while logged in; the Edge Function `notify-status` sends the "Today's status is up" alert.
- The app's own notes are its `README.md`. Keep both in step.

## Admin app
- ViewGrow Admin (com.viewgrow.admin) lives in the app repo's `admin/` folder and is built by `.github/workflows/admin.yml`. Not on Google Play. Uses only existing database functions (web_admin_stats, web_admin_new_members, web_admin_new_members_done, web_admin_log_verified, web_admin_log_verified_day). The admin page shows its extras only inside the app (window.VGAdminApp).

## Plain-words guide
`HOW_EVERYTHING_WORKS.md` explains the whole scheme (members, app, website, admin, rules) without technical words. Update it when a feature or rule changes. `DATABASE_EXPLAINED.md` is the same for the database side (tables, links, rules, functions, flows).

## Checking the live site and app
Run `check_live_matches_claims.sql` (read only, 8 queries; run each one in its own tab). All 8 passed on 16 Nov 2026. It tests that every function the app and site call exists, the 10pm clock and nightly job, the release schedule, the deleted-number block, row-level security and leftovers.

## Open items
00000000. 19 Nov 2026: VIP contacts (admin Settings screen + app saves them on members' phones). Left: run the check and migrations/20261119160000_vip_contacts.sql, deploy the website zip, build the app, then test: add a VIP on the admin page, open the app on a test phone with Contacts allowed, check the contact appears with the exact name. See changelog.
0000000. 17 Nov 2026: website v62 adds the bottom bar, History (green / amber / red days), the opened day with chat buttons and Profile. Migration 20261117120000 applied and confirmed (web_day_contacts, web_people). Left: deploy the website zip, then test: History, tap a day, chat button, Profile links, dark mode button on Profile, back button.
000000. 17 Nov 2026: website v61 restyles the member dashboard like the app Home (see changelog). Left: deploy the website zip and test log in, Import contacts, Increase my contact limit, chat button and dark mode.
00000. 17 Nov 2026: website v60 adds saved messages and the app / admin-chat link icons to Message all members. 20261117100000 applied and confirmed; notify-status redeployed (open "admin"). Left: deploy the website zip, then test: Save, Saved list (tap a row to use it), trash, app icon, chat icon.
000. 16 Nov 2026 (night): admin page redesigned (Today numbers first + "Log +", Lists, Users with count/filter on the right, member pop-up with facts and chat button, Days reshared / Downloads design B, Settings menu, one chat icon everywhere). Applied 20261116250000 (Signed up / Got file lists) and 20261116260000 (saved list time + app). Admin app: loading cover, smaller APK (Play services text reader), text size fixed, gradient buttons. Workflow: one deploy.yml that unpacks incoming/ and builds only the changed app (member AAB + APK, admin APK) into one release. Still to do: a "Change admin password" screen (web_admin_set_key exists; check its arguments first), then change the admin key.
0000. 16 Nov 2026: 20261116240000 applied, notify-status v2 deployed, job viewgrow-wake created (id 4). CRON_SECRET added. Test live with the new app: Notify members, Write your own message, Remind this group, and the 10:05pm wake-up.
1. Confirm the Android build is green on GitHub.
2. Deploy the website zip to Netlify.
3. Play Console: create products contacts_1000, contacts_3000, contacts_5000; upload the app file; forms (see the app's `docs/PLAY_STORE_CHECKLIST.md`). Privacy URL `https://viewgrow.site/android/privacy/`, delete URL `https://viewgrow.site/android/delete-account/`. When the app is live, put its Google Play link in `PLAY_URL` in `config.js` to show the "Get the app" card on the home page.
4. Real purchase test with a Play test account (a row should appear in `web_purchases` and the limit should rise by the pack size).
5. Owner decisions: (a) should admin Delete user also add the number to `deleted_numbers`? (Today it does not, and the website's `/delete-account/` page says a website member can sign up again after the admin deletes them. If this changes, change that page in `tools/legal.py`.) (b) should admin Delete user also delete `web_purchases` rows? (c) should a member's own delete keep the payment records (for refund disputes and accounting)?
6. Test a deleted number live: delete a test account in the app, then run QUERY 4 in `check_live_matches_claims.sql` (the deleted-number line should say ACCOUNT_DELETED).
