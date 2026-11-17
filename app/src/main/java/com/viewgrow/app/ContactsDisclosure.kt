package com.viewgrow.app

import android.content.Context

// Google Play "prominent disclosure": shown BEFORE Android's own Contacts permission box, in plain words,
// and only continues to that box if the person taps Agree. It says what is read, what is written, and where it goes.
object ContactsDisclosure {
    const val TITLE = "ViewGrow needs access to your contacts"

    // The same words appear on Step 1 of the setup (PermissionsFragment) and in this pop-up (used when Contacts is
    // turned on later, from Home), so both say exactly the same thing.
    const val SAVE = "Adds the contacts you receive from other members to your phone (tagged VG), so they can see your WhatsApp status. It also saves the admin\u2019s number once."
    const val READ = "Checks the numbers already on your phone, so none is added twice, and finds ViewGrow\u2019s contacts when you tap Delete My Contacts."
    const val PRIVACY = "Your own contacts are never uploaded, shared or sold. Your name and number are shared with verified members, as you agreed at sign up."

    // Step 1 of the setup says the same thing in two short lines (PermissionsFragment).
    const val STEP = "ViewGrow saves the contacts you receive, and the admin\u2019s number, to your phone. It only reads your contacts to skip duplicates and remove its own."
    const val STEP_PRIVACY = "Your contacts never leave your phone."

    const val MESSAGE =
        "ViewGrow uses your phone's Contacts permission for two things:\n\n" +
        "\u2022 SAVE: " + SAVE + "\n" +
        "\u2022 READ: " + READ + "\n\n" + PRIVACY

    // [onAgree] runs only if the person taps Agree; [onDecline] runs if they tap No thanks or dismiss the pop-up.
    fun show(ctx: Context, onDecline: () -> Unit = {}, onAgree: () -> Unit) {
        ConfirmDialog.show(
            ctx,
            title = TITLE,
            message = MESSAGE,
            confirmLabel = "Agree",
            cancelLabel = "No thanks",
            danger = false,
            onConfirm = onAgree,
            onCancel = onDecline
        )
    }
}
