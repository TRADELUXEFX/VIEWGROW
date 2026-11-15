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
2. Done: Home (contacts card with gauge, countdown row, all states, sync to phone tagged VGC, background timer, "contacts saved" message).
3. Done: History (20 per page), Profile (sync every 1h/6h/12h/24h, pause, Delete My Contacts), Terms and Privacy pages.
4. Done: History redesigned like VG Reshare: day cards with an arrow, tap a day to see names and numbers with a WhatsApp chat button. The phone keeps its own record of each sync (HistoryLog), because the database only sends counts per day.
5. Done: History has a Days | People toggle in the green header. People lists everyone this phone saved (from HistoryLog), once each, newest first, with the same chat button as a day.
6. Done: Profile, Sync. Add new contacts every has no title (the SYNC label says it), only the tip line; the chosen time is filled green, and the card is dimmed while syncing is paused.
7. Done: removed the "One last thing" name card from Home. Every account has a name, and Sign up already requires one.
8. Done: Delete My Contacts pop-up has its own dialog theme (no grey Android box). Battery pop-up entries added to the manifest, with a fallback to the battery list.
9. Done: the Duration filter was removed from History. (A `?` help pop-up, `HelpPopup.kt`, was built for History and Profile but never wired to a button, so it was deleted in the clean-up below.)
10. Done, ideas taken from VG Contact: a reminder when nothing has synced for 3 days, or when Contacts permission is off (Reminder.kt); the Sign up and Log in header shrinks while the keyboard is open (KeyboardAware.kt); names and numbers shrink instead of being cut on big-font phones (TextFit.kt).
11. Done, idea taken from VG Contact: full-screen Banned and Blocked pages (`StopFragment.kt`, `Ban.kt`).
    - **Banned** (red): the database answers `BANNED` to log in, Home, History or a sync. The phone remembers it (`Store.banned`), so the page shows at once, also offline. It shows the account number (in the header), the reason, what it means, and a Message admin on WhatsApp button to appeal. Every ViewGrow contact the app saved is removed from the phone (the same ones Delete My Contacts removes), the phone-only HistoryLog is cleared, and the timer, sync adapter and reminder stop. Back leaves the app. Each time the page comes into view it asks the database again; if the ban is lifted the flag clears and the member goes back into the app. If the database sends a `reason` with `BANNED`, it is shown (`Ban.reasonText`); without one it says "Breaking the app's rules".
    - **Blocked** (amber): the database answers `LOCKED` (password locked after too many wrong tries). It shows the number (in the header), what happened, how to get a new password, Message admin on WhatsApp, and Back to log in. Nothing is deleted. Back goes to Welcome.
    - **Needs database migration 20261115120000** (in the website repo, `supabase/migrations/`). It makes `_web_auth` answer `BANNED` (with the admin's reason, and only when the password is correct) and `LOCKED` (to anyone who tries a locked account). Until it is run in the SQL Editor, these screens never show and no contacts are removed. The app only obeys what the database says; only `ok: true` lifts a ban.
12. Done: a permission turned off is caught when the app opens or comes back from Settings (`PermissionWatch.kt`). Android closes an app the moment a permission is switched off, so there is no way to hear it live, and there is no background timer for this. Home shows a red box naming what is off (Contacts, Notifications, Battery) with a Fix now button that asks for it or opens the phone's settings (Profile no longer has permission switches). An automatic sync that finds Contacts or Battery off also sends one notification (if Notifications is off nothing can be shown, so only the Home box tells them). No database change.
13. Done: a pop-up inside the app when a permission is off (`dialog_permission.xml`, `MainActivity.maybeShowPermissionPopup`). Shown when the app opens or returns from Settings, on Home and History only, once per open. It names what is off (Contacts, Notifications, Battery) and why it matters; Fix now asks for it or opens the phone's settings, Later closes it. The red box on Home stays as well.
14. Done: a notification that stays in the notification bar (`ReshareReminder.kt`, an "ongoing" notification, silent). It says "Reshare the admin's status"; tapping it opens a WhatsApp chat with the admin, with a message filled in, so the member can get the status and reshare it. It is put back each time the app opens or an automatic sync runs (Android 14 and up lets people swipe it away), and removed on log out or ban. The admin's contact is saved on the phone (`Store.adminTarget`) so the tap works with the app closed. It needs Notifications allowed. No database change.
15. Done: Profile now has a SUPPORT section (Help and FAQ, Message admin) above Terms and Privacy. Help and FAQ (`FaqFragment.kt`) has eleven questions in plain words, with a short help line at the top. It covers the contact limit, the packs, that a pack does not guarantee contacts, what to do if charged but the limit did not go up, and refunds (handled by Google Play). It is listed on Google Play, so payments are described only as Google Play does them, one open at a time, and a Message admin on WhatsApp button at the bottom. Message admin opens the admin's WhatsApp (`Remote.openAdmin`).
16. Done: a red bar on Home ("Almost at your limit") appears whenever the member has used 90% or more of their contact limit (`HomeViews.nearLimitCard`). It is based on the total and limit the database sends, so an upgrade hides it on its own. At 100% the "Contact limit reached" card still shows instead. No database change.


17. Done (screens only, no charging yet): an "Increase my limit" button on Home, under the contacts card, always there for verified members. Tapping it opens a pop-up (`UpgradeSheet.kt`, `sheet_upgrade.xml`) with three packs (+1,000, +3,000, +5,000 contacts). It explains what is being bought (room in the contact limit, paid through Google Play in naira, and that the admin still decides which contacts are released) and shows each pack's new limit, a "Buy with Google Play" button with the Google Play icon, and "Not now". Prices show "₦ price" until set. The buy button only says "Purchases are not switched on yet." Still to do before it charges: Google Play billing (adds the billing library and product IDs), the naira prices in the Play Console, and a server check of each purchase before the limit goes up. The Google Play icon in `ic_google_play.xml` is the open-source Bootstrap Icons Google Play glyph, not Google's own logo file. Check Google's brand rules before using their official artwork.

18. Done: Help and Increase my limit redone (screens only, no charging yet). See `app-proposal-help-and-upgrade.png`.
    - **FAQ** (`FaqFragment.kt`, `fragment_faq.xml`): see item 19. Fifteen questions sit under three headings: Getting your contacts, Your limit and payments, Your phone and privacy. Answers are short; how-to answers are numbered steps. "How do I increase my limit?" has an Open Increase my limit button, and "I paid but my limit didn't go up" has a Message admin with my order button (WhatsApp message ready for the order number). The open question gets the green edge.
    - **Increase my limit** (`UpgradeSheet.kt`, `sheet_upgrade.xml`, `item_upgrade_pack.xml`): a used bar (red at 100%), "Choose a pack" with three tappable packs showing the new limit and the price (the middle pack starts picked), three short facts, one Buy button that names the pack (Buy +3,000 and the price), Not now, and a Questions about paying? link that opens the FAQ page. The facts say the payment is one-time and not a subscription: **check the packs really are one-time purchases in the Play Console before shipping this line.**
    - **Home**: the limit reached card now has one Increase my limit button that opens the same Google Play pop-up (it used to open WhatsApp to pay the admin), plus a "Paid but nothing changed? Get help" link to the FAQ page. The red "Almost at your limit" bar button also opens the pop-up and says Increase my limit. The outline Increase my limit button is hidden while the red bar or the limit reached card is showing, so there is never a double button.
    - `Store.lastTotal` and `Store.lastLimit` remember the numbers Home last showed, so the FAQ can open the pop-up with the right limit.
    - New drawables: `bg_progress_red`, `bg_radio_off`, `ic_check_white`.
    - Still to do before it charges, as in item 17: Google Play billing, naira prices in the Play Console, and a server check of each purchase.

19. Done: Help is now a floating chat button, and the FAQ page only has questions.
    - **Chat button** (`fab_chat` in `activity_main.xml`, `bg_fab.xml`): a round green button with a chat icon, floating on the right above the bottom bar on Home, History and Profile only (`MainActivity` shows and hides it as the screens change; it is hidden on the FAQ, Terms, Privacy, day pages and the sign-in screens). Tapping it opens the admin on WhatsApp with "Hi, I need help with ViewGrow. My name is ...".
    - The Profile row "Help and FAQ" is now **Frequently asked questions**. The page is titled FAQ ("Frequently asked questions" under it), and the Contact us button and the "Still stuck?" text are gone from it. Profile still has a Message admin row.


19. Done: Google Play billing in the app (`BillingManager.kt`). The prices on the "Increase my contact limit" pop-up now come from Google Play, in the member's own currency (they show "..." while loading and a dash, with Buy switched off, if Google Play cannot be reached). The +3,000 pack carries a "Recommended" badge and starts picked. Buy opens Google Play's own payment sheet; after paying, the app sends the purchase to the database function `web_verify_purchase`, and only an ok answer consumes the purchase and refreshes Home. A paid purchase that could not be confirmed is tried again whenever the app opens. Still to do outside the app, see `incoming/PLAY_BILLING_SETUP.md`: the three products and naira prices in the Play Console, and the `web_verify_purchase` database function that checks each purchase with Google.

20. Done: History shows a third kind of day, amber: the member reshared but nobody joined (+0, "You reshared \u00B7 No one joined"). Not tappable. The preview image is now `viewgrow-app-preview-v12.png`: no Duration chip or pop-up, Profile without permission switches.

21. Done: clean-up. Removed code and resources nothing used (also five unused layout ids): `HelpPopup.kt` with `dialog_help.xml` and `item_help_row.xml`, `part_name_card.xml` (the old name card), eight leftover drawables from the removed Duration calendar and old headers, seven unused colours (light and night), the `DlgWeekday` style, `Release.whenText`, and unused imports. The billing helper no longer keeps the screen alive after it closes (`BillingManager.stopListening`), and log out forgets the contacts numbers Home last showed.

22. Done: text size. The 1.3x cap on the phone's Font size is lifted to 2.0x (the most Android allows), so every phone's own setting is respected. To make that safe, buttons in pop-ups, the History toggle, and the Home and FAQ buttons now grow with their text (minimum height instead of a fixed height). Screens already scroll, and names and numbers shrink to fit. Test once with the phone's Font size on the largest setting. Display size is still capped at the phone's default (`MAX_DISPLAY_DENSITY_RATIO` in `ScaleGuard.kt`).

## Working rules for whoever edits this repo
- **Always send the FULL updated zip** (every file of the app, paths starting at the repo root), never only the changed files. A partial zip leaves stale files in the repo and breaks the build (this happened with `dialog_duration.xml`).
- When a file is removed from the app, list it in `DELETE_FILES.txt` at the zip root too, because uploading a zip never deletes anything by itself.
- Update the preview PNG whenever screens change.
