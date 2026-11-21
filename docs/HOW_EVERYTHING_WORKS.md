# How ViewGrow works, in plain words

Last updated 21 Nov 2026. Written so anyone can understand it, even a 12-year-old. No technical knowledge needed.

Every sentence here was taken from the project's own notes and code. If something here and the live database ever disagree, the live database wins, and the check files (see "Health checks") will tell you.

---

## 1. The big idea

Lots of people post on WhatsApp Status, and they all want more people to see it. ViewGrow is a club where members help each other.

- A member watches and reshares **the admin's status**.
- The admin checks that it really happened. This is called being **verified**.
- Every night at 10pm, everyone verified that day gets a **fresh list of new people** to save in their phone.
- Those new people now see the member's status. And when other members do the same, the member's own number lands in other people's phones too.

The admin (the owner) runs everything. Members only get contacts after the admin has checked.

---

## 2. The picture in one minute

Think of a restaurant.

| In the restaurant | In ViewGrow | What it does |
|---|---|---|
| The menu and the tables where you sit | **The website** and **the Android app** | What people see and tap. They never decide anything. |
| The kitchen | **The database (Supabase)** | The brain. It holds all the data and makes every decision. |
| The head chef's office | **The admin page** and **the admin app** | Where the owner checks reshares, bans people, sends messages. |
| Two delivery guys | **Edge Functions** (`verify-purchase`, `notify-status`) | Little programs that do one job: check a payment, or send an alert. |

```
 Member's phone (app)  ──┐
 Member's browser (site) ─┼──►  DATABASE (Supabase)  ◄──┬── Admin page (website)
                          │      holds the data and      └── Admin app (phone)
                          │      all the rules
                          └──►  Google Play (payments)  ──► verify-purchase checks it
                                Firebase (alerts)       ◄── notify-status sends it
```

**The most important rule:** the website and the app only *show* what the database says. If the website says one thing and the database says another, the database is right.

---

## 3. Who is in charge of what (the code map)

If you want to find the code for something, look here first.

### The database (folder `supabase/`)

| What | Where | In plain words |
|---|---|---|
| Every change ever made | `supabase/migrations/` | One file per change, oldest first. |
| How to undo a change | `supabase/rollback/` | An "undo" file for the bigger changes. |
| Health checks | `check_everything.sql`, `check_everything_part2.sql` and others | Safe to run any time. They only look; part 2 also tests rules with pretend members and undoes them. |
| The readable picture | `schema.md`, `state.md` | What exists today and the rules for changing it. |
| The diary | `changelog.md` | Every change, with whether it was applied. |
| The two little programs | `supabase/functions/verify-purchase`, `supabase/functions/notify-status` | The Edge Functions. |

Rule: never change the database by hand in the dashboard. Write a migration file, then update the notes. (This is how 18 functions ended up "dashboard only" and had to be written down afterwards. They are all recorded now.)

### The website (folder `website`)

| File | What it is in charge of |
|---|---|
| `index.html` | The home page. |
| `app.html` + `index.js` | The **member site**: sign up, log in, dashboard, History, Profile, downloads. |
| `admin.html` + `admin.js` | The **admin page** (the control room). Needs the admin password. |
| `api.js` | Talks to the database. Also formats phone numbers, shared by both pages. |
| `config.js` | Small settings: the database address, and `PLAY_URL` (the Google Play link, so the "Get the app" card appears). |
| `style.css`, `screens.css`, `nav.css`, `guide.css` | How everything looks. |
| `country.js`, `phone-lib.js`, `phone-examples.js`, `flags/` | The country picker, flags, and each country's phone number rules. |
| `contact.js`, `getapp.js`, `nav.js` | The contact buttons, the "Get the app" card, the top menu. |
| `pwa.js`, `sw.js`, `manifest.webmanifest` | Lets people add the site to their phone's home screen. |
| `privacy/`, `terms/`, `contact/`, `delete-account/` | The website's own rule pages. |
| `android/` | The app's own copies of those four pages, so app members only see the app's rules. |
| `tools/legal.py` | Makes all eight rule pages and the app's `Pages.kt` from one place, so they never disagree. |
| `blog/` | Guides that bring people in from Google. |
| `netlify.toml` | Hosting rules (for example, hiding the `supabase/` folder from visitors). |

### The member app (folder `app/`, package `com.viewgrow.app`)

