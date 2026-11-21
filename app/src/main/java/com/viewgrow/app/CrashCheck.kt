package com.viewgrow.app

import com.google.firebase.crashlytics.FirebaseCrashlytics

// One practice crash report, so the Firebase Crashlytics page leaves its "Add SDK" screen and shows the dashboard.
// Only on the owner's own phone (logged in as 0810 870 9629), only once per phone, and nothing shows on screen:
// it is a "non-fatal" report, the app does not crash or close. Called from Home. Members never send it.
object CrashCheck {
    private const val OWNER_LAST10 = "8108709629"

    fun maybeSend() {
        if (Store.crashCheckSent) return
        if (Store.phone.filter { it.isDigit() }.takeLast(10) != OWNER_LAST10) return
        try {
            FirebaseCrashlytics.getInstance().recordException(
                RuntimeException("ViewGrow practice report: Crashlytics is set up. This is not a real crash.")
            )
            // Sent straight away, instead of waiting for the next time the app opens.
            FirebaseCrashlytics.getInstance().sendUnsentReports()
            Store.crashCheckSent = true
        } catch (e: Exception) {
            // Crashlytics not ready: try again next time Home opens.
        }
    }
}
