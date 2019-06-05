package org.thoughtcrime.securesms.loki

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.signalservice.loki.messaging.LokiPreKeyBundleStore

class LokiPreKeyBundleStore(val context: Context) : LokiPreKeyBundleStoreProtocol {

    companion object {
        val FILE_LOCK = Object()
    }

    override fun getPreKeyBundle(pubKey: String): PreKeyBundle? {
        synchronized (FILE_LOCK) {
            return DatabaseFactory.getLokiPreKeyBundleDatabase(context).getPreKeyBundle(pubKey)
        }
    }

    override fun removePreKeyBundle(pubKey: String) {
        synchronized (FILE_LOCK) {
            DatabaseFactory.getLokiPreKeyBundleDatabase(context).removePreKeyBundle(pubKey)
        }
    }
}