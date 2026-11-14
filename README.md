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
