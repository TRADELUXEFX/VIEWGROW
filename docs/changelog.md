# Database changelog

Each change is one migration file. Newest first.

## Migration check (19 Nov 2026): which SQL is applied
- Order to run, oldest first: 20261118220000, 20261118230000, 20261119150000, 20261119160000, 20261119170000, 20261119180000, 20261119190000.
- All applied: 20261118220000, 20261118230000, 20261119150000, 20261119160000, 20261119170000, 20261119180000, 20261119190000. Nothing left to run (confirmed by the read-only check on 19 Nov 2026, all six columns true).
- Read-only check (changes nothing; all true means everything above is applied):
```sql
select
  to_regclass('public.web_vip_contacts') is not null as vip_table_160000,
  to_regprocedure('public.web_admin_vip_list(text)') is not null as vip_list_160000,
  position('+234000' in pg_get_functiondef('public.web_prepare_download(text,text)'::regprocedure)) = 0 as no_marker_150000,
  position('web_vip_contacts' in pg_get_functiondef('public.web_prepare_download(text,text)'::regprocedure)) > 0 as vip_in_file_170000,
  position('NOTE:ViewGrow-' in pg_get_functiondef('public.web_prepare_download(text,text)'::regprocedure)) = 0 as no_note_180000,
  position('exists (select 1 from public.web_users' in pg_get_functiondef('public.web_people(text,text,integer,integer)'::regprocedure)) > 0 as last_one_190000;
```
- Result on 19 Nov 2026: true, true, true, true, true, true.

## App update (19 Nov 2026): Last sync line, "Sync contacts" button, VG on People names, no database change
- Status: app files only (HomeFragment, HomeViews, Store, Sync, HistoryFragment, HistoryDayFragment). Needs a new app build; nothing to run in the database.
- Home: a "Last sync: just now" line (with a NEW chip) sits under the sync button. It counts up by itself (just now, 5 min ago, 2 hours ago, Today, Yesterday, date). Amber dot while checking, green when there is a time. Store.lastCheckAt is saved after any successful sync or restore.
- Home: the button now says "Sync contacts" (it said "Sync now").
- Home: auto-sync also runs for any state except NOT_VERIFIED. If contacts are READY it saves them and restores anything missing; if nothing is new it only puts back what is missing from the phone (for example after a reinstall).
- History > People and an opened day: names show with the VG tag ("Victor VG") like on the phone, using ContactsSaver.displayName (not added twice).
- Not changed: the 3/500 counter still counts members who were deleted (see 20261119190000).

## 20261119190000: people who are no longer members are hidden from History
- Status: APPLIED (confirmed by the read-only check on 19 Nov 2026: web_people and web_day_contacts both hide people who are not members). File: migrations/20261119190000_hide_deleted_in_history.sql. It replaces the earlier deleted-list version of this file. Needs the new app build to show the Banned tag.
- Why: when a member is deleted their number stays in other members' received lists (web_received holds copies, not links), so it kept showing in History > People (e.g. DREAL). The admin's Delete user button does not use deleted_numbers, so the rule is "a received person is shown only while a member with that number exists".
- web_people and web_day_contacts get that one condition. web_people's total counts only the listed people, so paging stays correct.
- Already correct (checked in the live function text, 19 Nov 2026): _web_candidates skips banned members, and both delete paths remove the person's web_snapshot_contacts, so nobody deleted or banned is handed out to new members.
- Not changed: the contact limit counter (e.g. 3/500) still counts them, web_received keeps the rows, web_history (the +count on each day card) is untouched, and banned members already received still show in People, now with a red "Banned" tag.
- Banned tag: web_people and web_day_contacts also return "banned" (true/false) for each person. The app (item_history_contact.xml, HistoryFragment, HistoryDayFragment) shows a red "Banned" tag beside the name. Older app versions ignore the extra field. Needs the new app build to show.

## 20261119150000: marker contact no longer added to contact files
- Status: APPLIED (confirmed by the read-only check on 19 Nov 2026: no +234000 marker in web_prepare_download). It had no earlier changelog entry.
- Changes ONLY web_prepare_download and web_app_restore: the member's own marker card ("ViewGrow <id>", number +234000...) is no longer added. Real contacts are unchanged. Markers already on phones stay until deleted by hand. App v60 hides the marker from History.

