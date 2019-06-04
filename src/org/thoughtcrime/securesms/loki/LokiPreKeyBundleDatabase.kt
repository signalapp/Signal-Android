package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.signalservice.api.push.SignalServiceAddress

class LokiPreKeyBundleDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        private val tableName = "loki_pre_key_bundle_database"
        private val pubKey = "pub_key"
        private val preKeyId = "pre_key_id"
        private val preKeyPublic = "pre_key_public"
        private val signedPreKeyId = "signed_pre_key_id"
        private val signedPreKeyPublic = "signed_pre_key_public"
        private val signedPreKeySignature = "signed_pre_key_signature"
        private val identityKey = "identity_key"
        private val deviceId = "device_id"
        private val registrationId = "registration_id"

        @JvmStatic
        val createTableCommand = "CREATE TABLE $tableName (" +
                "$pubKey TEXT PRIMARY KEY" +
                "$preKeyId INTEGER," +
                "$preKeyPublic TEXT NOT NULL" +
                "$signedPreKeyId INTEGER" +
                "$signedPreKeyPublic TEXT NOT NULL" +
                "$signedPreKeySignature TEXT" +
                "$identityKey TEXT NOT NULL" +
                "$deviceId INTEGER" +
                "$registrationId INTEGER" +
                ");"
    }

    /**
     * Generate a `PreKeyBundle` for the given contact.
     * This generated bundle shouldn't be stored locally since this is used to generate bundles to send to other users.
     *
     * @param pubKey String The hex encoded public key of the contact.
     * @return PreKeyBundle? A pre key bundle or `null` if something went wrong.
     */
    fun generatePreKeyBundle(pubKey: String): PreKeyBundle? {
        val identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context)
        val signedPreKey = PreKeyUtil.getActiveSignedPreKey(context) ?: return null

        val preKeyRecord = DatabaseFactory.getLokiContactPreKeyDatabase(context).getOrCreatePreKey(pubKey)
        val registrationId = TextSecurePreferences.getLocalRegistrationId(context)
        if (registrationId == 0) return null

        val deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID
        return PreKeyBundle(registrationId, deviceId,preKeyRecord.id, preKeyRecord.keyPair.publicKey, signedPreKey.id, signedPreKey.keyPair.publicKey, signedPreKey.signature, identityKeyPair.publicKey)
    }

    /**
     * Get the `PreKeyBundle` associated with the given contact.
     *
     * @param pubKey String The hex encoded public key of the contact.
     * @return PreKeyBundle? The pre key bundle or `null` if it doesn't exist.
     */
    fun getPreKeyBundle(pubKey: String): PreKeyBundle? {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, "${Companion.pubKey} = ?", arrayOf(pubKey)) { cursor ->
            val registrationId = cursor.getInt(registrationId)
            val deviceId = cursor.getInt(deviceId)
            val preKeyId = cursor.getInt(preKeyId)
            val preKey = Curve.decodePoint(cursor.getBase64EncodedData(preKeyPublic), 0)
            val signedPreKeyId = cursor.getInt(signedPreKeyId)
            val signedPreKey = Curve.decodePoint(cursor.getBase64EncodedData(signedPreKeyPublic), 0)
            val signedPreKeySignature = cursor.getBase64EncodedData(signedPreKeySignature)
            val identityKey = IdentityKey(cursor.getBase64EncodedData(identityKey), 0)

            PreKeyBundle(registrationId, deviceId, preKeyId, preKey, signedPreKeyId, signedPreKey, signedPreKeySignature, identityKey)
        }
    }

    /**
     * Set the `PreKeyBundle` for the given contact.
     *
     * @param pubKey String The hex encoded public key of the contact.
     * @param preKeyBundle PreKeyBundle The pre key bundle.
     */
    fun setPreKeyBundle(pubKey: String, preKeyBundle: PreKeyBundle) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(registrationId, preKeyBundle.registrationId)
        contentValues.put(deviceId, preKeyBundle.deviceId)
        contentValues.put(preKeyId, preKeyBundle.preKeyId)
        contentValues.put(preKeyPublic, Base64.encodeBytes(preKeyBundle.preKey.serialize()))
        contentValues.put(signedPreKeyId, preKeyBundle.signedPreKeyId)
        contentValues.put(signedPreKeyPublic, Base64.encodeBytes(preKeyBundle.signedPreKey.serialize()))
        contentValues.put(signedPreKeySignature, Base64.encodeBytes(preKeyBundle.signedPreKeySignature))
        contentValues.put(identityKey, Base64.encodeBytes(preKeyBundle.identityKey.serialize()))
        contentValues.put(Companion.pubKey, pubKey)

        database.insertWithOnConflict(tableName, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Remove the `PreKeyBundle` for the given contact.
     *
     * @param pubKey String The hex encoded public key of the contact.
     */
    fun removePreKeyBundle(pubKey: String) {
        val database = databaseHelper.writableDatabase
        database.delete(tableName, "${Companion.pubKey} = ?", arrayOf(pubKey))
    }

}