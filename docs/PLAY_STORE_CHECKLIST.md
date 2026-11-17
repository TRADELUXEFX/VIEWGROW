# ViewGrow: Play Console checklist

## Before the first upload
1. Database: `20261116140000_deleted_numbers.sql` and `20261116180000_delete_removes_data.sql` are applied (account deletion really removes the data). To allow a number back: `delete from public.deleted_numbers where public.vg_phone_key(phone) = public.vg_phone_key('THE_NUMBER');`
2. Play Console URLs: Privacy Policy `https://viewgrow.site/android/privacy/`, Delete account `https://viewgrow.site/android/delete-account/`. These are the app's own pages (not the website's `/privacy/` and `/delete-account/`, which are for the website only). They have no menu and no link to the rest of the site. The app's built-in copy (`Pages.kt`) has the same words; the website repo makes both from one file.
3. **Server-side purchase check.** Done: Edge Function `verify-purchase` (supabase/functions/verify-purchase/index.ts), with `web_purchase_precheck` and `web_apply_purchase` live in the database. Tested with a fake token. Still to do: one real purchase with a Play test account.
4. GitHub secrets for the upload key: `KEYSTORE_BASE64` (the key file encoded as base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Build with `gradle :app:bundleRelease` and upload `app-release.aab`. Turn on Play App Signing.
5. Create the three in-app products `contacts_1000`, `contacts_3000`, `contacts_5000` (consumable, active, with prices).
6. Install the release build on Android 14, 15 and 16 phones. Test: sign up, contacts permission (Agree and No thanks), sync, notifications off, delete contacts, **delete account**, a test purchase, large font size.

## Declarations
- **Permissions declaration (Contacts):** core function = saving contacts received from other members so they can view the member's WhatsApp status. WRITE_CONTACTS adds them; READ_CONTACTS only dedupes numbers on the phone and finds the app's own VG contacts for removal. Nothing is uploaded. The Contact Picker cannot write contacts or scan for duplicates. This is the item most likely to get a policy question; answer it truthfully and keep the in-app disclosure identical to the listing.
- **Data safety:** collected and shared with the service provider (Supabase): Name, Phone number, Contacts (numbers of other members delivered to the user; the user's own contacts are NOT uploaded), Purchase history, IP address (abuse limiting), phone number kept on a deleted-accounts list after deletion (abuse prevention; say so in the Privacy Policy), Device or other IDs: yes, the Firebase Cloud Messaging installation ID (collected by Google's Firebase SDK to deliver the "status is up" notification; not linked to the account, not used for ads). Data encrypted in transit: yes (HTTPS only). App activity: the last time the app checked in (to show the admin who uses the app and send app reminders), linked to the account. Deletion request: yes, in app and at the web URL.
- **Target audience:** 18+. **Content rating:** complete the questionnaire. **Ads:** none.
- **App access:** give reviewers a test number and password.
- Listing: do not use WhatsApp or Meta logos or wording that suggests affiliation. Do not promise a number of status views.

## Known limits
- Android 17+ and later: when you raise targetSdk to 37, Play's Contacts policy applies in full (declaration form). Android 18 plans tighter rules for bulk contact writes. Check the policy before that bump.
- Login tokens (app v53, website v79): the password is sent once at log in; every other call sends a login token. Database migration `20261117140000_login_tokens.sql` applied 17 Nov 2026.
