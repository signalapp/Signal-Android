package org.thoughtcrime.securesms.loki.protocol

import android.content.ContentValues
import org.thoughtcrime.securesms.loki.database.LokiAPIDatabase
import org.thoughtcrime.securesms.loki.database.SharedSenderKeysDatabase
import org.thoughtcrime.securesms.loki.utilities.get
import org.thoughtcrime.securesms.loki.utilities.getAll
import org.thoughtcrime.securesms.loki.utilities.getString
import org.thoughtcrime.securesms.loki.utilities.insertOrUpdate
import org.thoughtcrime.securesms.util.Hex
import org.session.libsignal.libsignal.ecc.DjbECPrivateKey
import org.session.libsignal.libsignal.ecc.DjbECPublicKey
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.service.loki.utilities.PublicKeyValidation
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.service.loki.utilities.toHexString
import java.util.*

object ClosedGroupsMigration {

    fun perform(database: net.sqlcipher.database.SQLiteDatabase) {
        val publicKeys = database.getAll(SharedSenderKeysDatabase.closedGroupPrivateKeyTable, null, null) { cursor ->
            cursor.getString(SharedSenderKeysDatabase.closedGroupPublicKey)
        }.filter {
            PublicKeyValidation.isValid(it)
        }
        val keyPairs = mutableListOf<ECKeyPair>()
        for (publicKey in publicKeys) {
            val query = "${SharedSenderKeysDatabase.closedGroupPublicKey} = ?"
            val privateKey = database.get(SharedSenderKeysDatabase.closedGroupPrivateKeyTable, query, arrayOf( publicKey )) { cursor ->
                cursor.getString(SharedSenderKeysDatabase.closedGroupPrivateKey)
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