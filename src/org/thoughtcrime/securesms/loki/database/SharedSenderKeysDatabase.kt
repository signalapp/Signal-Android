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
        private val closedGroupRatchetsTable = "closed_group_ratchets"
        private val senderPublicKey = "sender_public_key"
        private val chainKey = "chain_key"
        private val keyIndex = "key_index"
        private val messageKeys = "message_keys"
        @JvmStatic val createClosedGroupRatchetsTableCommand
            = "CREATE TABLE $closedGroupRatchetsTable ($closedGroupPublicKey STRING, $senderPublicKey STRING, $chainKey STRING, " +
                "$keyIndex INTEGER DEFAULT 0, $messageKeys STRING, PRIMARY KEY ($closedGroupPublicKey, $senderPublicKey));"
        // Private keys
        private val closedGroupPrivateKeysTable = "closed_group_private_keys"
        private val closedGroupPrivateKey = "closed_group_private_key"
        @JvmStatic val createClosedGroupPrivateKeysTableCommand
            = "CREATE TABLE $closedGroupPrivateKeysTable ($closedGroupPublicKey STRING PRIMARY KEY, $closedGroupPrivateKey STRING);"
    }

    // region Ratchets & Sender Keys
    override fun getClosedGroupRatchet(groupPublicKey: String, senderPublicKey: String): ClosedGroupRatchet? {
        val database = databaseHelper.readableDatabase
        val query = "${Companion.closedGroupPublicKey} = ? AND ${Companion.senderPublicKey} = ?"
        return database.get(closedGroupRatchetsTable, query, arrayOf( groupPublicKey, senderPublicKey )) { cursor ->
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
        database.insertOrUpdate(closedGroupRatchetsTable, values, query, arrayOf( groupPublicKey, senderPublicKey ))
    }

    override fun removeAllClosedGroupRatchets(groupPublicKey: String) {
        val database = databaseHelper.writableDatabase
        database.delete(closedGroupRatchetsTable, null, null)
    }

    override fun getAllClosedGroupSenderKeys(groupPublicKey: String): Set<ClosedGroupSenderKey> {
        val database = databaseHelper.readableDatabase
        val query = "${Companion.closedGroupPublicKey} = ? AND ${Companion.senderPublicKey} = ?"
        return database.getAll(closedGroupRatchetsTable, query, arrayOf( groupPublicKey, senderPublicKey )) { cursor ->
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
        return database.get(closedGroupPrivateKeysTable, query, arrayOf( groupPublicKey )) { cursor ->
            cursor.getString(Companion.closedGroupPrivateKey)
        }
    }

    override fun setClosedGroupPrivateKey(groupPublicKey: String, groupPrivateKey: String) {
        val database = databaseHelper.writableDatabase
        val values = ContentValues()
        values.put(Companion.closedGroupPublicKey, groupPublicKey)
        values.put(Companion.closedGroupPrivateKey, groupPrivateKey)
        val query = "${Companion.closedGroupPublicKey} = ?"
        database.insertOrUpdate(closedGroupPrivateKeysTable, values, query, arrayOf( groupPublicKey ))
    }

    override fun removeClosedGroupPrivateKey(groupPublicKey: String) {
        val database = databaseHelper.writableDatabase
        val query = "${Companion.closedGroupPublicKey} = ?"
        database.delete(closedGroupPrivateKeysTable, query, arrayOf( groupPublicKey ))
    }

    override fun getAllClosedGroupPublicKeys(): Set<String> {
        val database = databaseHelper.readableDatabase
        return database.getAll(closedGroupPrivateKeysTable, null, null) { cursor ->
            cursor.getString(Companion.closedGroupPublicKey)
        }.toSet()
    }
    // endregion
}
