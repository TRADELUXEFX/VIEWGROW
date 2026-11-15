package com.viewgrow.app

// The in-app text pages: Terms and Conditions, Privacy Policy.
object Pages {
    const val TERMS = 0
    const val PRIVACY = 1

    fun title(kind: Int): String = if (kind == PRIVACY) "Privacy Policy" else "Terms and Conditions"

    fun body(kind: Int): String = if (kind == PRIVACY) PRIVACY_TEXT else TERMS_TEXT

    private const val TERMS_TEXT = """About ViewGrow
ViewGrow helps you get more contacts saved on your phone, so more people can see your WhatsApp status. ViewGrow is not made by, or connected to, WhatsApp or Meta.

Your account
You sign up with your WhatsApp number and a password of 6 to 30 letters or numbers. Keep your password private. You are responsible for what happens on your account. One account per number.

How contacts work
To get contacts you reshare the admin's status and get verified. Contacts are released on the schedule the admin sets, and you save them to your phone when you open the app or with the automatic timer. The more you reshare, the more contacts you get. Each account has a contact limit. You can raise it with a one-time purchase through Google Play. A bigger limit gives you room for more contacts, but your reshares decide how many you get.

Keep contacts private
The contacts you receive are for you only. Do not share, sell, post or leak them or the files they come in. An account that leaks contacts will be banned.

Use contacts fairly
Only send messages people would be happy to get. Do not spam. Follow WhatsApp's rules and the laws where you live.

Bans
The admin may suspend or ban an account for leaking contacts, fake reshares, abuse, or breaking these terms.

No guarantees
We cannot promise a number of status views. ViewGrow may change, pause or stop at any time.

Deleting contacts and your account
Profile, Delete My Contacts removes the contacts ViewGrow saved to your phone. It does not give them back to you or reset your count. Profile, Delete my account deletes your account and its data for good. Your number stays on a deleted list, so it cannot log in or sign up again unless the admin removes it. After that you sign up again with a new account and a new contact limit.

Changes and help
These terms may change. If you keep using the app after a change, you accept it. For help, message the admin on WhatsApp or email teamlimitlessaffliate@gmail.com."""

    private const val PRIVACY_TEXT = """What we keep
When you sign up we keep your name, your WhatsApp number and your password. We also keep the days you were verified, how many contacts you received, your contact limit, and your Google Play purchases of contact packs (product, order number and date; Google Play handles your payment details, we never see your card). Your connection address (IP address) is used to limit abuse, such as too many sign-ups from one place.

On your phone
The app keeps your number, name and password on your phone so you stay logged in. Log out removes the password and name from the app.

Your phone contacts
The app asks for permission to read and write contacts. It writes the contacts you receive to your phone. It reads the numbers already in your phone only on the phone itself, to avoid saving the same contact twice and to find the contacts ViewGrow added when you tap Delete My Contacts. It never uploads, shares or sells your own phone contacts. The contacts it saves stay on your phone, tagged VGC. If you say no to the permission, the app still opens, but it cannot save contacts.

The contacts you receive
Other members' numbers are shared with you because they agreed to this when they signed up. In the same way, your name and number may be given to other verified members. Keep the contacts you receive private.

Notifications and background work
The app can show a message when contacts are saved, and it checks for new contacts in the background at the interval you choose in Profile. You can pause this in Profile.

Who can see your data
The admin can see account details to run the service. We do not sell your data. Your data is stored with our database provider, Supabase.

Your choices
You can log out, delete the contacts ViewGrow saved (Profile, Delete My Contacts), or turn off permissions in your phone's settings.

Delete your account: in the app, go to Profile, Delete my account. When you confirm, ViewGrow removes the contacts it saved to your phone, logs you out and deletes your account, including your name, password, contact history and purchase records. You cannot get the account back. We keep only your phone number on a deleted-accounts list, so the number cannot log in or sign up again; this stops people deleting and coming back to reset their limit. You can ask the admin to remove your number from that list. You can also delete your account at viewgrow.site/delete-account, or by emailing teamlimitlessaffliate@gmail.com from your registered number. Some records may be kept where the law requires it (for example, payment records held by Google Play).

Changes
This policy may change. The latest version is always in the app and at viewgrow.site/privacy. Questions? Email teamlimitlessaffliate@gmail.com."""
}
