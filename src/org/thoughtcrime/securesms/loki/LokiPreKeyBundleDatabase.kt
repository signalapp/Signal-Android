package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.Cursor
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.util.KeyHelper
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.messaging.LokiPreKeyBundleDatabaseProtocol

class LokiPreKeyBundleDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiPreKeyBundleDatabaseProtocol {

    companion object {
        private val tableName = "loki_pre_key_bundle_database"
        private val hexEncodedPublicKey = "public_key"
        private val preKeyID = "pre_key_id"
        private val preKeyPublic = "pre_key_public"
        private val signedPreKeyID = "signed_pre_key_id"
        private val signedPreKeyPublic = "signed_pre_key_public"
        private val signedPreKeySignature = "signed_pre_key_signature"
        private val identityKey = "identity_key"
        private val deviceID = "device_id"
        private val registrationID = "registration_id"
        @JvmStatic val createTableCommand = "CREATE TABLE $tableName (" + "$hexEncodedPublicKey TEXT PRIMARY KEY," + "$preKeyID INTEGER," +
            "$preKeyPublic TEXT NOT NULL," + "$signedPreKeyID INTEGER," + "$signedPreKeyPublic TEXT NOT NULL," +
            "$signedPreKeySignature TEXT," + "$identityKey TEXT NOT NULL," + "$deviceID INTEGER," + "$registrationID INTEGER" + ");"
    }

    fun resetAllPreKeyBundleInfo() {
        TextSecurePreferences.removeLocalRegistrationId(context)
        TextSecurePreferences.setSignedPreKeyRegistered(context, false)
    }

    fun generatePreKeyBundle(hexEncodedPublicKey: String): PreKeyBundle? {
        var registrationID = TextSecurePreferences.getLocalRegistrationId(context)
        if (registrationID == 0) {
            registrationID = KeyHelper.generateRegistrationId(false)
            TextSecurePreferences.setLocalRegistrationId(context, registrationID)
        }
        val deviceID = SignalServiceAddress.DEFAULT_DEVICE_ID
        val preKeyRecord = DatabaseFactory.getLokiPreKeyRecordDatabase(context).getOrCreatePreKeyRecord(hexEncodedPublicKey)
        val identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context)
        if (TextSecurePreferences.isSignedPreKeyRegistered(context)) {
            Log.d("Loki", "A signed pre key has already been registered.")
        } else {
            Log.d("Loki", "Registering a new signed pre key.")
            PreKeyUtil.generateSignedPreKey(context, identityKeyPair, true)
            TextSecurePreferences.setSignedPreKeyRegistered(context, true)
        }
        val activeSignedPreKey = PreKeyUtil.getActiveSignedPreKey(context) ?: return null
        return PreKeyBundle(registrationID, deviceID, preKeyRecord.id, preKeyRecord.keyPair.publicKey, activeSignedPreKey.id, activeSignedPreKey.keyPair.publicKey, activeSignedPreKey.signature, identityKeyPair.publicKey)
    }

    override fun getPreKeyBundle(hexEncodedPublicKey: String): PreKeyBundle? {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey )) { cursor ->
            val registrationID = cursor.getInt(registrationID)
            val deviceID = cursor.getInt(deviceID)
            val preKeyID = cursor.getInt(preKeyID)
            val preKey = Curve.decodePoint(cursor.getBase64EncodedData(preKeyPublic), 0)
            val signedPreKeyID = cursor.getInt(signedPreKeyID)
            val signedPreKey = Curve.decodePoint(cursor.getBase64EncodedData(signedPreKeyPublic), 0)
            val signedPreKeySignature = cursor.getBase64EncodedData(signedPreKeySignature)
            val identityKey = IdentityKey(cursor.getBase64EncodedData(identityKey), 0)
            PreKeyBundle(registrationID, deviceID, preKeyID, preKey, signedPreKeyID, signedPreKey, signedPreKeySignature, identityKey)
        }
    }

    fun setPreKeyBundle(hexEncodedPublicKey: String, preKeyBundle: PreKeyBundle) {
        val database = databaseHelper.writableDatabase
        val values = ContentValues(9)
        values.put(registrationID, preKeyBundle.registrationId)
        values.put(deviceID, preKeyBundle.deviceId)
        values.put(preKeyID, preKeyBundle.preKeyId)
        values.put(preKeyPublic, Base64.encodeBytes(preKeyBundle.preKey.serialize()))
        values.put(signedPreKeyID, preKeyBundle.signedPreKeyId)
        values.put(signedPreKeyPublic, Base64.encodeBytes(preKeyBundle.signedPreKey.serialize()))
        values.put(signedPreKeySignature, Base64.encodeBytes(preKeyBundle.signedPreKeySignature))
        values.put(identityKey, Base64.encodeBytes(preKeyBundle.identityKey.serialize()))
        values.put(Companion.hexEncodedPublicKey, hexEncodedPublicKey)
        database.insertOrUpdate(tableName, values, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey ))
    }

    override fun removePreKeyBundle(hexEncodedPublicKey: String) {
        val database = databaseHelper.writableDatabase
        database.delete(tableName, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey ))
    }

    fun hasPreKeyBundle(hexEncodedPublicKey: String): Boolean {
        val database = databaseHelper.readableDatabase
        var cursor: Cursor? = null
        return try {
            cursor = database.query(tableName, null, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey ), null, null, null)
            cursor != null && cursor.count > 0
        } catch (e: Exception) {
            false
        } finally {
          cursor?.close()
        }
    }
}