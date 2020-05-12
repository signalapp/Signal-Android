package org.thoughtcrime.securesms.loki.protocol

import org.thoughtcrime.securesms.database.Address

object SyncMessagesProtocol {

    @JvmStatic
    fun shouldSyncReadReceipt(address: Address): Boolean {
        return !address.isGroup
    }
}