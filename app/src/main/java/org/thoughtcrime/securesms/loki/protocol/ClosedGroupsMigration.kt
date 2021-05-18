package org.thoughtcrime.securesms.loki.protocol

import android.content.ContentValues
import org.thoughtcrime.securesms.loki.database.LokiAPIDatabase
import org.thoughtcrime.securesms.loki.utilities.get
import org.thoughtcrime.securesms.loki.utilities.getAll
import org.thoughtcrime.securesms.loki.utilities.getString
import org.thoughtcrime.securesms.loki.utilities.insertOrUpdate
import org.session.libsignal.utilities.Hex
import org.session.libsignal.crypto.ecc.DjbECPrivateKey
import org.session.libsignal.crypto.ecc.DjbECPublicKey
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.utilities.PublicKeyValidation
import org.session.libsignal.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.toHexString
import java.util.*

object ClosedGroupsMigration {

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


fun perform(database: net.sqlcipher.database.SQLiteDatabase) {
        val publicKeys = database.getAll(closedGroupPrivateKeyTable, null, null) { cursor ->
            cursor.getString(closedGroupPublicKey)
        }.filter {
            PublicKeyValidation.isValid(it)
        }
        val keyPairs = mutableListOf<ECKeyPair>()
        for (publicKey in publicKeys) {
            val query = "${closedGroupPublicKey} = ?"
            val privateKey = database.get(closedGroupPrivateKeyTable, query, arrayOf( publicKey )) { cursor ->
                cursor.getString(closedGroupPrivateKey)
            }
            val keyPair = ECKeyPair(DjbECPublicKey(Hex.fromStringCondensed(publicKey.removing05PrefixIfNeeded())), DjbECPrivateKey(Hex.fromStringCondensed(privateKey)))
            keyPairs.add(keyPair)
            val row = ContentValues(1)
            row.put(LokiAPIDatabase.groupPublicKey, publicKey)
            database.insertOrUpdate(LokiAPIDatabase.closedGroupPublicKeysTable, row, "${LokiAPIDatabase.groupPublicKey} = ?", arrayOf( publicKey ))
        }
        for (keyPair in keyPairs) {
            // In this particular case keyPair.publicKey == groupPublicKey
            val timestamp = Date().time.toString()
            val index = "${keyPair.publicKey.serialize().toHexString()}-$timestamp"
            val encryptionKeyPairPublicKey = keyPair.publicKey.serialize().toHexString().removing05PrefixIfNeeded()
            val encryptionKeyPairPrivateKey = keyPair.privateKey.serialize().toHexString()
            val row = ContentValues(3)
            row.put(LokiAPIDatabase.closedGroupsEncryptionKeyPairIndex, index)
            row.put(LokiAPIDatabase.encryptionKeyPairPublicKey, encryptionKeyPairPublicKey)
            row.put(LokiAPIDatabase.encryptionKeyPairPrivateKey, encryptionKeyPairPrivateKey)
            database.insertOrUpdate(LokiAPIDatabase.closedGroupEncryptionKeyPairsTable, row, "${LokiAPIDatabase.closedGroupsEncryptionKeyPairIndex} = ?", arrayOf( index ))
        }
    }
}