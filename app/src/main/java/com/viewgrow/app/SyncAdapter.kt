package com.viewgrow.app

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.provider.ContactsContract
import android.util.Log

// An EXTRA trigger for the automatic sync. Nothing else changes.
//
// The app adds a small login-free account called "ViewGrow" (Settings > Accounts) and asks Android
// to run a periodic sync for it. Android's own sync scheduler then wakes ViewGrowSyncAdapter, which
// runs the same BackgroundSync as the WorkManager timer. Contacts are still saved loose on the
// phone exactly as before (no account), so nothing moves and WhatsApp sees them as it always did.
//
// Why: some phone brands (Tecno, Infinix, itel ...) silence the app's own timer but treat account
// syncing more gently. If one trigger is blocked the other may still run. Two triggers firing
// together is safe: Sync.runBlocking lets only one run at a time.
//
// SyncScheduler turns it on and off with the timer: on while logged in and not paused, off when
// paused or logged out.
object SyncAdapterSetup {
    const val ACCOUNT_TYPE = "com.viewgrow.app.account"
    private const val ACCOUNT_NAME = "ViewGrow"
    private const val TAG = "VGSync"
    private val AUTHORITY = ContactsContract.AUTHORITY

    private fun account() = Account(ACCOUNT_NAME, ACCOUNT_TYPE)

    // Creates the account (once) and the periodic sync. Safe to call on every app open.
    fun enable(context: Context) {
        try {
            Store.init(context)
            val acc = account()
            // True only when the account did not exist yet.
            val created = AccountManager.get(context).addAccountExplicitly(acc, null, null)
            if (created) {
                ContentResolver.setIsSyncable(acc, AUTHORITY, 1)
                // Only on creation: if the user later switches Auto-sync off for this account in
                // Settings, that choice is respected.
                ContentResolver.setSyncAutomatically(acc, AUTHORITY, true)
            }
            val hours = if (Store.syncHours in intArrayOf(1, 6, 12, 24)) Store.syncHours else 24
            if (created || Store.adapterHours != hours) {
                ContentResolver.addPeriodicSync(acc, AUTHORITY, Bundle.EMPTY, hours * 3600L)
                Store.adapterHours = hours
            }
        } catch (e: Exception) {
            Log.w(TAG, "sync adapter enable failed", e)
        }
    }

    // Removes the account (and with it the periodic sync). Safe to call when it does not exist.
    fun disable(context: Context) {
        try {
            Store.init(context)
            AccountManager.get(context).removeAccountExplicitly(account())
            Store.adapterHours = 0
        } catch (e: Exception) {
            Log.w(TAG, "sync adapter disable failed", e)
        }
    }
}

// Runs when Android wakes the "ViewGrow" account. Same automatic sync as the timer.
class ViewGrowSyncAdapter(context: Context) : AbstractThreadedSyncAdapter(context, true) {

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: android.content.SyncResult
    ) {
        val ctx = context.applicationContext
        Store.init(ctx)
        if (!Store.loggedIn) {
            SyncAdapterSetup.disable(ctx)
            return
        }
        // Could not reach the database: tell Android so it retries with its own back-off.
        if (BackgroundSync.run(ctx) == false) syncResult.stats.numIoExceptions++
    }
}

class ViewGrowSyncService : Service() {
    override fun onCreate() {
        synchronized(lock) {
            if (adapter == null) adapter = ViewGrowSyncAdapter(applicationContext)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = adapter?.syncAdapterBinder

    companion object {
        private val lock = Any()
        private var adapter: ViewGrowSyncAdapter? = null
    }
}

// Android needs an "authenticator" for any account type. This one only exists so the "ViewGrow"
// account can exist: the app adds it itself, there is no sign-in, and adding it by hand from
// Settings is refused.
class ViewGrowAuthenticator(context: Context) : AbstractAccountAuthenticator(context) {
    override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?): Bundle? = null

    override fun addAccount(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
        authTokenType: String?,
        requiredFeatures: Array<String>?,
        options: Bundle?
    ): Bundle? = Bundle().apply {
        putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
        putString(AccountManager.KEY_ERROR_MESSAGE, "ViewGrow adds this account by itself.")
    }

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse?, account: Account?, options: Bundle?
    ): Bundle? = null

    override fun getAuthToken(
        response: AccountAuthenticatorResponse?, account: Account?, authTokenType: String?, options: Bundle?
    ): Bundle? = null

    override fun getAuthTokenLabel(authTokenType: String?): String? = null

    override fun updateCredentials(
        response: AccountAuthenticatorResponse?, account: Account?, authTokenType: String?, options: Bundle?
    ): Bundle? = null

    override fun hasFeatures(
        response: AccountAuthenticatorResponse?, account: Account?, features: Array<String>?
    ): Bundle? = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
}

class ViewGrowAccountService : Service() {
    private var authenticator: ViewGrowAuthenticator? = null

    override fun onCreate() {
        authenticator = ViewGrowAuthenticator(this)
    }

    override fun onBind(intent: Intent?): IBinder? = authenticator?.iBinder
}
