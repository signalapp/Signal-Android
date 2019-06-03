package org.thoughtcrime.securesms.loki

import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.whispersystems.signalservice.loki.api.LokiAPIDatabaseProtocol
import org.whispersystems.signalservice.loki.api.LokiAPITarget

class LokiAPIDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiAPIDatabaseProtocol {

    companion object {
        private val tableKey = "loki_api_database"
        private val swarmCacheKey = "swarm_cache"
        private val lastMessageHashValueKey = "last_message_hash_value"
        private val receivedMessageHashValuesKey = "received_message_hash_values"

        @JvmStatic
        val createTableCommand = "CREATE TABLE $tableKey ($swarmCacheKey TEXT, $lastMessageHashValueKey TEXT, $receivedMessageHashValuesKey TEXT);"
    }

    override fun getSwarmCache(): Map<String, List<LokiAPITarget>>? {
        return null
    }

    override fun setSwarmCache(newValue: Map<String, List<LokiAPITarget>>) {
        // TODO: Implement
    }

    override fun getLastMessageHashValue(target: LokiAPITarget): String? {
        return null
    }

    override fun setLastMessageHashValue(target: LokiAPITarget, newValue: String) {
        // TODO: Implement
    }

    override fun getReceivedMessageHashValues(): Set<String>? {
        return null
    }

    override fun setReceivedMessageHashValues(newValue: Set<String>) {
        // TODO: Implement
    }
}