package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.utilities.*
import org.session.libsignal.utilities.Hex
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupRatchet
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupRatchetCollectionType
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupSenderKey
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysDatabaseProtocol
import org.session.libsignal.service.loki.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.database.DatabaseFactory

class SharedSenderKeysDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), SharedSenderKeysDatabaseProtocol {

    companion object {
        // Shared
        public val closedGroupPublicKey = "closed_group_public_key"
        // Ratchets
        private val oldClosedGroupRatchetTable = "old_closed_group_ratchet_table"
        private val currentClosedGroupRatchetTable = "closed_group_ratchet_table"
        private val senderPublicKey = "sender_public_key"
        private val chainKey = "chain_key"
        private val keyIndex = "key_index"
        private val messageKeys = "message_keys"
        @JvmStatic val createOldClosedGroupRatchetTableCommand
            = "CREATE TABLE $oldClosedGroupRatchetTable ($closedGroupPublicKey STRING, $senderPublicKey STRING, $chainKey STRING, " +
                "$keyIndex INTEGER DEFAULT 0, $messageKeys TEXT, PRIMARY KEY ($closedGroupPublicKey, $senderPublicKey));"
        // Private keys
        @JvmStatic val createCurrentClosedGroupRatchetTableCommand
            = "CREATE TABLE $currentClosedGroupRatchetTable ($closedGroupPublicKey STRING, $senderPublicKey STRING, $chainKey STRING, " +
                "$keyIndex INTEGER DEFAULT 0, $messageKeys TEXT, PRIMARY KEY ($closedGroupPublicKey, $senderPublicKey));"
        // Private keys
        public val closedGroupPrivateKeyTable = "closed_group_private_key_table"
        public val closedGroupPrivateKey = "closed_group_private_key"
        @JvmStatic val createClosedGroupPrivateKeyTableCommand
            = "CREATE TABLE $closedGroupPrivateKeyTable ($closedGroupPublicKey STRING PRIMARY KEY, $closedGroupPrivateKey STRING);"
    }

    private fun getTable(collection: ClosedGroupRatchetCollectionType): String {
        return when (collection) {
            ClosedGroupRatchetCollectionType.Old -> oldClosedGroupRatchetTable
            ClosedGroupRatchetCollectionType.Current -> currentClosedGroupRatchetTable
        }
    }

    // region Ratchets & Sender Keys
    override fun getClosedGroupRatchet(groupPublicKey: String, senderPublicKey: String, collection: ClosedGroupRatchetCollectionType): ClosedGroupRatchet? {
        val database = databaseHelper.readableDatabase
        val query = "${Companion.closedGroupPublicKey} = ? AND ${Companion.senderPublicKey} = ?"
        return database.get(getTable(collection), query, arrayOf( groupPublicKey, senderPublicKey )) { cursor ->
            val chainKey = cursor.getString(Companion.chainKey)
            val keyIndex = cursor.getInt(Companion.keyIndex)
            val messageKeys = cursor.getString(Companion.messageKeys).split("-")
            ClosedGroupRatchet(chainKey, keyIndex, messageKeys)
        }
    }

    override fun setClosedGroupRatchet(groupPublicKey: String, senderPublicKey: String, ratchet: ClosedGroupRatchet, collection: ClosedGroupRatchetCollectionType) {
        val database = databaseHelper.writableDatabase
        val values = ContentValues()
        values.put(Companion.closedGroupPublicKey, groupPublicKey)
        values.put(Companion.senderPublicKey, senderPublicKey)
        values.put(Companion.chainKey, ratchet.chainKey)
        values.put(Companion.keyIndex, ratchet.keyIndex)
        values.put(Companion.messageKeys, ratchet.messageKeys.joinToString("-"))
        val query = "${Companion.closedGroupPublicKey} = ? AND ${Companion.senderPublicKey} = ?"
        database.insertOrUpdate(getTable(collection), values, query, arrayOf( groupPublicKey, senderPublicKey ))
    }

    override fun removeAllClosedGroupRatchets(groupPublicKey: String, collection: ClosedGroupRatchetCollectionType) {
        val database = databaseHelper.writableDatabase
        val query = "${Companion.closedGroupPublicKey} = ?"
        database.delete(getTable(collection), query, arrayOf( groupPublicKey ))
    }

    override fun getAllClosedGroupRatchets(groupPublicKey: String, collection: ClosedGroupRatchetCollectionType): Set<Pair<String, ClosedGroupRatchet>> {
        val database = databaseHelper.readableDatabase
        val query = "${Companion.closedGroupPublicKey} = ?"
        return database.getAll(getTable(collection), query, arrayOf( groupPublicKey )) { cursor ->
            val chainKey = cursor.getString(Companion.chainKey)
            val keyIndex = cursor.getInt(Companion.keyIndex)
            val messageKeys = cursor.getString(Companion.messageKeys).split("-")
            val senderPublicKey = cursor.getString(Companion.senderPublicKey)
            val ratchet = ClosedGroupRatchet(chainKey, keyIndex, messageKeys)
            Pair(senderPublicKey, ratchet)
        }.toSet()
    }

    override fun getAllClosedGroupSenderKeys(groupPublicKey: String, collection: ClosedGroupRatchetCollectionType): Set<ClosedGroupSenderKey> {
        return getAllClosedGroupRatchets(groupPublicKey, collection).map { pair ->
            val senderPublicKey = pair.first
            val ratchet = pair.second
            ClosedGroupSenderKey(Hex.fromStringCondensed(ratchet.chainKey), ratchet.keyIndex, Hex.fromStringCondensed(senderPublicKey))
        }.toSet()
    }
    // endregion

    // region Public & Private Keys
    override fun getClosedGroupPrivateKey(groupPublicKey: String): String? {
        val database = databaseHelper.readableDatabase
        val query = "${Companion.closedGroupPublicKey} = ?"
        return database.get(closedGroupPrivateKeyTable, query, arrayOf( groupPublicKey )) { cursor ->
            cursor.getString(Companion.closedGroupPrivateKey)
        }
    }

    override fun setClosedGroupPrivateKey(groupPublicKey: String, groupPrivateKey: String) {
        val database = databaseHelper.writableDatabase
        val values = ContentValues()
        values.put(Companion.closedGroupPublicKey, groupPublicKey)
        values.put(Companion.closedGroupPrivateKey, groupPrivateKey)
        val query = "${Companion.closedGroupPublicKey} = ?"
        database.insertOrUpdate(closedGroupPrivateKeyTable, values, query, arrayOf( groupPublicKey ))
    }

    override fun removeClosedGroupPrivateKey(groupPublicKey: String) {
        val database = databaseHelper.writableDatabase
        val query = "${Companion.closedGroupPublicKey} = ?"
        database.delete(closedGroupPrivateKeyTable, query, arrayOf( groupPublicKey ))
    }

    override fun getAllClosedGroupPublicKeys(): Set<String> {
        val database = databaseHelper.readableDatabase
        val result = mutableSetOf<String>()
        result.addAll(database.getAll(closedGroupPrivateKeyTable, null, null) { cursor ->
            cursor.getString(Companion.closedGroupPublicKey)
        }.filter {
            PublicKeyValidation.isValid(it)
        })
        result.addAll(DatabaseFactory.getLokiAPIDatabase(context).getAllClosedGroupPublicKeys())
        return result
    }
    // endregion

    override fun isSSKBasedClosedGroup(groupPublicKey: String): Boolean {
        if (!PublicKeyValidation.isValid(groupPublicKey)) { return false }
        return getAllClosedGroupPublicKeys().contains(groupPublicKey)
    }
    // endregion
}
