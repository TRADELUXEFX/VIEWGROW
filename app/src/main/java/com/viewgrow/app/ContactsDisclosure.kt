package com.viewgrow.app

import android.content.Context

// Google Play "prominent disclosure": shown BEFORE Android's own Contacts permission box, in plain words,
// and only continues to that box if the person taps Agree. It says what is read, what is written, and where it goes.
object ContactsDisclosure {
    const val TITLE = "ViewGrow needs access to your contacts"

    const val MESSAGE =
        "ViewGrow uses your phone's Contacts permission for two things:\n\n" +
        "\u2022 SAVE: it adds the contacts you receive from other ViewGrow members to your phone (tagged VGC), so they can see your WhatsApp status.\n" +
        "\u2022 READ: it checks the numbers already saved on your phone, only to avoid adding the same number twice, and to find and remove the contacts ViewGrow added when you tap Delete My Contacts.\n\n" +
        "Your own phone contacts are never uploaded, shared or sold. Your name and number are shared with other verified ViewGrow members, as you agreed when you signed up."

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
