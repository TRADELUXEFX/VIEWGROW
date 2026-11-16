# ViewGrow (Android)

Android version of the ViewGrow website. Native Kotlin, package `com.viewgrow.app`.
It signs up and logs in with the same WhatsApp number and password as the website, and uses the same Supabase database (`Config.kt`). The app never decides who gets which contacts: the database does, with the same rules as the website. Plain-words guide to everything (app, website, database): `docs/HOW_EVERYTHING_WORKS.md`; the database side: `docs/DATABASE_EXPLAINED.md`. The live state of the whole project is in `docs/STATE_FROM_WEBSITE_REPO.md` (a copy of the website repo's `supabase/STATE.md`).

## Two apps in this repository
- `app/` is **ViewGrow**, the member app (com.viewgrow.app), published on Google Play. Built by `.github/workflows/deploy.yml` ("Build member app").
- `admin/` is **ViewGrow Admin** (com.viewgrow.admin, shown as "VG Admin"), the admin's own app. Never sent to Google Play; install the APK from the "Build admin app" run (artifact `viewgrow-admin`). Built by `.github/workflows/admin.yml`.
- Each workflow runs only when its own folder (or a shared build file: build.gradle, settings.gradle, gradle.properties) changes, or with "Run workflow". Both use the same upload key secrets.
- VG Reshare (the reshare tracker for WhatsApp TVs) is a separate product and a separate repository. Nothing here depends on it; the screenshot row reader in `admin/` was copied from it.

### ViewGrow Admin (admin/)
- **Sign in** with the admin key once (kept encrypted with the Android Keystore, `SecretBox.kt`).
- **Setup**: Contacts (save members, look up screenshot names) and Notifications.
- **The admin website inside the app** (`MainActivity.kt`, WebView of https://viewgrow.site/admin). It is the real website, so app and website always match. The page gets a bridge, `VGAdminApp`: key(), signedOut(msg), syncText(), openSync(), openScreenshots(), saveFile(name, mime, base64). It answers only pages from viewgrow.site. The website shows its app extras (sync line on Today, the "Log from screenshots" button) only when the bridge is there.
- **Contacts sync** (`Members.kt`): every 15 minutes when online, on opening the app, and with Check now. Uses `web_admin_new_members` / `web_admin_new_members_done` (the same bookmark as the website's New members button). The first run and once a day it fetches every member, to keep the member list on the phone (`MemberList`, number key -> "Name VG"). New members are saved as "Name VG", company VG; numbers already in the phone are left as they are.
- **Log from screenshots** (`ShotsActivity.kt`): photo picker, ML Kit text recognition on the phone (bundled model), the VG Reshare row reader (rows with "Reshared"), then matching: number on the screenshot -> member list; remembered choice; name -> number in the phone contacts -> member list (so "Team Limitless" works); otherwise member names, allowing small reading mistakes (unsure ones are left for the admin to choose). Logs with `web_admin_log_verified` (today) or `web_admin_log_verified_day` (another day).
- **Sync screen** (`SyncActivity.kt`): sync on/off, notes on/off, numbers, recently saved, Check now, Sign out of this phone.
- No new database functions are needed.

## Build
- GitHub Actions (`.github/workflows/deploy.yml`) builds the APK and the Play bundle on every push to `main`: Java 17, Gradle 8.11.1, AGP 8.10.1, compileSdk and targetSdk 36.
- `versionCode` is `1 + the run number`, so each build installs over the last. Profile shows the app version.
- The Play bundle needs the upload key (secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD). Without them the build makes a test APK only.

## What the app does
- **Welcome, Sign up, Log in.** Light and dark mode. Sign-in screens shrink while the keyboard is open. Text size follows the phone's Font size up to 2.0x.
- **Home.** Contacts used and limit (gauge), countdown to the next release, and every state (not verified, limit reached, almost at limit at 90%). Saves new contacts to the phone tagged VG.
- **Sync.** Background timer (WorkManager) every 1h, 6h, 12h or 24h, can be paused. Opening the app always syncs. Android may delay background syncs on some phones.
- **History.** Days and People views. The phone keeps its own record (`HistoryLog`) because the database only sends counts per day. Each person has a WhatsApp chat button.
- **Increase my contact limit.** Three packs (+1,000, +3,000, +5,000) bought through Google Play Billing (`BillingManager.kt`). Prices come from Google Play. A purchase is checked by the Edge Function `verify-purchase` before the limit goes up, and only then consumed. The pop-up always shows the opposite of the app's light or dark mode.
- **App pages on the website.** Terms, Privacy, Contact us and Delete my account open the app's own pages at `viewgrow.site/android/...` (`Web.kt`). They are separate from the website's pages, are not in the website menu and have no menu, so members only see the app's rules. `Pages.kt` holds the same Terms and Privacy words, shown only if the phone has no browser.
- **Profile.** Sync settings, Delete My Contacts (removes only VG contacts), FAQ, Purchase history (kept on the phone), Terms, Privacy, Delete my account, Log out.
- **Delete my account** (`DeleteAccountFragment.kt`). Calls `web_delete_account`: the member's data is deleted and the number goes on the deleted list. VG contacts are removed from the phone, local records are cleared, and the member is logged out. No approval step.
- **Banned and Blocked pages** (`StopFragment.kt`, `Ban.kt`). Banned: the database answers BANNED, the phone remembers it, and the ViewGrow contacts it saved are removed. Blocked: password locked after too many wrong tries. The app only obeys what the database says.
- **Permissions.** A plain-words contacts disclosure (Agree / No thanks) shows before Android's Contacts box. Two permission steps: Contacts and Notifications. A turned-off permission is caught when the app opens, with a pop-up and a red box on Home.
- **Notifications.** Reminder after 3 days with no sync or with Contacts off; a notification that stays and opens the admin's WhatsApp chat to get the status; one alert at 80% of the limit; a weekly summary on Sunday at 6pm (nothing is sent in a week with no new contacts).
- **Nightly delivery and morning message** (`Nightly.kt`). A check at 10:10pm Lagos after every release saves new contacts the same night, whatever interval was chosen. Contacts saved between 9pm and 7am are not announced at night; at 8am the member gets "48 new status viewers added last night", or, if they were not on last night's list, "41 people joined last night" (tap opens the admin's WhatsApp). That second message needs the database function `web_last_release` (`supabase/migrations/20261116220000_last_release.sql`); until it is applied the app simply skips it.
- **"Today's status is up" alert** (`StatusPush.kt`, Firebase Cloud Messaging). Phones join the Firebase topic `members` while logged in and not banned (started and stopped with the Reshare reminder). The admin's Notify members button calls the Edge Function `notify-status` (website repo). The message only carries type = status; the phone writes the words. Tap opens the admin's WhatsApp chat. Needs `app/google-services.json` (Firebase project viewgrow-site).
- **Messages from the admin** (`StatusPush.kt`). Besides "status is up": the admin's own messages (topic `members`), group reminders (the member's own topic `u<id>`; the id comes from `web_app_seen`, which also tells the database the member uses the app, at most every 6 hours) and the silent 10pm wake-up (`WakeWorker` saves the night's contacts). A message's `open` decides the tap: the app, the admin's WhatsApp chat, or Increase my contact limit.
- **Update prompt** (`UpdateCheck.kt`, Play In-App Updates). When Google Play has a newer version, a pop-up offers it, at most once a day. Not shown on test APKs.
- **Admin's number saved** (`AdminContactSaver.kt`). Once Contacts is allowed, the admin's WhatsApp number is saved as "ViewGrow Admin", once per number, so the member can see the admin's status. Not removed by Delete My Contacts. The contacts disclosure and the app's Privacy Policy say so.
- **Chat button.** A floating button on Home, History and Profile opens the admin on WhatsApp.
- **Security.** The password is encrypted with the Android Keystore (`SecretBox.kt`). Internet only over HTTPS.

## Rules for whoever edits this repo
- Send the FULL updated zip (every file, paths from the repo root). Uploading a zip never deletes anything, so remove old files by hand.
- When something is replaced or dropped, delete the old file and its notes. Do not leave unused files.
- Keep this file and `docs/STATE_FROM_WEBSITE_REPO.md` in step with the website repo.
- Before Google Play: `docs/PLAY_STORE_CHECKLIST.md`.
