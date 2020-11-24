package org.whispersystems.signalservice.loki.database

import org.whispersystems.libsignal.state.PreKeyBundle

interface LokiPreKeyBundleDatabaseProtocol {

    fun getPreKeyBundle(publicKey: String): PreKeyBundle?
    fun removePreKeyBundle(publicKey: String)
}
