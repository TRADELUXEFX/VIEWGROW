package com.viewgrow.app

import android.content.Context

// Google Play "prominent disclosure": shown BEFORE Android's own Contacts permission box, in plain words,
// and only continues to that box if the person taps Agree. It says what is read, what is written, and where it goes.
object ContactsDisclosure {
    const val TITLE = "ViewGrow needs access to your contacts"

    // The same words appear on Step 1 of the setup (PermissionsFragment) and in this pop-up (used when Contacts is
    // turned on later, from Home), so both say exactly the same thing.
    const val SAVE = "Adds the contacts you receive from other members to your phone (tagged VG), so they can see your WhatsApp status. It also saves the admin\u2019s number, and a few extra contacts the admin chose, once."
    const val READ = "Checks the numbers already on your phone, so none is added twice, and finds ViewGrow\u2019s contacts when you tap Delete My Contacts."
    const val PRIVACY = "Your own contacts are never uploaded, shared or sold. Your name and number are shared with verified members, as you agreed at sign up."

    // Step 1 of the setup (PermissionsFragment): a "why we ask" line, three short rows and one privacy line.
    const val STEP_WHY_LABEL = "Why we ask:"
    const val STEP_WHY = " for other members to see your status, their phone must save your number, and yours must save theirs."
    const val STEP_ROW_1 = "Saves new contacts, the admin\u2019s number and a few contacts the admin chose, labelled VG"
    const val STEP_ROW_2 = "Checks your phone so nobody is saved twice"
    const val STEP_ROW_3 = "Lets you delete all VG contacts any time"
    const val STEP_PRIVACY = "Your own contacts are never uploaded and never deleted from your phone."

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
