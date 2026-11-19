# How ViewGrow works, in plain words

Last updated 16 Nov 2026. Written for the owner. No technical knowledge needed.

## The big idea

ViewGrow helps people get more people to see their WhatsApp status. Members help each other: when you reshare the admin's status and the admin confirms it, you receive new contacts to save on your phone. When other members do the same, your number reaches them too. The admin runs everything.

## The three parts

1. **The website (viewgrow.site).** Two pages in one: the member site (sign up, log in, download contacts) and the admin page (the owner's control room). It also has guides (blog), and Privacy, Terms, Contact and Delete My Account pages for the website. The app has its own copy of those four pages on the same site, under viewgrow.site/android/. They are not in the website menu and have no menu, so app members only see the app's rules.
2. **The database (Supabase).** The brain. It holds every member, every rule and every contact list. The website and the app only show what the database says. They never decide anything themselves.
3. **The Android app.** The same account and the same database as the website, made for phones. It also saves the contacts straight to the phone and lets members buy a bigger limit through Google Play.

Two extra pieces: small server programs (Edge Functions). "verify-purchase" asks Google whether a payment is real before the limit goes up. "notify-status" sends the "Today's status is up" alert through Firebase.

## A member's journey

1. **Sign up.** The member enters a name, their WhatsApp number and a password (6 to 30 letters or numbers), and agrees to share their contact with other members. The name cannot be changed later. The admin can close sign-ups, and each internet connection can only sign up 3 people a day.
2. **Reshare and get verified.** The member messages the admin on WhatsApp, views the admin's status and reshares it. The admin checks it and logs it in the admin page (by name or number, for the right day). Only after that does the member count. Nothing is given before verification.
3. **Release at 10pm.** Every night at 10pm Nigeria time the day closes, the list is saved, and the people verified that day are released together. A reshare verified after 10pm goes into the next night's release. The admin can change this to once a week or once a month, or press Release now. Today it is set to every day.
4. **Get the contacts.** On the website the member presses Import contacts and gets a file to import into their phone, once a day. In the app the contacts are saved straight to the phone, tagged VG, and the app checks again by itself every 1, 6, 12 or 24 hours. If something goes wrong half way, the member gets the same file again. Nothing counts as received until the member confirms it.
5. **The contact limit.** Each member can receive 500 contacts in total, for life (the admin can change the default or give one person a different limit). Nobody is ever given the same person twice. When the limit is reached, no more arrive until the limit is raised, and then only contacts from later days.
6. **Buying a bigger limit.** On the website the member messages the admin, who arranges payment and raises the limit by hand. In the app there are three packs, +1,000, +3,000 and +5,000 contacts, paid through Google Play in the member's own currency. A pack adds room in the limit. It does not guarantee more contacts, because the admin and the release still decide who gets what. Refunds go through Google Play.

## What the app has

- **Welcome, Sign up, Log in.** Light and dark mode. Big text sizes are respected.
- **Home.** How many contacts used out of the limit, a countdown to the next release, and clear messages for each situation: not verified yet, limit reached, almost at the limit (from 90%).
- **History.** Day by day, or person by person, each with a WhatsApp chat button. The phone keeps its own record because the database only sends counts.
- **Profile.** Sync times, pause sync, Delete My Contacts (removes only the ViewGrow contacts), FAQ, Purchase history, Terms, Privacy, Delete my account, Log out.
- **Increase my contact limit.** The pack pop-up and Google Play payment.
- **Notifications.** A reminder after 3 days without a sync or when Contacts permission is off, a notification that opens the admin's WhatsApp chat so the member can get the status, an alert at 80% of the limit, and a weekly summary on Sunday at 6pm. Every morning at 8am, one of: "48 new status viewers added last night" (contacts saved at night are announced then, not at night), or "41 people joined last night" for a member who was not on last night's list (tapping it opens the admin's chat; needs web_last_release).
- **Messages from the admin.** On the admin page: "Write your own message to everyone" (Today), and "Remind this group" on the Users tab for two groups: Never reshared (signed up, never logged) and Limit full. Only members who use the app get them; each group shows how many use the app (Android icon) and how many only the website (globe icon). The app tells the database it is in use, at most every 6 hours.
- **10pm wake-up.** At 10:05pm Lagos a scheduled job in Supabase sends a silent signal; phones save that night's contacts straight away. The 10:10pm check and 8am catch-up stay as backups.
- **Update prompt.** When Google Play has a newer version, the app offers it (at most once a day).
- **"Today's status is up" alert.** After posting the day's status, the admin presses Notify members on the admin page (Today). Every app member gets a notification; tapping it opens the admin's WhatsApp chat. Sent through Firebase (Google), by the Edge Function notify-status.
- **Same-night delivery.** Besides the member's chosen interval, the app checks once just after each 10pm release (10:10pm Lagos), so new contacts usually arrive the same night.
- **Admin's number saved for the member.** Once Contacts is allowed, the app saves the admin's WhatsApp number as "ViewGrow Admin" (once), so the member can see the admin's status without saving it by hand.
- **VIP contacts.** The admin keeps a list in Settings > VIP contacts on the admin page (a name and a number each, as many as wanted). When a member allows Contacts, the app saves every one of them on the member's phone, named exactly as the admin typed. Saved once per number: a VIP the member deletes is not put back; a VIP added later reaches phones at their next check; removing one from the list does not remove it from phones. Delete My Contacts leaves them alone.
- **Chat button.** A floating button that opens the admin on WhatsApp.
- **Banned and Blocked pages.** Banned (red): the member is banned, and the ViewGrow contacts the app saved are removed. Blocked (amber): password locked after too many wrong tries.
- **Permissions.** Step 1 of the setup explains on the screen, in plain words, what the app saves and reads with Contacts. "Agree and allow" opens Android's own box; "Not now" skips. When Contacts is turned on later from Home, the same words show in a pop-up first.

## The admin app (ViewGrow Admin)

The admin's own Android app, not on Google Play. It shows the real admin page inside it (so it always matches the website), and adds two things a website cannot do: it saves every new member to the admin's phone as "Name VG" in the background (about every 15 minutes when online), and it logs reshares from screenshots of the status viewers list (the names are read on the phone, matched to members by name or by the number saved in the phone, and the admin ticks and logs). It uses the same database functions as the website.

## What the website has

**Member site:** sign up and log in, a dashboard with contacts received out of the limit, a countdown to the next file, a list of verified days, the Import contacts button, a button to raise the contact limit (opens WhatsApp to the admin, who arranges payment; the website's Terms say this), an install card (add to the phone's home screen), and a dark mode switch. The home page has a "Get the ViewGrow app" card that appears once the Google Play link is added.

**Admin page (needs the admin key):**
- **Today:** Log reshares (paste anything: names ending in VG and phone numbers are picked out of copied WhatsApp text), Notify members, Write your own message to everyone (the "status is up" alert to app members), New members (one contact file of everyone who signed up since the last file, saved as "Name VG", so the admin's phone has them and they can see the status), the day's numbers (verified, signed up, downloaded, total, locked, banned), Log reshares (choose the day, paste names or numbers), Save list now, go to a date, export a summary PDF.
- **Pool:** the saved lists, day by day.
- **Users:** groups in the header (All, Verified, Never reshared, Limit full) with app / website counts; Remind this group on the last two; open a member to ban or unban, reset their password, set a personal limit, allow a full re-download (one a month), or Delete user.
- **Settings:** sign-up open or closed, sign-ups per internet address per day, default contact limit, how people can reach you, Backup (a file of all data, no passwords), the release schedule and Release now, and dark mode.

## Safety and rules

- **Wrong passwords.** 5 wrong tries lock a member until the admin resets the password. The admin key locks that connection for 15 minutes after 10 wrong tries.
- **Bans.** A banned member cannot log in. Leaking contact files gets a member banned.
- **Passwords** are stored scrambled in the database, and encrypted on the phone.
- **Nobody can read the database directly.** Visitors can only use the set of functions the site and app are allowed to call. Row-level security blocks everything else.
- **Deleting an account.** Website members ask the admin (the Delete user button; the number is not blocked). In the app: Profile, Delete my account. It is permanent: the member's data is removed and the number goes on a blocked list, so it cannot log in or sign up again unless the admin removes it from that list. No approval step. The admin's Delete user button is for members who cannot log in; it does not block the number.
- **Payments.** Google confirms every payment. A payment can only be counted once, and a used-up payment cannot be replayed.

## Checked live on 16 Nov 2026

- Every function the app and site use exists and works for visitors.
- The 10pm day, the nightly job (7 of 7 recent nights) and the Daily release setting.
- Nobody can reach the tables directly; nothing old is left behind.
- 13 members, 3 verifications, 1 finished download. One download a day, no repeated contacts and one verification a day are enforced by the database itself.

**Not tested yet:** a deleted number being blocked, and a real purchase.

## Words you will see

- **Verified:** the admin confirmed your reshare.
- **Release:** the moment saved lists become available to download.
- **Pool:** the saved list of verified people for a day.
- **Batch:** days released together.
- **Limit:** the most contacts a member can ever receive.
- **VG:** the tag on contacts the app saved, so it can find and remove them.