| Job | Files |
|---|---|
| **Talking to the database and remembering things** | `Config.kt` (address), `Api.kt` (calls), `Session.kt` (login token), `SecretBox.kt` (locks the saved login), `Store.kt` (the phone's memory) |
| **Screens** | `WelcomeFragment`, `SignUpFragment`, `LogInFragment`, `PermissionsFragment`, `HomeFragment` + `HomeViews`, `HistoryFragment`, `HistoryDayFragment`, `ProfileFragment`, `FaqFragment`, `PurchaseHistoryFragment`, `DeleteAccountFragment`, `TextPageFragment`, `MainActivity` (the frame around all of them) |
| **Stop pages** (Banned, Blocked, Account deleted, One account per phone, Sign-up blocked) | `StopFragment.kt`, `Ban.kt` |
| **Saving contacts to the phone** | `ContactsSaver.kt` (members), `AdminContactSaver.kt` (the admin's number), `VipContactSaver.kt` (VIPs), `ContactsDisclosure.kt` (the plain-words notice before Android's own box) |
| **Taking people off the phone** | `Departed.kt` (people who were banned or deleted), `Swaps.kt` (people who changed number) |
| **The phone's own record of what it received** | `HistoryLog.kt` |
| **Timing** | `Sync.kt` (background sync), `Nightly.kt` (the 10:10pm check and 8am message), `Release.kt` (the release clock), `Reminder.kt`, `ReshareReminder.kt`, `StatusPush.kt` (Firebase alerts and the 10pm wake-up), `Alerts.kt`, `PermissionWatch.kt` |
| **Money** | `BillingManager.kt` (Google Play), `UpgradeSheet.kt` (the pack pop-up), `PurchaseLog.kt` |
| **Safety and fairness** | `Device.kt` (the phone's code, used to stop banned people coming back), `ReportSheet.kt` (report a person), `PhoneChangeSheet.kt` (ask to change number), `CrashCheck.kt` (one practice crash report) |
| **Phone numbers** | `Phone.kt` (formatting), `CountryField.kt` (country picker) |
| **Look and feel** | `AppTheme.kt`, `Ui.kt`, `Anim.kt`, `BannerPic.kt`, `GaugeView.kt`, `ShineTextView.kt`, `TextFit.kt`, `ScaleGuard.kt`, `KeyboardAware.kt`, `ConfirmDialog.kt` |
| **Small extras** | `UpdateCheck.kt` ("Update ViewGrow" pop-up), `Rating.kt` (Google's rate-this-app pop-up), `Web.kt` (opens the app's website pages), `Pages.kt` (offline copy of Terms and Privacy), `Remote.kt` (the link to the admin's chat) |

### The admin app (folder `admin/`, "VG Admin", never on Google Play)

| File | Job |
|---|---|
| `MainActivity.kt` | Shows the real admin page inside the app, so the app and the website always match. |
| `Members.kt` | Every 15 minutes, saves new members to the admin's phone as "Name VG". |
| `ShotsActivity.kt` | Reads names from screenshots of the status viewers list, so the admin can log reshares faster. |
| `SyncActivity.kt` | The sync screen: on/off, recently saved, Check now. |
| `Api.kt`, `Config.kt`, `Store.kt`, `SecretBox.kt`, `Ui.kt` | Same jobs as in the member app. |

### Other places

| Where | What |
|---|---|
| **Netlify** | Hosts the website. |
| **GitHub** | Builds the apps (`deploy.yml` for the member app, `admin.yml` for the admin app) and runs the nightly backup (`.github/workflows/backup.yml`). |
| **Firebase** | Sends the alerts to phones, and collects crash reports (Crashlytics). |
| **Google Play** | Sells the apps, takes payments, confirms them. |

---

## 4. A member's journey

1. **Sign up.** Name, WhatsApp number (any country; the picker shows flags) and a password of 6 to 30 letters or numbers. They agree to share their name and number with other members, and they must be 18 or older. The name cannot be changed later. The admin can close sign-up, and one internet connection can only make 3 accounts a day.
2. **Get verified.** The member messages the admin, watches the admin's status and reshares it. The admin checks and logs it (by name or number, for the right day). Nothing is given before this.
3. **The 10pm release.** Every night at 10pm (Nigeria time) the day closes, the list is saved, and everyone verified that day is released together. Verified after 10pm? That counts for the next night. (The admin can switch to weekly or monthly, or press **Release now**. Today it is set to every day.)
4. **Getting the contacts.**
   - **App:** the contacts are saved straight into the phone. Each is named "Name VG" and has the company tag VG, so the app can find its own contacts later. The app also checks by itself every 1, 6, 12 or 24 hours, and once right after each release.
   - **Website:** the member presses Import contacts and gets a file to open on their phone, once a day.
   - Nothing counts as received until the member confirms it.
5. **The limit.** A member can receive 500 contacts in total, for life (the admin can change the default, or give one person a different number). Nobody is ever given the same person twice. At the limit, nothing more arrives until the limit goes up.
6. **Extra contacts for everybody.** Every contact file or phone also gets the admin's number and the admin's **VIP contacts** at the top. They do not use up the member's limit. And the admin and the VIPs are never handed out as ordinary member contacts.

### Buying a bigger limit
- **App:** three packs (+1,000, +3,000, +5,000) paid through Google Play in the member's own money. A pack only adds *room*. It does not promise contacts, because the release and the admin still decide who gets what.
- **Website:** the member messages the admin, who arranges payment and raises the limit by hand.
- **Refunds** go through Google Play.

### What the app screens do
- **Home:** contacts used out of the limit (the gauge goes red from 90%), a countdown to the next release, and a coloured message for each situation.
- **History:** day by day, or person by person. The phone keeps its own record, because the database only sends counts. Each person has a WhatsApp chat button and a "⋮" menu to **report** them.
- **Profile:** sync settings, Delete My Contacts (removes only ViewGrow's contacts), **Change phone number**, FAQ, purchase history, Terms, Privacy, Delete my account, Log out.
- **Notifications:** a reminder after 3 days with no sync; "Today's status is up"; the admin's own messages; an alert near the limit; a weekly summary (Sunday 6pm); and every morning at 8am either "48 new status viewers added last night" or "41 people joined last night".
- **Update pop-up:** each time the app opens, if Google Play has a newer version.

---

## 5. The database, in plain words

The database is a set of **filing cabinets (tables)** and **staff (functions)**. Nobody can open a cabinet directly, not even the website or the app. They can only ask a staff member, and the staff member checks the rules first. So every rule lives in one place and nobody can sneak around it.

### The filing cabinets

| Group | Tables | What they hold |
|---|---|---|
| **Members** | `web_users`, `web_sessions` | Every member (name, number, scrambled password, banned or not, limit). Sessions are the logins on each phone or browser. |
| **Activity** | `web_verifications`, `web_received`, `web_downloads`, `web_redownload_grants` | Who was verified on which day, who was given whom, every download, and the admin's permission for a full re-download (one a month). |
| **Daily lists** | `web_snapshot_contacts`, `web_snapshot_meta`, `web_day_releases`, `web_release_config` | The frozen list for each day, its summary, which days were released, and the release schedule. |
| **Money** | `web_purchases` | Each real Google Play payment. When a member is deleted, their purchases are deleted with them. |
| **VIPs and settings** | `web_vip_contacts`, `web_settings`, `web_signup_log`, `web_admin_attempts` | The admin's VIP list, all settings (including the scrambled admin password), sign-ups per connection, wrong admin-password tries. |
| **Deleted and left** | `deleted_numbers`, `web_gone` | Numbers of deleted accounts (they can't log in or sign up again), and the list of people to take off other members' phones. |
| **Reports and bans** | `web_reports`, `web_devices`, `web_banned_devices`, `web_device_flags` | Reports about people, the phones each member used, banned phones, and "this phone tried to make a second account" alerts. |
| **Number changes** | `web_phone_changes`, `web_phone_swaps` | Requests to change a phone number, and what other members' phones must update. |

### The staff (functions), by job
- **Public ones** (the site and app may call them): sign up, log in and out, status, download and confirm, History, report, change-number request, delete account, purchase checks.
- **Admin ones** (names start `web_admin_`): they all demand the admin password first. They do the admin page's jobs: log reshares, ban, delete, reset a password, set a limit, release now, reports, flagged sign-ups, number changes, deleted numbers, VIPs, settings, backup, change the admin password.
- **Inside helpers** (names start `_web_`): little tools the other functions use, like "make this number the standard form". The public cannot call them.

### The nightly job
At 10pm Nigeria time a scheduled job (`pg_cron`) saves the day's list. At 10:05pm a silent signal wakes the members' phones so they save the night's contacts right away. The 10:10pm check and the 8am catch-up on the phone are backups. The health check showed it has run every night.

---

## 6. Safety and fairness rules

**Passwords and logins**
- Passwords are stored scrambled (the database can't read them back). On the phone, the saved login is locked with the phone's own key vault.
- The password is sent **once**, at log in. After that the phone uses a **login token** (it lasts 90 days since last use). A new password, or deleting the member, ends the tokens.
- **5 wrong passwords** lock a member until the admin resets it. The admin's password locks that connection for 15 minutes after 10 wrong tries.

**Bans**
- A banned member cannot use the app. The ViewGrow contacts the app saved are removed from the phone. Leaking contact files gets a member banned.
- **Banned phone:** the app sends a code made from the phone (never the Android ID itself). A banned member's phone cannot sign up again with another number.
- **One account per phone:** if a phone that already has an account tries to sign up again, it is stopped and shows up for the admin under **Flagged sign-ups**. The admin can **Allow one** more account, or close it.

**Reports (app only)**
- A member can report someone they were given (spam, scam, harassment, sexual, under 18, other), and can also remove them from their own phone.
- After **3 different members** report the same person, that person is banned automatically (the admin can change the 3, or turn it off). A report for "under 18" bans at once, until the admin checks.
- The admin sees reports on **Reports** (Check, Banned, Closed) and can Dismiss, Ban, Unban or Keep banned. The reported person is never told who reported.

**People who leave**
- When a member is deleted or banned, every phone that was given them is told to remove that contact. Phones only remove contacts ViewGrow saved, never the member's own contacts, the VIPs or the admin.

**Deleting an account**
- **App:** Profile, Delete my account. Permanent. The member's data and purchases are removed and the number goes on the deleted list, so it can't join again unless the admin lets it back (**Settings, Deleted numbers, Let back in**).
- **Website members** ask the admin, who uses Delete user. That does not block the number.

**Changing a phone number**
- The member asks in Profile. The admin sees it under **Number changes**, checks the new number by WhatsApp, then Approves or Refuses. Approving moves everything (history, verified days, limit, purchases) to the new number, logs the member out, and frees the old number. Other members' phones update the saved contact.

**Payments**
- Google confirms every payment (the `verify-purchase` program). Each payment counts only once.

**Nobody can read the database directly.** Row-level security blocks every cabinet; only the staff functions work.

---

## 7. The admin page

Open it on the website (or in the VG Admin app). Four tabs:

- **Today:** log reshares (paste anything; names ending in VG and phone numbers are picked out), Notify members ("Today's status is up"), a message to everyone, New members (a contact file of recent sign-ups so the admin's phone has them), **Flagged sign-ups**, **Reports**, **Number changes**, the day's numbers, Save list now, Release now, and a summary PDF.
- **Pool:** the saved lists, day by day.
- **Users:** groups (All, Verified, Never reshared, Limit full), each showing how many use the app or only the website. Open a member to ban or unban, reset their password, set a personal limit, allow a full re-download, or Delete user. "Verified" here means "has reshared on any day"; "Verified today" is its own filter.
- **Settings:** sign-up open or closed, sign-ups per connection, default limit, how people can reach you, **VIP contacts**, **Deleted numbers**, **Admin password** (change it here), Backup (a file of all data, no passwords), the release schedule, and dark mode.

---

## 8. Backups, crash reports and health checks

- **Backup:** every night at 02:17 (Lagos) GitHub makes a locked copy of the database and keeps it for 30 days. The password for the lock is `BACKUP_PASSWORD` (keep it safe!). How to open one is at the top of `backup.yml`.
- **Crash reports:** the member app sends a report if it crashes (Firebase Crashlytics). It contains no name, number, password or contacts.
- **Health checks:** `check_everything.sql` and `check_everything_part2.sql`. Last run, 21 Nov 2026: **169 checks, 0 problems, 40 of 40 rule tests passed.** They are safe to run any time.

---

## 9. When something goes wrong, look here

| Problem | Look at |
|---|---|
| Someone can't log in | The member's row in Users (locked? banned?), then `web_login` |
| A member got no contacts at 10pm | Is the day released? Pool tab, and the nightly job in the health check ("last saved list") |
| A payment didn't raise the limit | `verify-purchase` and the `web_purchases` table |
| "Today's status is up" didn't arrive | `notify-status` and the Firebase secret |
| The app looks different from the website | The app needs a new build; the website needs a new upload |
| Something odd in the data | Run `check_everything_part2.sql` and look for PROBLEM rows |

---

## 10. Still not tested on a real phone
The app has not been built or run on a phone since these were added, so test them: **Report a person**, **Account deleted**, **Change phone number**. Also still to do: a real Google Play purchase with a test account, the Google Play Data safety form, and checking the practice crash report in Crashlytics.

---

## 11. Words you will see

- **Verified:** the admin confirmed your reshare.
- **Release:** the moment saved lists become available to download.
- **Pool:** the saved list of verified people for a day.
- **Batch:** days released together.
- **Limit:** the most contacts a member can ever receive.
- **VG:** the tag on contacts the app saved, so it can find and remove them later.
- **VIP contacts:** a few contacts the admin chose that every member gets at the top of their contacts.
- **Migration:** one file that makes one change to the database.
- **Edge Function:** a small program that runs on Supabase's servers for one job.
- **Token:** a secret code that proves you logged in, so your password isn't sent again.
- **Row-level security:** the lock on every table that stops direct access.
