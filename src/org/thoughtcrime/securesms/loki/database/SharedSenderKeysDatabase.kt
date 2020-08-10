package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.utilities.*
import org.thoughtcrime.securesms.util.Hex
import org.whispersystems.signalservice.loki.protocol.closedgroups.ClosedGroupRatchet
import org.whispersystems.signalservice.loki.protocol.closedgroups.ClosedGroupSenderKey
import org.whispersystems.signalservice.loki.protocol.closedgroups.SharedSenderKeysDatabaseProtocol

class SharedSenderKeysDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), SharedSenderKeysDatabaseProtocol {

    companion object {
        // Shared
        private val closedGroupPublicKey = "closed_group_public_key"
        // Ratchets
        private val closedGroupRatchetTable = "closed_group_ratchet_table"
        private val senderPublicKey = "sender_public_key"
        private val chainKey = "chain_key"
        private val keyIndex = "key_index"
        private val messageKeys = "message_keys"
        @JvmStatic val createClosedGroupRatchetTableCommand
            = "CREATE TABLE $closedGroupRatchetTable ($closedGroupPublicKey STRING, $senderPublicKey STRING, $chainKey STRING, " +
                "$keyIndex INTEGER DEFAULT 0, $messageKeys TEXT, PRIMARY KEY ($closedGroupPublicKey, $senderPublicKey));"
        // Private keys
        private val closedGroupPrivateKeyTable = "closed_group_private_key_table"
        private val closedGroupPrivateKey = "closed_group_private_key"
        @JvmStatic val createClosedGroupPrivateKeyTableCommand
            = "CREATE TABLE $closedGroupPrivateKeyTable ($closedGroupPublicKey STRING PRIMARY KEY, $closedGroupPrivateKey STRING);"
    }

    // region Ratchets & Sender Keys
    override fun getClosedGroupRatchet(groupPublicKey: String, senderPublicKey: String): ClosedGroupRatchet? {
        val database = databaseHelper.readableDatabase
        val query = "${Companion.closedGroupPublicKey} = ? AND ${Companion.senderPublicKey} = ?"
        return database.get(closedGroupRatchetTable, query, arrayOf( groupPublicKey, senderPublicKey )) { cursor ->
            val chainKey = cursor.getString(Companion.chainKey)
            val keyIndex = cursor.getInt(Companion.keyIndex)
            val messageKeys = cursor.getString(Companion.messageKeys).split(" - ")
            ClosedGroupRatchet(chainKey, keyIndex, messageKeys)
        }
    }

    override fun setClosedGroupRatchet(groupPublicKey: String, senderPublicKey: String, ratchet: ClosedGroupRatchet) {
        val database = databaseHelper.writableDatabase
        val values = ContentValues()
        values.put(Companion.closedGroupPublicKey, groupPublicKey)
        values.put(Companion.senderPublicKey, senderPublicKey)
        values.put(Companion.chainKey, ratchet.chainKey)
        values.put(Companion.keyIndex, ratchet.keyIndex)
        values.put(Companion.messageKeys, ratchet.messageKeys.joinToString(" - "))
        val query = "${Companion.closedGroupPublicKey} = ? AND ${Companion.senderPublicKey} = ?"
        database.insertOrUpdate(closedGroupRatchetTable, values, query, arrayOf( groupPublicKey, senderPublicKey ))
    }

    override fun removeAllClosedGroupRatchets(groupPublicKey: String) {
        val database = databaseHelper.writableDatabase
        database.delete(closedGroupRatchetTable, null, null)
    }

    override fun getAllClosedGroupSenderKeys(groupPublicKey: String): Set<ClosedGroupSenderKey> {
        val database = databaseHelper.readableDatabase
        val query = "${Companion.closedGroupPublicKey} = ? AND ${Companion.senderPublicKey} = ?"
        return database.getAll(closedGroupRatchetTable, query, arrayOf( groupPublicKey, senderPublicKey )) { cursor ->
            val chainKey = cursor.getString(Companion.chainKey)
            val keyIndex = cursor.getInt(Companion.keyIndex)
            val senderPublicKey = cursor.getString(Companion.senderPublicKey)
            ClosedGroupSenderKey(Hex.fromStringCondensed(chainKey), keyIndex, Hex.fromStringCondensed(senderPublicKey))
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
        return database.getAll(closedGroupPrivateKeyTable, null, null) { cursor ->
            cursor.getString(Companion.closedGroupPublicKey)
        }.toSet()
    }
    // endregion

    override fun isSSKBasedClosedGroup(groupPublicKey: String): Boolean {
        return getAllClosedGroupPublicKeys().contains(groupPublicKey)
    }
    // endregion
}
