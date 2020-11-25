package org.session.libsignal.service.loki.database

import org.session.libsignal.libsignal.state.PreKeyBundle

interface LokiPreKeyBundleDatabaseProtocol {

    fun getPreKeyBundle(publicKey: String): PreKeyBundle?
    fun removePreKeyBundle(publicKey: String)
}
