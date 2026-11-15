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

Deleting contacts
Profile, Delete My Contacts removes the contacts ViewGrow saved to your phone. It does not give them back to you or reset your count.

Changes and help
These terms may change. If you keep using the app after a change, you accept it. For help, message the admin on WhatsApp or email teamlimitlessaffliate@gmail.com."""

    private const val PRIVACY_TEXT = """What we keep
When you sign up we keep your name, your WhatsApp number and your password. We also keep the days you were verified and how many contacts you received. Your connection address is used to limit abuse, such as too many sign-ups from one place.

On your phone
The app keeps your number, name and password on your phone so you stay logged in. Log out removes the password and name from the app.

Your phone contacts
The app asks for permission to read and write contacts. It reads only the numbers already in your phone, to avoid saving the same contact twice. It never uploads your phone contacts. The contacts it saves stay on your phone, tagged VGC.

The contacts you receive
Other members' numbers are shared with you because they agreed to this when they signed up. In the same way, your name and number may be given to other verified members. Keep the contacts you receive private.

Notifications and background work
The app can show a message when contacts are saved, and it checks for new contacts in the background at the interval you choose in Profile. You can pause this in Profile.

Who can see your data
The admin can see account details to run the service. We do not sell your data. Your data is stored with our database provider, Supabase.

Your choices
You can log out, delete the contacts ViewGrow saved (Profile, Delete My Contacts), or turn off permissions in your phone's settings. To delete your account, message the admin on WhatsApp from your registered number, or email teamlimitlessaffliate@gmail.com with your registered number.

Changes
This policy may change. The latest version is always in the app and at viewgrow.site/privacy. Questions? Email teamlimitlessaffliate@gmail.com."""
}