## 20261118230000: contact names in the file end with " VG"
- Status: APPLIED 18 Nov 2026 by the owner (per the file header). No earlier changelog entry.
- Changes ONLY web_prepare_download and web_app_restore: "Team Limitless" becomes "Team Limitless VG" in the FN and N lines, besides the VG company. web_received.name is unchanged (the stored name stays the person's own). 20261118220000 (web_app_restore, applied 18 Nov 2026) is described under App v60.

## 20261119180000: contacts carry only the VG company (no "ViewGrow-<id>" note)
- Status: APPLIED 19 Nov 2026 by the owner in the Supabase SQL Editor (migrations/20261119180000_remove_note_from_contacts.sql, run after 20261119170000). The new app build (VG company instead of the note) is still to release; until members update, an old app saves contacts from a file without the note as VIPs.
- web_prepare_download and web_app_restore no longer write the NOTE line on member contact cards. Names (ending " VG"), the ORG:VG company, numbers, VIP contacts and the limit are unchanged.
- App (ContactsSaver.kt): a contact is a member's when it has the VG company (an older file that still has the "ViewGrow-<id>" note also counts); no VG company = a VIP. The app no longer saves the note. Delete My Contacts and a ban find contacts by the VG company, and still find older ones by their note. "ViewGrow Admin" is never removed.
- Contacts already on phones keep their old note. Nothing is deleted from the database or from phones.
- Order matters: apply the migration and the new app together. An old app reading a file without the note would treat members as VIPs.

## 20261119170000: VIP contacts are in every contacts file from the website
- Status: APPLIED (confirmed by the read-only check on 19 Nov 2026: web_prepare_download mentions web_vip_contacts). Run order was AFTER 20261119160000_vip_contacts.sql and 20261119150000_remove_marker_contact.sql (it is built on that latest web_prepare_download: people keep their " VG" names, no marker contact). Run migrations/20261119170000_vip_in_contact_file.sql once in the SQL Editor (it stops without changing anything if something is not as expected). Rollback: rollback/vip_in_contact_file_rollback.sql.
- Every VIP (name exactly as typed, no VG tag, no ViewGrow note) is in EVERY VCF a member downloads on the website (daily files and full re-downloads), first in the file. Nothing is tracked, so a VIP added later is simply in the next file. VIPs never count toward the contact limit and are not in web_received. Only web_prepare_download changes; web_confirm_download is untouched.
- Decision (owner): duplicates are accepted. A member who imports two files may see a VIP more than once, because the phone's import does not check for existing contacts. (The app is different: it saves each VIP once per phone and skips numbers already there.)
- Wording: the website Privacy Policy (/privacy/, section The contact files you download) now says the file can include a few extra contacts the admin chose. The admin VIP screen says the app saves them and every contacts file includes them.

## 20261119160000: VIP contacts (admin Settings + app)
- Status: APPLIED (confirmed by the read-only check on 19 Nov 2026: web_vip_contacts table and web_admin_vip_list exist). Before running it needed the read-only check below with both columns true. Then run migrations/20261119160000_vip_contacts.sql once in the SQL Editor. Then deploy the website zip and build the app.
- Check (read only): `select to_regprocedure('public._web_admin_ok(text)') is not null as admin_ok, to_regprocedure('public._web_auth(text,text)') is not null as auth_ok, to_regclass('public.web_vip_contacts') is null as table_is_new;` (table_is_new false only means the file was already run, which is safe.)
- New table web_vip_contacts (id, name, phone unique, created_at), row level security on, nobody can read or write it directly. New functions: web_admin_vip_list(p_key), web_admin_vip_save(p_key, p_name, p_phone, p_id default null) (adds or changes; errors BAD_NAME, BAD_PHONE, DUPLICATE, NOT_FOUND), web_admin_vip_delete(p_key, p_id), web_vip_numbers(p_phone, p_pin) (a logged-in member gets {ok, contacts:[{name, phone}]}). No existing function or table is changed. Phones: 0803 456 7890 becomes 2348034567890; another country code is kept; 10 to 15 digits.
- Admin page (admin.html, admin.js, style.css): Settings has a new row "VIP contacts" (star icon, "3 numbers · saved to new members"). It opens its own full screen: back circle, centred title "VIP contacts", a + circle on the right. Rows show a person icon, the name, the number and a chat button (opens WhatsApp to check the number). The + opens the Add VIP pop-up (Name, Phone number, Save). Tapping a row opens the same pop-up to change it, with Remove.
- Member app (VipContactSaver.kt, AdminContactSaver.kt, ContactsSaver.kt, Store.kt): when Contacts is allowed, and at every Home open and background sync, the app asks web_vip_numbers (at most every 15 minutes) and saves every VIP with the exact name the admin typed (no " VG", no company tag, no ViewGrow note, so Delete My Contacts and a ban leave them alone). Saved once per number (Store.vipSaved): a VIP the member deleted is not put back; a VIP added later reaches phones at their next check; removing one from the list does not remove it from phones. A number already in the phone under any name is skipped.
- Wording (members never see the word "VIP", only plain words): the contacts disclosure (ContactsDisclosure.kt) says "a few extra contacts the admin chose", the delete-contacts pop-up (ProfileFragment.kt) says "the contacts the admin chose", and the app's Privacy Policy (tools/legal.py, regenerated: /android/privacy/ and Pages.kt) says "any extra contacts the admin has chosen, with the names the admin gave them". The website's own Privacy Policy (/privacy/) has no contacts section and was not changed. Policies dated 19 November 2026. Google Play: the Contacts permission is already declared, but check that the Data safety form says the app saves contacts to the phone. (VIP stays the name inside the admin page only.)

## App v60 (18 Nov 2026): Sync now button; marker contact hidden from History, no database change
- Home: new "Sync now" button (refresh icon and words side by side) above "Increase my contact limit". One tap asks the database again and saves the contacts at once if they are ready, so members at 10pm do not wait for the timer. The icon turns while it works. If nothing is ready it says so. Saving when Home opens is unchanged. Files: HomeViews.kt (syncButton), HomeFragment.kt (syncNow, finishSyncNow, spin).
- History: the member's own marker contact ("ViewGrow <id>", number +234000...) no longer shows in People or on a day. Before, only the exact name "ViewGrow" was skipped. Now skipped when saving the record and when reading it, so cards already stored on phones disappear too. The contact is still saved to the phone (Delete My Contacts and a ban find the member's contacts through it). File: HistoryLog.kt (isMarker; add, entries, people, countSince).
- 10pm delivery: the 10pm wake-up (StatusPush.kt WakeWorker) and the 10:10pm check (Nightly.kt ReleaseCheckWorker) now try again (up to 4 times, every 3 minutes, linear backoff) when the release had not landed yet (state PREPARING) or the save failed. Before, they only retried when the database could not be reached, so a late release or a failed save waited for the next timer (up to 24h) or the 8am catch-up. Sync.kt (BackgroundSync.retryWanted).
- Sync now also puts back contacts the member already received but deleted from the phone, with no admin approval and no monthly limit (the app skips numbers already in the phone, so nothing is duplicated, and it adds nothing new). New database function web_app_restore (supabase/migrations/20261118220000_app_restore_received.sql, APPLIED 18 Nov 2026 by the owner in the SQL Editor; if it is missing, Sync now still works for new contacts and the restore step is skipped). The website's one-a-month full re-download is unchanged. App: Sync.kt (restore), ContactsSaver.kt (saveCounting), HomeFragment.kt (restoreDeleted), Api.kt.
- History: today's reshare no longer shows the amber "You reshared · No new members that day" card before the 10pm release (it was untrue, nothing had been released yet). It now shows a calm card with a clock: "Today · Contacts arrive at 10pm". After the release the day shows the real count or the real "No new members". If the list would be empty it says "Your first contacts arrive tonight at 10pm." Release.kt (dbToday), HistoryFragment.kt, new res/layout/item_history_pending.xml and res/drawable/ic_clock_green.xml.
- Increase my contact limit pop-up: prices show at once. Before, the app only asked Google Play when the pop-up opened, so the prices showed "…" for a while (or never). Now it asks when the app opens, keeps the last prices on the phone and shows them immediately, refreshes them from Google Play in the background (a failed refresh never wipes prices already showing), retries once after 2 seconds if Google Play does not answer, and loads the pack details just before paying if they are not there yet. Prices still come only from Google Play. BillingManager.kt (cachedPrices, prefetch, retry), UpgradeSheet.kt, MainActivity.kt.
- Not built or tested on a phone yet.

