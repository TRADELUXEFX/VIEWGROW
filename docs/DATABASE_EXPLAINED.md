# ViewGrow database, explained in plain words

Last updated 16 Nov 2026. This is only the database side (Supabase). Everything here was checked against the live database on 16 Nov 2026, except where it says "not tested".

## The picture in one minute

The database is a set of **tables** (the filing cabinets) and **functions** (the staff who are allowed to open the cabinets). Visitors, the website and the app can never open a cabinet themselves. They can only ask a function to do something, and the function checks the rules first. All the rules live in the functions and in the table settings, not in the website or the app.

## The tables (16 filing cabinets)

**Members and their activity**

| Table | What it holds |
|---|---|
| web_users | One row per member: name, phone number (unique), scrambled password, wrong-try count, locked flag, banned flag and reason, consent time, personal limit (limit_override), and capped_day (the day the limit was reached). |
| web_verifications | One row per member per day they were verified by the admin. A member can only have one row per day. |
| web_received | One row per person a member has been given: member, phone, name, day. A member can only be given the same phone once. |
| web_downloads | Every download: member, kind (daily or full), the day, status (pending, done or abandoned), the list picked, the contact file text, and when confirmed. |
| web_redownload_grants | The admin's permission for a full re-download. One per member per month. |
| web_purchases | One row per verified Google Play pack: member, product, pack size, purchase token (unique), order number, limit before and after. |
| deleted_numbers | Numbers of deleted accounts. A number here cannot log in or sign up. |

**Daily lists and releases**

| Table | What it holds |
|---|---|
| web_snapshot_contacts | The frozen copy of verified people (name and phone) for each day. This is what members' downloads are picked from. |
| web_snapshot_meta | A summary per day: sign-ups and contact count. |
| web_day_releases | Which saved days have been released, and their batch. Days released together share a batch. |
| web_release_config | One row only: the release schedule (daily, weekly with a weekday, or monthly with a day). Now: daily. |
| web_release_backup | A saved copy of three functions from before the release schedule was added, so it can be rolled back. |

**Settings and protection**

| Table | What it holds |
|---|---|
| web_settings | Sign-up open or closed, sign-ups per address (3), default limit (500), the admin's contact options, message templates, and the admin key (scrambled). |
| web_signup_log | The internet address and time of each sign-up, used for the 3-a-day limit. |
| web_vip_contacts | The admin's VIP contacts (a name and a number each) that the app saves on members' phones. Nobody can read or write it directly; only the VIP functions can. |
| web_admin_attempts | Wrong admin-key tries per connection and any temporary lock. |

## How the tables connect

A member (web_users) is the centre. When a member row is deleted, these are removed automatically: their downloads, received contacts, re-download permissions and verifications. Two tables are **not** linked, so the delete-account function removes them by hand: web_purchases and web_snapshot_contacts. That is why the admin's Delete user button leaves purchase rows behind: it does not remove them.

## The rules the tables enforce by themselves

These cannot be broken even by a mistake in the code:

- One verification per member per day.
- A person is given to a member only once.
- One finished daily download per member per day (a second one is refused with ALREADY_TODAY).
- One full re-download permission per member per month.
- Phone numbers are unique, and member names are unique.
- A purchase token can only be recorded once.
- Download kind and status, and the release settings, can only hold allowed values.

## The functions, in groups

- **Public (anyone may call; they check the password inside):** web_vip_numbers (the VIP list for the app), web_register, web_status, web_boot, web_history, web_set_name, web_prepare_download, web_confirm_download, web_delete_account, web_public_config, web_next_release, web_app_seen (live 16 Nov 2026). Also web_last_release (live 16 Nov 2026) (for the app's morning message: last night's release, how many people, and whether you were in it; reads only).
- **Admin (anyone may call, but each one checks the admin key first):** VIP contacts (web_admin_vip_list, _save, _delete), stats, pool, users, new members file (web_admin_new_members and _done, live 16 Nov 2026), ban, reset password, set limits, log verified (today or a chosen day), un-log, grant re-download, delete user, settings, backup, release get, set and now, save list now.
- **Internal (blocked for the public, used by other functions):** the password check (_web_auth), the day clock (_web_today), the limit and received counters, picking contacts (_web_candidates, _web_pick), the release helpers, and the nightly jobs (web_nightly_snapshot, web_take_snapshot). Also web_status_core and web_register_core, web_purchase_precheck and web_apply_purchase.

