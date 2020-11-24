package org.whispersystems.signalservice.loki.database

import org.whispersystems.libsignal.state.PreKeyRecord

interface LokiPreKeyRecordDatabaseProtocol {

    fun getPreKeyRecord(publicKey: String): PreKeyRecord?
}
