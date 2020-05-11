package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import android.util.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.jobs.CleanPreKeysJob
import org.thoughtcrime.securesms.util.TextSecurePreferences

object SessionManagementProtocol {

    @JvmStatic
    fun refreshSignedPreKey(context: Context) {
        if (TextSecurePreferences.isSignedPreKeyRegistered(context)) {
            Log.d("Loki", "Skipping signed pre key refresh; using existing signed pre key.")
        } else {
            Log.d("Loki", "Signed pre key refreshed successfully.")
            val identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context)
            PreKeyUtil.generateSignedPreKey(context, identityKeyPair, true)
            TextSecurePreferences.setSignedPreKeyRegistered(context, true)
            ApplicationContext.getInstance(context).jobManager.add(CleanPreKeysJob())
        }
    }
}