## How things happen (step by step in the database)

**Sign up.** web_register first checks the deleted list, then the original registration function. It checks sign-up is open and the 3-a-day limit for that connection, validates the number and password, scrambles the password, and creates the web_users row.

**Log in.** web_status checks the deleted list, then the password. Five wrong tries lock the member until the admin resets it. A banned member gets BANNED. A good login returns the member's state (not verified, ready, limit reached) and counts. web_boot does this plus the public settings in one call.

**Verify.** The admin logs a reshare. The database adds a web_verifications row only if the member is not banned, the day is not in the future, not before they registered, after their capped day, and the member is still under their limit. If that day's list is already saved, the member is added to it too.

**The day.** The database's own clock is Lagos time plus 2 hours, so a "day" runs from 10pm to 10pm.

**The nightly job.** At 10pm Lagos (21:00 UTC) the job viewgrow-snapshot saves the day that just ended into web_snapshot_contacts and web_snapshot_meta, releases saved days into web_day_releases when the schedule says so (now: every night), and clears finished file text. It succeeded on the last 7 nights.

**Download.** web_prepare_download works out how many contacts the member may still get (limit minus received), picks that many people from the released lists (nobody they already have), builds the file, and stores it as a pending download. If the member asks again the same day it hands out the same file. Nothing is recorded as received yet. web_confirm_download then marks it done, writes the web_received rows, and sets capped_day if the limit is now reached. A second confirmed daily download the same day is blocked by the table rule.

**What a contact in the file looks like.** Each member contact has the person's name ending " VG", the company VG, and the number. There is no note and no marker contact. The admin's VIP contacts come first, with the exact name and no company. The same card is used by web_app_restore (the app's restore). Contacts saved on phones before 19 Nov 2026 still carry the old "ViewGrow-<id>" note; the app still recognises it.

**The limit.** The default is 500 (web_settings). A personal limit (limit_override) replaces it. A paid pack raises limit_override by the pack size, up to 100,000.

**Buying a pack.** The app sends the purchase to the Edge Function verify-purchase, not to the database directly. It checks the member (web_purchase_precheck), asks Google whether the payment is real and not already used up, then web_apply_purchase writes the web_purchases row and raises the limit. The two purchase functions are blocked for the public and only the Edge Function can call them.

**Deleting.** web_delete_account (the member, in the app): checks the password, adds the number to deleted_numbers, deletes the member's purchase rows and saved contact entries, then the member row (the rest follow automatically). web_admin_delete_user (the admin) is different: it does not add the number to the deleted list, refuses banned members, and leaves purchase rows.

## Security, in database terms

- Row-level security is on for all 15 tables and there are no access rules, so nobody can read or write a table directly. Checked 16 Nov 2026.
- The public key can run only the functions the site and app need. Internal functions and the two purchase functions are blocked. Checked 16 Nov 2026.
- Functions that handle data run with the database's own rights ("security definer") and a fixed search path, so they cannot be tricked into using other tables.
- Passwords and the admin key are scrambled (bcrypt). The admin backup does not include passwords.
- Login and sign-up answers do not reveal whether a number is registered.

## Loose ends (known, not harmful)

- Five tables still carry table permissions for the public key (deleted_numbers, web_day_releases, web_signup_log, web_snapshot_contacts, web_snapshot_meta). Row-level security blocks them. Removing the permissions would be a tidy-up, not a fix.
- Old settings rows no longer used: contact_label, contact_value, contact_channel, web_004_applied, admin_fail_count, admin_locked_until. The function web_admin_contact_set, and the helpers _web_contacts and _web_days, are also unused. They are kept because removing counts as deleting.
- web_admin_unlog_verified is not used by any screen, and its "already downloaded" guard no longer works.
- web_purchases has no link to web_users, so purchase rows can outlive a member after an admin delete.
- The unique name index (web_users_name_key_uniq) exists in the database but no migration file creates it.
- Not tested live: a deleted number being blocked, and a real purchase.
