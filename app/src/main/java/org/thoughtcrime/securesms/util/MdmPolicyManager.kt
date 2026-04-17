// DEPRECATED — see proposals/fips-discovery-2026-04-17.md

package org.thoughtcrime.securesms.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Bundle
import android.util.Log

/**
 * Manages and provides access to the application's managed configuration policies
 * pushed by a Mobile Device Management (MDM) solution.
 *
 * This singleton object is initialized once on application startup. It reads the
 * current MDM restrictions, provides convenient accessors for policy checks, and
 * listens for broadcast intents to update policies dynamically if they are changed
 * by an administrator while the app is running.
 */
object MdmPolicyManager {

    private const val TAG = "MdmPolicyManager"

    // Keys must exactly match the `android:key` attributes in `app_restrictions.xml`.
    private const val KEY_FIPS_ONLY_MODE = "FipsOnlyMode"
    private const val KEY_ALLOW_CHAT_BACKUPS = "AllowChatBackups"
    private const val KEY_FORCE_SCREEN_SECURITY = "ForceScreenSecurity"

    // In-memory cache for the policy values.
    @Volatile
    private var isFipsOnlyMode: Boolean = false
    @Volatile
    private var areBackupsAllowed: Boolean = true
    @Volatile
    private var isScreenSecurityForced: Boolean = true

    private var isInitialized = false

    /**
     * Initializes the policy manager. Should be called once from the main Application class.
     * It performs an initial load of the policies and registers a receiver for updates.
     *
     * @param context The application context.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.w(TAG, "Policy manager already initialized.")
            return
        }

        loadRestrictions(context)

        val intentFilter = IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.i(TAG, "Received application restrictions changed broadcast. Reloading policies.")
                loadRestrictions(context)
            }
        }
        context.registerReceiver(receiver, intentFilter)
        
        isInitialized = true
        Log.i(TAG, "MDM Policy Manager initialized. Current state: FIPS-Only=${isFipsOnlyMode}, Backups-Allowed=${areBackupsAllowed}")
    }

    /**
     * Checks if the application is configured to run in a strict FIPS-only mode.
     *
     * @return `true` if FIPS-only mode is enforced, `false` otherwise.
     */
    fun isFipsOnlyMode(): Boolean {
        return isFipsOnlyMode
    }

    /**
     * Checks if the user is permitted to create chat backups.
     *
     * @return `true` if backups are allowed, `false` if they are disabled by policy.
     */
    fun areBackupsAllowed(): Boolean {
        return areBackupsAllowed
    }

    /**
     * Checks if the application should enforce screen security (disable screenshots).
     *
     * @return `true` if screen security should be forced on, `false` otherwise.
     */
    fun isScreenSecurityForced(): Boolean {
        return isScreenSecurityForced
    }

    /**
     * Fetches the latest restrictions from the system's RestrictionsManager and updates
     * the in-memory cache.
     */
    private fun loadRestrictions(context: Context) {
        val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        val appRestrictions: Bundle = restrictionsManager.applicationRestrictions

        // If no MDM is configured, the bundle will be empty. We use the default values
        // specified in the app_restrictions.xml file in that case.
        isFipsOnlyMode = appRestrictions.getBoolean(KEY_FIPS_ONLY_MODE, false)
        areBackupsAllowed = appRestrictions.getBoolean(KEY_ALLOW_CHAT_BACKUPS, true)
        isScreenSecurityForced = appRestrictions.getBoolean(KEY_FORCE_SCREEN_SECURITY, true)

        Log.d(TAG, "Policies loaded: FIPS-Only=${isFipsOnlyMode}, Backups-Allowed=${areBackupsAllowed}, Screen-Security-Forced=${isScreenSecurityForced}")
    }
}
