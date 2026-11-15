# ViewGrow (Android)

Android version of the ViewGrow website. Native Kotlin, package `com.viewgrow.app`.

It signs up and logs in with the same WhatsApp number and password as the website, and talks to the same
database (the website's Supabase project, see `app/src/main/java/com/viewgrow/app/Config.kt`).
The app never decides who gets which contacts. The database does, with the same rules as the website.

## Folders
- `app/` the Android app.
- `incoming/` drop a zip here to deploy. See `incoming/DEPLOY_WORKFLOW.md`.

## Build
GitHub Actions builds the APK on every push to `main`; it appears on the Releases page.
`versionCode` goes up by one on every build (it is `1 + the run number`), so each APK installs over the last one.
Settings, Profile shows the app version.

## Progress
1. Done: project base, Welcome, Sign up, Log in, bottom bar, light and dark mode.
2. Done: Home (countdown, all states, sync to phone tagged VGC, background timer, "contacts saved" message).
3. Done: History (20 per page), Profile (sync every 1h/6h/12h/24h, pause, permissions, battery, Delete My Contacts), Terms and Privacy pages.
4. Done: History redesigned like VG Reshare: day cards with an arrow, Duration chip in the green header, tap a day to see names and numbers with a WhatsApp chat button. The phone keeps its own record of each sync (HistoryLog), because the database only sends counts per day.
5. Done: History has a Days | People toggle in the green header. People lists everyone this phone saved (from HistoryLog), once each, newest first, with the same chat button as a day. The Duration chip limits People too.
6. Done: Profile, Sync. Add new contacts every has no title (the SYNC label says it), only the tip line; the chosen time is filled green, and the card is dimmed while syncing is paused.
7. Done: removed the "One last thing" name card from Home. Every account has a name, and Sign up already requires one.
8. Done: Delete My Contacts pop-up has its own dialog theme (no grey Android box). Battery pop-up entries added to the manifest, with a fallback to the battery list.
9. Done, ideas taken from VG Reshare: the ? help pop-up on History and Profile (HelpPopup.kt), and a calendar for Duration > Pick dates, single day or range (DateRangeSheet.kt).
10. Done, ideas taken from VG Contact: a reminder when nothing has synced for 3 days, or when Contacts permission is off (Reminder.kt); the Sign up and Log in header shrinks while the keyboard is open (KeyboardAware.kt); names and numbers shrink instead of being cut on big-font phones (TextFit.kt).
12. Done: Home loading bar (slides across the top until the database answers) and the new Contacts received card: label and number on the left, half-circle gauge on the right (`GaugeView.kt`); the gauge turns red at the limit. Contacts received is now the main card at the top; the countdown is a small row under it.
11. Done, idea taken from VG Contact: full-screen Banned and Blocked pages (`StopFragment.kt`, `Ban.kt`).
    - **Banned** (red): the database answers `BANNED` to log in, Home, History or a sync. The phone remembers it (`Store.banned`), so the page shows at once, also offline. It shows the account number (in the header), the reason, what it means, and a Message admin on WhatsApp button to appeal. Every ViewGrow contact the app saved is removed from the phone (the same ones Delete My Contacts removes), the phone-only HistoryLog is cleared, and the timer, sync adapter and reminder stop. Back leaves the app. Each time the page comes into view it asks the database again; if the ban is lifted the flag clears and the member goes back into the app. If the database sends a `reason` with `BANNED`, it is shown (`Ban.reasonText`); without one it says "Breaking the app's rules".
    - **Blocked** (amber): the database answers `LOCKED` (password locked after too many wrong tries). It shows the number (in the header), what happened, how to get a new password, Message admin on WhatsApp, and Back to log in. Nothing is deleted. Back goes to Welcome.
    - **Needs database migration 20261115120000** (in the website repo, `supabase/migrations/`). It makes `_web_auth` answer `BANNED` (with the admin's reason, and only when the password is correct) and `LOCKED` (to anyone who tries a locked account). Until it is run in the SQL Editor, these screens never show and no contacts are removed. The app only obeys what the database says; only `ok: true` lifts a ban.
