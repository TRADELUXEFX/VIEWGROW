package com.viewgrow.app

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

// "Rate this app": Google Play's own rating pop-up (Play In-App Review library). Google draws it, not us.
// Asked once per phone, right after a save of 10 or more contacts, so members are asked at a happy moment.
// Google decides whether it really shows (it limits how often), and builds not installed from Google Play never see it.
object Rating {
    private const val MIN_SAVED = 10

    fun maybeAsk(activity: Activity, saved: Int) {
        Store.init(activity)
        if (saved < MIN_SAVED || Store.reviewAsked) return
        Store.reviewAsked = true
        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { req ->
                if (req.isSuccessful && !activity.isFinishing && !activity.isDestroyed) {
                    manager.launchReviewFlow(activity, req.result)
                }
            }
        } catch (e: Exception) {
            // Google Play not available on this phone: nothing to do.
        }
    }
}
