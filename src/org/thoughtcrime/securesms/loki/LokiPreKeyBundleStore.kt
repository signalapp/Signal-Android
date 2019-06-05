package org.thoughtcrime.securesms.loki

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.signalservice.loki.messaging.LokiPreKeyBundleStoreProtocol

class LokiPreKeyBundleStore(val context: Context) : LokiPreKeyBundleStoreProtocol {

    companion object {
        private val lock = Object()
    }

    override fun getPreKeyBundle(pubKey: String): PreKeyBundle? {
        synchronized(lock) {
            return DatabaseFactory.getLokiPreKeyBundleDatabase(context).getPreKeyBundle(pubKey)
        }
    }

    override fun removePreKeyBundle(pubKey: String) {
        synchronized(lock) {
            DatabaseFactory.getLokiPreKeyBundleDatabase(context).removePreKeyBundle(pubKey)
        }
    }
}