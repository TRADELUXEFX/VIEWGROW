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
