package com.viewgrow.app

// The in-app text pages: Terms and Conditions, Privacy Policy.
// Normally the app opens viewgrow.site/android/terms/ and /android/privacy/ (Web.kt). This text is only shown when the
// phone has no browser. It is made from the same source as those web pages, so the words are the same. Change both together.
object Pages {
    const val TERMS = 0
    const val PRIVACY = 1

    fun title(kind: Int): String = if (kind == PRIVACY) "Privacy Policy" else "Terms and Conditions"

    fun body(kind: Int): String = if (kind == PRIVACY) PRIVACY_TEXT else TERMS_TEXT

    private const val TERMS_TEXT = """About ViewGrow
ViewGrow helps you get more contacts saved on your phone, so more people can see your WhatsApp status. ViewGrow is not made by, or connected to, WhatsApp or Meta.

Your account
You sign up with your WhatsApp number and a password of 6 to 30 letters or numbers. Keep your password private. You are responsible for what happens on your account. One account per number, and one account per person.

How contacts work
To get contacts you reshare the admin's status and get verified. Your verified reshares decide how many contacts you get: the more you reshare, the more contacts you get. Contacts are released on the schedule the admin sets, and the app saves them to your phone when you open it or with the automatic timer. We cannot promise a number of status views.

Your contact limit and payments
Each account has a contact limit: the most contacts it can ever receive. You can raise it by 1,000, 3,000 or 5,000 with a one-time purchase through Google Play, in your local currency. It is not a subscription. Paying only raises your limit. It does not send you contacts and does not buy anyone's name or number. You still get contacts only by resharing, and your reshares decide how many you get.
Google Play handles payment and refunds under its own rules. If you paid and your limit did not go up, contact us and send your Google Play order number.

Keep contacts private
The contacts you receive are for you only. Do not share, sell, post or leak them. An account that leaks contacts will be banned.

Use contacts fairly
Only send messages people would be happy to get. Do not spam. Follow WhatsApp's rules and the laws where you live.

Bans
The admin may suspend or ban an account for leaking contacts, fake reshares, using more than one account, abuse, or breaking these terms. When an account is banned, the app removes the contacts ViewGrow saved to the phone.

Deleting contacts and your account
Profile, Delete My Contacts removes the contacts ViewGrow saved to your phone. It does not give them back to you or reset your count.
Profile, Delete my account deletes your account and its data for good. Your number stays on a deleted list, so it cannot log in or sign up again unless the admin removes it. See the Privacy Policy for what happens to your data.

Changes and help
ViewGrow may change, pause or stop at any time, and these terms may change. If you keep using the app after a change, you accept it. For help, use the chat button in the app, contact us or email teamlimitlessaffliate@gmail.com."""

    private const val PRIVACY_TEXT = """What we keep
When you sign up we keep your name, your WhatsApp number and your password. Your password is stored in scrambled form, so nobody can read it. We also keep:
• the days your reshares were verified, and how many contacts you received;
• which people were given to you, so nobody gets the same contact twice;
• the number of wrong password tries, and whether your account is locked or banned;
• your contact limit;
• the time you agreed to our terms;
• your Google Play purchases of limit increases (which one, the order number and the date);
• the last time your app checked in, so the admin can see who uses the app and send app reminders;
• your login sessions: a scrambled login code for each phone you log in on, and when it was last used. Logging out ends it.
When you sign up we also record your connection address (IP address) and the time. This is used to limit abuse, such as too many sign-ups from one place.

On your phone
So you stay logged in, the app keeps your number, your name and a login code (not your password) on your phone. The login code is encrypted on the phone. The app also keeps its own record of which contacts it saved each day, and the limit increases you bought, so you can see them in History and Purchase history. That record never leaves your phone. Log out removes the login code and name from the app.

Your phone contacts
The app asks for permission to read and write contacts. Before Android asks, step 1 of the setup shows you what it does with them:
• Save: it adds the contacts you receive to your phone, tagged VG. It also saves the admin's WhatsApp number once, as ViewGrow Admin, so you can see the admin's status, and any extra contacts the admin has chosen, with the names the admin gave them. If you delete one of these contacts, the app does not add it again.
• Read: it checks the numbers already in your phone, only on the phone itself, to avoid saving the same contact twice and to find the contacts ViewGrow added when you tap Delete My Contacts (or if the account is banned).
It never uploads, shares or sells your own phone contacts. If you say no, the app still opens, but it cannot save contacts.

The contacts you receive
The names and numbers you receive belong to other members, who agreed to share them when they signed up. In the same way, your name and number may be given to other verified members. Members get contacts by resharing, never by paying: nobody can buy your name or number. Keep the contacts you receive private. Do not share, sell or post them.

Notifications and background work
The app checks for new contacts in the background at the interval you choose in Profile (1, 6, 12 or 24 hours), and once more just after each 10pm release so new contacts arrive the same night. You can pause this in Profile. The app can show:
• a message when the admin has posted the day's status, so you can reshare it;
• messages from the admin to all members (for example, no release tonight), and reminders to members who have not reshared yet or whose contact limit is full;
• a silent signal at about 10pm that tells the app to save that night's contacts (nothing is shown);
• a message when a newer version of the app is on Google Play;
• a message when new contacts are saved (contacts saved at night are announced the next morning at 8am instead);
• a morning message when you were not on the previous night's list, with how many people joined, so you can reshare that day;
• a notification that opens the admin's WhatsApp chat, so you can get the status to reshare;
• one alert when you have used 80% of your contact limit, and one when it is full;
• a weekly summary on Sunday evening (nothing is sent in a week with no new contacts);
• a reminder after 3 days with no new check, or when a permission is off.
These messages are delivered by Firebase Cloud Messaging, a Google service. It gives the app an ID for this installation so the message can reach your phone. No name, number or other account detail is sent with the message.
You can turn notifications off in your phone settings.

Payments
You can raise your contact limit with a one-time purchase through Google Play. Google handles your payment details and we never see your card or account details. We receive a confirmation of the purchase (which pack, and the order number), check it with Google, and then raise your limit. The purchase only raises your limit. It does not buy anyone's name or number. Google has its own privacy policy for payments.

Who can see your data
The admin can see account details in order to run the service and help members. We do not sell your data. Your data is stored with our database provider, Supabase.

Your choices
• You can log out at any time.
• Profile, Delete My Contacts removes the contacts ViewGrow saved to your phone.
• Profile, Delete my account deletes your account (see below).
• You can turn off the app's permissions and notifications in your phone settings.

Delete your account
In the app: go to Profile and tap Delete my account. When you confirm, ViewGrow removes the contacts it saved to your phone, clears its records on your phone, logs you out and deletes your account, including your name, password, contact history and purchase records. You cannot get the account back. Contacts you added yourself are never touched.
If you cannot log in: message the admin from your registered WhatsApp number, or email teamlimitlessaffliate@gmail.com with your registered number. The admin confirms it is you and deletes the account. See Delete my account.
What we keep after deletion: only your phone number, on a deleted-accounts list, so the number cannot log in or sign up again. This stops people deleting and signing up again to get a fresh contact limit. You can ask the admin to remove your number from the list. Some records may also be kept where they are needed to prevent abuse (the sign-up connection records above) or where the law requires it (for example, payment records held by Google Play).

Changes
This policy may change. The latest version is always in the app and at viewgrow.site/android/privacy. Questions? Contact us or email teamlimitlessaffliate@gmail.com."""
}
