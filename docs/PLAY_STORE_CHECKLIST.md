# ViewGrow: Play Console checklist

## Before the first upload
1. Run `supabase/migrations/20261116140000_deleted_numbers.sql` once in the Supabase SQL Editor, and add the delete of the member's own rows to `web_delete_account` (Play needs the data really deleted). To allow a number back: `delete from public.deleted_numbers where public.vg_phone_key(phone) = public.vg_phone_key('THE_NUMBER');`
2. Put the same Privacy Policy text on `https://viewgrow.site/privacy/` and a Delete account page on `https://viewgrow.site/delete-account/` (what is removed, how to ask, how long it takes).
3. **Server-side purchase check.** `web_verify_purchase` is not in this repo. It must call the Google Play Developer API (`purchases.products.get`) with a service account, confirm the product, `purchaseState = 0`, and that the token was never used before, and only then raise the limit. Without this a fake token can raise a limit.
4. GitHub secrets for the upload key: `KEYSTORE_FILE` (path), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Build with `gradle :app:bundleRelease` and upload `app-release.aab`. Turn on Play App Signing.
5. Create the three in-app products `contacts_1000`, `contacts_3000`, `contacts_5000` (consumable, active, with prices).
6. Install the release build on Android 14, 15 and 16 phones. Test: sign up, contacts permission (Agree and No thanks), sync, notifications off, delete contacts, **delete account**, a test purchase, large font size.

## Declarations
- **Permissions declaration (Contacts):** core function = saving contacts received from other members so they can view the member's WhatsApp status. WRITE_CONTACTS adds them; READ_CONTACTS only dedupes numbers on the phone and finds the app's own VGC contacts for removal. Nothing is uploaded. The Contact Picker cannot write contacts or scan for duplicates. This is the item most likely to get a policy question; answer it truthfully and keep the in-app disclosure identical to the listing.
- **Data safety:** collected and shared with the service provider (Supabase): Name, Phone number, Contacts (numbers of other members delivered to the user; the user's own contacts are NOT uploaded), Purchase history, IP address (abuse limiting), phone number kept on a deleted-accounts list after deletion (abuse prevention; say so in the Privacy Policy), Device or other IDs: none. Data encrypted in transit: yes (HTTPS only). Deletion request: yes, in app and at the web URL.
- **Target audience:** 18+. **Content rating:** complete the questionnaire. **Ads:** none.
- **App access:** give reviewers a test number and password.
- Listing: do not use WhatsApp or Meta logos or wording that suggests affiliation. Do not promise a number of status views.

## Known limits
- Android 17+ and later: when you raise targetSdk to 37, Play's Contacts policy applies in full (declaration form). Android 18 plans tighter rules for bulk contact writes. Check the policy before that bump.
- The app still sends the member's password to the database on every call (the website works the same way). Moving to a login token is a server change.