## App v59 (17 Nov 2026): website links in admin messages open; Step 1 made short, no database change
- Admin "Message everyone" with a link: tapping the notification now opens that link (https only). Before, the app ignored the link and opened itself. StatusPush.kt (open "link" with data "url"; linkTap). The admin page and notify-status already sent the link; no change there.
- Step 1 (Contacts): the SAVE / READ rows from v57 are replaced by two short lines in the card, the same size as Step 2 ("ViewGrow saves the contacts you receive, and the admin's number, to your phone. It only reads your contacts to skip duplicates and remove its own." and "Your contacts never leave your phone."). "Agree and allow" and "Not now" stay. res/layout/part_perm_use.xml deleted. ContactsDisclosure.kt (STEP, STEP_PRIVACY), PermissionsFragment.kt, fragment_permissions.xml.
- Step 2 (Notifications) now lists everything the app notifies about: "Get notified when new contacts are saved to your phone, when the admin's status is up, when the admin sends an announcement, and when your contact limit is almost full. You also get a weekly summary and reminders, like when to reshare or when a setting needs fixing."
- Privacy Policy (Pages.kt, made by the website's tools/legal.py, same words as viewgrow.site/android/privacy/ in website v82): the phone keeps a login code, not the password; login sessions are listed; step 1 shows the contacts disclosure; banned accounts have their VG contacts removed; the limit-full alert is listed.
- SecretBox.kt: the key helper is named loadKey (clearer next to KeyStore.load).
- Not built or tested on a phone yet.

## App v58 (17 Nov 2026): admin messages open the admin's chat with nothing typed, no database change
- A message sent from the admin page with the WhatsApp button on now just opens the chat with the admin. Before, it also typed "Hi, I am ready to reshare your status…" into the box. StatusPush.kt (adminTap takes reshare), Remote.kt (adminLink with empty text gives a plain wa.me link).
- The "Today's status is up" alert still opens the chat with the reshare message typed in, as before.
- Not built or tested on a phone yet.

## App v57 (17 Nov 2026): blank white screen after the setup steps fixed; contacts explained on Step 1, no database change
- Blank screen: the saved login is kept encrypted with the phone's Keystore, and the app decrypted it on the main thread every time it checked "logged in?" (dozens of times when Home opens). On some phones (Samsung) each Keystore call is slow, so the screen froze white for about 10 seconds between the steps and Home. Now the Keystore key is looked up once and the decrypted login is kept in memory until it changes. Files: SecretBox.kt, Store.kt. The login is still stored encrypted; nothing else changes.
- Step 1 (Contacts): what ViewGrow saves and reads with Contacts is written on the screen (SAVE and READ rows and a privacy line), so there is no pop-up before Android's own box. The button reads "Agree and allow"; "Not now" skips to Step 2. The card is more compact on this step. Files: PermissionsFragment.kt, res/layout/fragment_permissions.xml, new res/layout/part_perm_use.xml. ContactsDisclosure.kt keeps the words in one place (SAVE, READ, PRIVACY); its pop-up is still used when Contacts is turned on later from Home, with the same words.
- Not built or tested on a phone yet.

## App v56 (17 Nov 2026): "Rate this app" pop-up, no database change
- Member app: Google Play's own rating pop-up (Play In-App Review library, com.google.android.play:review 2.0.2) is asked for once per phone, 1.5 seconds after Home shows "Saved N contacts" for a save of 10 or more. Google decides whether it really shows and limits how often; test APKs never see it. New file Rating.kt. HomeFragment.kt (the call), Store.kt (review_asked, kept on log out), app/build.gradle (the library).
- Not built or tested on a phone yet. To see it before release, install from a Play internal testing track.

## App v53 (17 Nov 2026): login tokens (database migration 20261117140000 applied 17 Nov 2026)
- The password is sent once, at log in (and once after sign up), to web_login; the app keeps the login token it gets back (encrypted, in the same place the password was kept) and sends it on every call. New file Session.kt. LogInFragment.kt, SignUpFragment.kt, MainActivity.kt (start and onLoggedIn: a phone that still has a saved password trades it for a token once; log out ends the token on the server), Api.kt, HomeFragment.kt, HistoryFragment.kt (SESSION_EXPIRED: "You were logged out. Please log in again." and back to Log in), BillingManager.kt (logged out during a purchase: the purchase stays open and is checked after the next log in), Store.kt (notes).
- Safe before the migration: if web_login does not exist (HTTP 404) the app logs in the old way and keeps the password.
- The verify-purchase Edge Function needs no change: it passes the token to web_purchase_precheck like the password before.
- Not built or tested on a phone yet. Test: fresh log in, sign up, an install that was logged in before (opens without asking to log in), log out, admin resets the password (the app goes back to Log in), ban and unban, a test purchase.

## App v52 (17 Nov 2026): interval locked while syncing is paused, no database change
- Profile: when Pause syncing is on, the 1h / 6h / 12h / 24h buttons can't be tapped (they were dimmed but still worked). Turning Pause syncing off unlocks them at once. File: ProfileFragment.kt.
- Not built or tested on a phone yet.

## App v51 (17 Nov 2026): "+1,000 limit" wording, no database change
- Member app: packs read "+1,000 limit", "+3,000 limit", "+5,000 limit" (was "Limit +1,000"), on the limit pop-up and in Purchase history. The Google Play button reads "Get +3,000 limit · price". FAQ: "Pick +1,000, +3,000 or +5,000 limit." Files: UpgradeSheet.kt, PurchaseHistoryFragment.kt, FaqFragment.kt.
- Not built or tested on a phone yet.

## App v50 (17 Nov 2026): admin setup header like the member app, no database change
- Admin app: the two setup steps now have the member app's step header: a small "STEP 1 OF 2" above the title and two dashes underneath (the first filled on step 1, both on step 2). File: admin MainActivity.kt (stepHero).
- Not built or tested on a phone yet.

## App v49 (17 Nov 2026): admin setup pictures, no database change
- Admin app: the two setup steps (Save members, Stay notified) show the member app's full-colour pictures (contact card, bell) instead of the emoji. Files: admin MainActivity.kt (showSetup), admin res/drawable-nodpi/ob_contacts.png and ob_bell.png (copied from the member app).
- Not built or tested on a phone yet.

## App v47 (17 Nov 2026): "Not verified yet" box removed, no database change
- Home (not verified): the amber "Not verified yet" box at the top is gone; the "How to get new contacts" card already says what to do. The empty top_slot in fragment_home.xml is removed. Files: HomeFragment.kt, res/layout/fragment_home.xml.
- Contacts off is still handled only by the existing red "Contacts is off" box and pop-up for verified members.
- Not built or tested on a phone yet.

## App v46 (17 Nov 2026): clearer wording, no database change
- Member app: plainer words on Welcome, Sign up, the permission steps, Home, History, Profile, Delete my account, Banned and Blocked. New members see "How to get new contacts", and step 3 says new status viewers are added to the phone overnight (no more "next release"). Home messages no longer say "imported" (the app saves contacts by itself). "Get new contacts in" is now "Next contacts in". History: "You reshared · No new members that day".
- The pop-up when a member who is not verified taps History or Profile is now "Get verified first": one line on why, then the same 3 steps as Home, left-aligned (res/layout/dialog_verify.xml). Button: "Message the admin".
- Contact limit and payments (from v42 to v45): packs are named "Limit +1,000 / +3,000 / +5,000", the Buy button says "Raise limit", and the limit pop-up explains that paying only raises the limit and members get contacts by resharing. "One-time payment, not a subscription" is centred, without the tick (ic_check_green.xml removed).
- Admin app: "Save new members to this phone", "Tell me when members are saved", and clearer lines on the screenshot Check and Skipped tabs (SyncActivity.kt, ShotsActivity.kt).
- Unchanged on purpose: the contacts notice (ContactsDisclosure.kt), because Google Play checks that wording; the "Get new status viewers today" notification.
- Not built or tested on a phone yet.

## App v41 (17 Nov 2026): sign-up closed screen and moving banner pictures, no database change
- Member app only (Android). Sign-up closed: the whole form is hidden, as on the website. A centred amber circle with a lock, "Sign-up is closed right now", "New members can’t join at the moment. Please check back soon." and an "I have an account: Log in" button. The old small amber box above the form is gone. If the admin closes sign-up while the form is open and the member taps Sign up, the screen switches to the closed screen at once. Files: res/layout/fragment_signup.xml, SignUpFragment.kt, new res/drawable/ic_lock.xml and bg_closed_circle.xml.
- Banner pictures now move inside, like the website. The five Home banner pictures (Reshare verified, Saved, Done for today, No new contacts, Your limit is full) are drawn in code from the website's own shapes (new BannerPic.kt) instead of flat PNGs. After the banner slides in: the small dot / badge / hub rises into place (0.6 s delay, 0.7 s) and the clock hand, tick, dashes or gauge needle pops in (0.85 s delay, overshoots, settles in 0.5 s). The whole picture no longer rises on its own (the website does not do that). Nothing moves when the phone has animations turned off. The ban_*.png files are removed. Files: BannerPic.kt (new), HomeViews.kt, HomeFragment.kt, Anim.kt (bannerIn no longer moves the picture).
- Not built or tested on a phone yet. Check on a real phone: the closed screen (close sign-up on the website, then open the app’s sign-up), and each banner’s picture animation.

## App v40 (17 Nov 2026): website icons, message banners and animations, no database change
- Member app only (Android). Home messages are now coloured banners with a picture and a close button (✕), like the website: "No new contacts yet" and "No new contacts this time" (empty tray), "Reshare verified" (phone, clock, moon), "Done for today" (calendar), "Saved N contacts" (phone with tick), "Your limit is full" (red, full gauge; the Increase my contact limit button sits under it). One banner at a time. Closing it hides it on this phone until the message changes or the next day. The red "Almost full" box is gone: the gauge turns red from 90%, and the outline Increase button stays. Warnings (permission off, not verified, could not save, could not load) keep the plain boxes.
- One icon set (same 2px lines, rounded ends, soft filled part): Home / History / Profile in the bottom bar (the open tab has the filled part), moon and sun, and a log-out icon beside "Log out" on Profile. The chat button is unchanged.
- Animations, all off when the phone has animations turned off: banner slides in and its picture rises; on opening Home the contacts number counts up and the gauge fills; the Verified pill shines once; the countdown digits drop in each second; History day cards come in one after another.
- Files: HomeViews.kt (banner; the old limit box and red bar removed), HomeFragment.kt, HistoryFragment.kt, MainActivity.kt (tab icons), GaugeView.kt, new Anim.kt and ShineTextView.kt, res/drawable (ic_home, ic_home_on, ic_history, ic_history_on, ic_profile, ic_profile_on, ic_moon, ic_sun, ic_logout, bg_banner_ok / bad / close), res/drawable-nodpi/ban_*.png, layouts activity_main, part_header_user, fragment_profile. Not built or tested on a phone yet.

## Website v72 + app v38 (17 Nov 2026): onboarding pictures, no database change
- Member app: same screens, same layout and text; only the icons change to full-colour pictures in the banner style. Welcome: sign-up form, reshare, new viewers (ob_signup / ob_reshare / ob_viewers). Allow Contacts: contact card (ob_contacts). Allow Notifications: bell (ob_bell). Home, not verified, "3 quick steps": message, reshare, saved (ob_step_*). Files: WelcomeFragment.kt, PermissionsFragment.kt, HomeViews.kt, res/drawable-nodpi/ob_*.png. Not built or tested on a phone yet.
- Member site (index.js, style.css): the same three pictures in "3 quick steps to new contacts" on Home (not verified).

## Website v71 (17 Nov 2026): one icon set and new banner pictures, no database change
- Member site (app.html, index.js, style.css): Home / History / Profile, log out, dark mode (moon / sun), Import and the sign-up-closed lock use one icon style (same 2px lines and rounded ends, with a soft filled part; the open tab's filled part is stronger). New flat banner pictures: phone with a clock and moon (Reshare verified), contact cards with a download badge (contacts ready), phone with a tick (Saved), calendar with a tick (Done for today), empty tray (No new contacts), browser (Open in Chrome or Safari), full gauge (limit full). The badge rises and the tick / arrow pops as before. The chat icon is unchanged.
- Admin page (admin.html): bottom bar icons (Today, Lists, Users, Settings) in the same style.

## Website v70 + app v37 (17 Nov 2026): animations, no database change
- Member site (index.js, style.css): the Contacts received number counts up and the gauge fills when Home first opens; banners slide in and their picture moves a little (moon rises, tick pops); the Verified badge shines once; the countdown seconds tick; Import shows a spinner, then a tick, then the saved screen; History cards come in one after another.
- Admin page (admin.js, style.css): Today's numbers count up (and again when they change); pop-ups rise from the bottom with a small bounce; list rows come in one after another. No press animations on buttons.
- Admin app (ShotsActivity.kt): on the Logged screen the tick pops in, "22 logged" counts up and the rest slides up.
- All animations are off when the phone is set to reduce motion (website) or animations are turned off (admin app). Not tested on a phone yet.

## Website v69 (17 Nov 2026): wording put back, no database change
- Member site (index.js). Only the asked-for wording stays changed: "48 new contacts. Tap Import to add them to your phone.", the privacy line, and the centred "Reshare tomorrow to get more contacts." line after Import. Put back as in v67: Reshare verified, Done for today, No new contacts yet, No new contacts this time, Your limit is full, Download again approved, and the Instagram/WhatsApp text. After Import, the full "Import them" steps card is back under the "Saved 48 contacts" banner.

## Website v68 (17 Nov 2026): shorter banner wording, sign-up closed screen, no database change
- Member site (index.js, app.html, style.css). Shorter banners: "48 new contacts. Tap Import to add them to your phone.", "48 contacts saved. Open the file in your Downloads and choose Contacts.", "Reshare verified. Your new contacts arrive tonight at 10pm.", "Done for today. Reshare tomorrow to get more contacts.", "No new contacts yet. You have them all. Reshare to get more.", "Your limit is full. Increase it to keep getting new contacts.", etc. "Almost full" text removed (the red gauge shows it). Privacy line: "Keep the file private. Sharing it gets you banned." (not shown inside Instagram/WhatsApp, where there is no Import). After Import, the bottom line is centred: "Reshare tomorrow to get more contacts." Sign-up closed: the form is hidden; a centred lock, "Sign-up is closed right now", and "I have an account: Log in".

## Website v67 (17 Nov 2026): message banners (design D), one at a time, with a close button, no database change
- Member site (index.js, app.html, style.css). Every message on Home (and "Sign-up is closed" on the sign-up page) is now a coloured banner with a small picture: green good news, amber do something, red a problem. Only one banner shows at a time: "Download again approved" and the Instagram/WhatsApp warning are written into the "contacts are ready" banner; "Almost full" is a short red line over the Increase button; after Import, one "Saved 48 contacts" banner says how to import (the separate "Import them" box is gone). Every banner has a ✕: it slides away and stays closed on that phone until the message changes (next day or a new state). The app is not changed yet.

## Website v66 + app v36 (17 Nov 2026): clearer member messages, no database change
- Wording only (website index.js; app HomeFragment.kt, HomeViews.kt). "All caught up" → "No new contacts yet. You've imported every contact you've earned. Reshare the admin's status again to get more." "Already downloaded today" → "Done for today…". "No contacts today" → "No new contacts this time…". "Almost at your limit" → "Almost full…". "Contact limit reached" → "Your limit is full…". "Reshare verified" now says "Come back then to import them". Also: "20 new contacts are ready", "Open this page in Chrome or Safari", "Download again approved", "Nothing to import right now". The look of the message boxes is not changed yet.

## Website v65 (17 Nov 2026): limit screens tidied, no database change
- Member site (index.js, style.css). Limit reached: no "Get new contacts in" countdown (nothing comes until the limit is raised); the reason comes first ("Contact limit reached. You have received all 500 contacts your limit allows…") and then the "Increase my contact limit" button (was "UPGRADE CONTACT LIMIT" in capitals, above the note). Contacts ready: "Keep contact files private…" is a short red line under Import contacts instead of a box, so at 90% only one red box ("Almost at your limit") shows. WhatsApp messages unchanged.

## App v35 (17 Nov 2026): Log day picked on a calendar, no database change
- Admin app: the day on the Log screen (top right, with a calendar icon) opens a calendar pop-up like the admin website's: month with ‹ ›, today ringed in green, later days greyed out, tap a day to pick it, Cancel / Today. Any day up to today (it used to be a list of the last 7 days). Files: Ui.kt (pickDate), ShotsActivity.kt, res/drawable/ic_calendar.xml. Not built or tested on a phone yet.

## App v34 (17 Nov 2026): new Log check screen in the admin app, no database change
- Admin app (admin/.../ShotsActivity.kt): the screenshot screens are titled just "Log". The check screen's green header has "Log" on the left, the day on the right (tap to change it) and three tabs: Ready, Check (amber dot while something is waiting) and Skipped. Tap a tab or swipe left / right. Ready has Tick all / Untick all; Check has a Choose button per unsure name and shows "All checked" when done; Skipped lists the non-members. The Log button stays at the bottom and shows "Logging 22…" while it saves. You still check and tap Log; nothing is logged by itself.
- Website unchanged (still v64). Not built or tested on a phone yet.

## Website v64 + app v33 (17 Nov 2026): admin bottom bar like the member app, no database change
- Admin page (style.css, admin.js): the bottom bar is now the member app's: full width, green gradient, round top corners, white icons (open tab solid white, others faded); darker green in dark mode. Same width as the page on a computer. "Remind this group" sits just above it and the list scrolls clear of it.
- Admin app (admin/.../MainActivity.kt): the phone's own bottom area (under the home line) takes the colour the page asks for: green under the bar, white under an open pop-up or full screen. Sign in and Setup keep the normal bar. Needs website v64 for the green (an older website just gives white).
- Not built or tested on a phone yet.

## Website v63 + app v32 (17 Nov 2026): no bottom bar until verified; admin app splash, no database change
- Member site (index.js, screens.css) and member app (MainActivity.kt, HomeFragment.kt): members who are not verified (or still need to enter a name) see Home only, with no Home / History / Profile bar. The app also hides its floating chat button then (Home already has "Message admin on WhatsApp"); the "only available to verified members" pop-up can no longer appear. The bar comes back as soon as the status says verified.
- Admin app: opens with the member app's splash (icon in the middle) on green, kept until the admin page has signed in (at most 10 seconds), then Today. The green "Opening your admin page" screen is gone; a green "No internet connection" screen with Try again only appears if the page can't load.
- App History arrows and the Profile moon from app v30 are included. Not built or tested yet.

## App v30 (17 Nov 2026): arrows on every day, moon on Profile, no database change
- Android member app only (play-ready v30). History: the amber (reshared, nobody joined) and red (did not reshare) day cards now show the side arrow like every other day; they still do nothing when tapped. Home Recent: every row shows the arrow; +0 rows no longer open an empty day. Profile: the light/dark button (moon / sun) sits in the green header next to Log out, as on website v62.
- Files: app/src/main/java/com/viewgrow/app/HomeViews.kt, ProfileFragment.kt; res/layout/item_history_missed.xml, item_history_quiet.xml, fragment_profile.xml. Not built or tested yet (GitHub builds it).

## 20261117120000: History opened day + People lists, website v62
- Status: APPLIED 17 Nov 2026 by the owner. Confirmed: pg_proc shows web_day_contacts and web_people.
- Adds two public read-only functions: `web_day_contacts(p_phone, p_pin, p_day)` (the people of one day) and `web_people(p_phone, p_pin, p_offset, p_limit)` (everyone the member has imported, newest first). Both read only the logged-in member's own `web_received` rows, so contacts that are not yet imported or are over the limit are never shown. No table changed, nothing deleted.
- Website v62 (no other database change): bottom bar (Home, History, Profile), History with green / amber / red day cards (red = a day in between with no reshare, filled in on the page; amber = reshared, nobody joined), opened day with name, number and a WhatsApp chat button, Profile (name, number, FAQ, Terms, Privacy, Delete my account, Log out). Dark mode button is in the green header (Home and Profile). Every day card and Home Recent row shows the side arrow; only days with people open (amber, red and +0 days do nothing when tapped, as in the app). `web_people` is not used by any screen yet (the People tab was left out on purpose).
- Files: `app.html`, `index.js`, new `screens.css`. The Android app is not changed.
- Not tested live yet: History, tap a day, chat button, Profile links, dark mode on Profile, back button on the opened day.

## Website v61 (17 Nov 2026): member dashboard looks like the app Home
- No database change. Files: `app.html` (dashboard header and slots), `index.js` (`render`, theme icons), `style.css` (block at the end). Needs only the website deploy.
- Removed from the dashboard: the "Available now" box (the count now shows in a notice when files are ready) and the old top bar.
- Not tested live yet: log in, a verified day, Import contacts, Increase my contact limit link, chat button, dark mode.

## 20261117100000: saved messages (Message all members), website v60
- Status: APPLIED 17 Nov 2026 by the owner. Pre-check passed (_web_admin_ok exists: true). Confirmed: web_admin_saved_list returns {ok: true, rows: []}.
- Adds table admin_saved_messages (RLS on, no public access) and admin functions web_admin_saved_list(p_key), web_admin_saved_add(p_key, p_title, p_body, p_mode, p_url), web_admin_saved_delete(p_key, p_id). Changes no existing function.
- Website (admin.js, style.css): the Message all members sheet is now Title, Message, Link, with two icons beside Link. App icon = tapping opens the app (no link). Chat icon = tapping opens the admin's WhatsApp chat (the app already knows the number). Neither on = the typed https link, or the app if empty. Send to all members, then Save and Saved list (replaces the bottom Cancel; the top x closes). The old grey text under the heading and the link hint line are gone. The heading now lines up with the x.
- Saved list: tapping a row loads it into the sheet to edit and send; the trash icon deletes at once (no question). Save and the list need the migration above; without it the buttons say so.
- Edge Function notify-status: kind "all" also accepts open: "admin" (tap opens the admin's WhatsApp chat). Status: DEPLOYED 17 Nov 2026 by the owner. Confirmed: the deployed code has the toAdmin change (checked from the downloaded function). Not yet tested live: the chat icon and app icon sends.
- No app change, no versionCode change. Phones on the current app open the admin chat for open "admin" (already supported in StatusPush.kt).

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

## Message all members: optional link
- The admin's "Message all members" sheet has an optional Link field. With a link, tapping the notification opens it; without one it opens the app, as before.
- Edge Function `notify-status` (kind "all") accepts `url` (https only; anything else is refused with BAD_LINK) and sends `open: "link"` + `url`. Redeploy this function in Supabase.
- Member app reads `url` in `StatusPush.kt`. Older app versions ignore it and just open the app. No database change.
