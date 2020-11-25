package org.session.libsignal.service.loki.database

import org.session.libsignal.libsignal.state.PreKeyRecord

interface LokiPreKeyRecordDatabaseProtocol {

    fun getPreKeyRecord(publicKey: String): PreKeyRecord?
}
