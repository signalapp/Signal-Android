package org.thoughtcrime.securesms.groups

import androidx.annotation.VisibleForTesting
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Hex
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent

object OpenGroupMigrator {
    const val HTTP_PREFIX = "__loki_public_chat_group__!687474703a2f2f"
    private const val HTTPS_PREFIX = "__loki_public_chat_group__!68747470733a2f2f"
    const val OPEN_GET_SESSION_TRAILING_DOT_ENCODED = "6f70656e2e67657473657373696f6e2e6f72672e"
    const val LEGACY_GROUP_ENCODED_ID = "__loki_public_chat_group__!687474703a2f2f3131362e3230332e37302e33332e" // old IP based toByteArray()
    const val NEW_GROUP_ENCODED_ID = "__loki_public_chat_group__!68747470733a2f2f6f70656e2e67657473657373696f6e2e6f72672e" // new URL based toByteArray()

    data class OpenGroupMapping(val stub: String, val legacyThreadId: Long, val newThreadId: Long?)

    @VisibleForTesting
    fun Recipient.roomStub(): String? {
        if (!isOpenGroupRecipient) return null
        val serialized = address.serialize()
        if (serialized.startsWith(LEGACY_GROUP_ENCODED_ID)) {
            return serialized.replace(LEGACY_GROUP_ENCODED_ID,"")
        } else if (serialized.startsWith(NEW_GROUP_ENCODED_ID)) {
            return serialized.replace(NEW_GROUP_ENCODED_ID,"")
        } else if (serialized.startsWith(HTTP_PREFIX + OPEN_GET_SESSION_TRAILING_DOT_ENCODED)) {
            return serialized.replace(HTTP_PREFIX + OPEN_GET_SESSION_TRAILING_DOT_ENCODED, "")
        }
        return null
    }

    @VisibleForTesting
    fun getExistingMappings(legacy: List<ThreadRecord>, new: List<ThreadRecord>): List<OpenGroupMapping> {
        val legacyStubsMapping = legacy.mapNotNull { thread ->
            val stub = thread.recipient.roomStub()
            stub?.let { it to thread.threadId }
        }
        val newStubsMapping = new.mapNotNull { thread ->
            val stub = thread.recipient.roomStub()
            stub?.let { it to thread.threadId }
        }
        return legacyStubsMapping.map { (legacyEncodedStub, legacyId) ->
            // get 'new' open group thread ID if stubs match
            OpenGroupMapping(
                legacyEncodedStub,
                legacyId,
                newStubsMapping.firstOrNull { (newEncodedStub, _) -> newEncodedStub == legacyEncodedStub }?.second
            )
        }
    }

    @JvmStatic
    fun migrate(databaseComponent: DatabaseComponent) {
        // migrate thread db
        val threadDb = databaseComponent.threadDatabase()

        val legacyOpenGroups = threadDb.legacyOxenOpenGroups
        val httpBasedNewGroups = threadDb.httpOxenOpenGroups
        if (legacyOpenGroups.isEmpty() && httpBasedNewGroups.isEmpty()) return // no need to migrate

        val newOpenGroups = threadDb.httpsOxenOpenGroups
        val firstStepMigration = getExistingMappings(legacyOpenGroups, newOpenGroups)

        val secondStepMigration = getExistingMappings(httpBasedNewGroups, newOpenGroups)

        val groupDb = databaseComponent.groupDatabase()
        val lokiApiDb = databaseComponent.lokiAPIDatabase()
        val smsDb = databaseComponent.smsDatabase()
        val mmsDb = databaseComponent.mmsDatabase()
        val lokiMessageDatabase = databaseComponent.lokiMessageDatabase()
        val lokiThreadDatabase = databaseComponent.lokiThreadDatabase()

        firstStepMigration.forEach { (stub, old, new) ->
            val legacyEncodedGroupId = LEGACY_GROUP_ENCODED_ID+stub
            if (new == null) {
                val newEncodedGroupId = NEW_GROUP_ENCODED_ID+stub
                // migrate thread and group encoded values
                threadDb.migrateEncodedGroup(old, newEncodedGroupId)
                groupDb.migrateEncodedGroup(legacyEncodedGroupId, newEncodedGroupId)
                // migrate Loki API DB values
                // decode the hex to bytes, decode byte array to string i.e. "oxen" or "session"
                val decodedStub = Hex.fromStringCondensed(stub).decodeToString()
                val legacyLokiServerId = "${OpenGroupApi.legacyDefaultServer}.$decodedStub"
                val newLokiServerId = "${OpenGroupApi.defaultServer}.$decodedStub"
                lokiApiDb.migrateLegacyOpenGroup(legacyLokiServerId, newLokiServerId)
                // migrate loki thread db server info
                val oldServerInfo = lokiThreadDatabase.getOpenGroupChat(old)
                val newServerInfo = oldServerInfo!!.copy(server = OpenGroupApi.defaultServer, id = newLokiServerId)
                lokiThreadDatabase.setOpenGroupChat(newServerInfo, old)
            } else {
                // has a legacy and a new one
                // migrate SMS and MMS tables
                smsDb.migrateThreadId(old, new)
                mmsDb.migrateThreadId(old, new)
                lokiMessageDatabase.migrateThreadId(old, new)
                // delete group for legacy ID
                groupDb.delete(legacyEncodedGroupId)
                // delete thread for legacy ID
                threadDb.deleteConversation(old)
                lokiThreadDatabase.removeOpenGroupChat(old)
            }
            // maybe migrate jobs here
        }

        secondStepMigration.forEach { (stub, old, new) ->
            val legacyEncodedGroupId = HTTP_PREFIX + OPEN_GET_SESSION_TRAILING_DOT_ENCODED + stub
            if (new == null) {
                val newEncodedGroupId = NEW_GROUP_ENCODED_ID+stub
                // migrate thread and group encoded values
                threadDb.migrateEncodedGroup(old, newEncodedGroupId)
                groupDb.migrateEncodedGroup(legacyEncodedGroupId, newEncodedGroupId)
                // migrate Loki API DB values
                // decode the hex to bytes, decode byte array to string i.e. "oxen" or "session"
                val decodedStub = Hex.fromStringCondensed(stub).decodeToString()
                val legacyLokiServerId = "${OpenGroupApi.httpDefaultServer}.$decodedStub"
                val newLokiServerId = "${OpenGroupApi.defaultServer}.$decodedStub"
                lokiApiDb.migrateLegacyOpenGroup(legacyLokiServerId, newLokiServerId)
                // migrate loki thread db server info
                val oldServerInfo = lokiThreadDatabase.getOpenGroupChat(old)
                val newServerInfo = oldServerInfo!!.copy(server = OpenGroupApi.defaultServer, id = newLokiServerId)
                lokiThreadDatabase.setOpenGroupChat(newServerInfo, old)
            } else {
                // has a legacy and a new one
                // migrate SMS and MMS tables
                smsDb.migrateThreadId(old, new)
                mmsDb.migrateThreadId(old, new)
                lokiMessageDatabase.migrateThreadId(old, new)
                // delete group for legacy ID
                groupDb.delete(legacyEncodedGroupId)
                // delete thread for legacy ID
                threadDb.deleteConversation(old)
                lokiThreadDatabase.removeOpenGroupChat(old)
            }
            // maybe migrate jobs here
        }

    }
}