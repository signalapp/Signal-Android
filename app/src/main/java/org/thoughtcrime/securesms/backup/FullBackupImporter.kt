package org.thoughtcrime.securesms.backup

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import net.sqlcipher.database.SQLiteDatabase
import org.greenrobot.eventbus.EventBus
import org.thoughtcrime.securesms.backup.BackupProtos.*
import org.thoughtcrime.securesms.crypto.AttachmentSecret
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream
import org.thoughtcrime.securesms.database.*
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.util.BackupUtil

import org.session.libsession.messaging.avatars.AvatarHelper
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.Conversions
import org.session.libsession.utilities.Util
import org.session.libsignal.crypto.kdf.HKDFv3
import org.session.libsignal.utilities.ByteUtil

import java.io.*
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object FullBackupImporter {
    /**
     * Because BackupProtos.SharedPreference was made only to serialize string values,
     * we use these 3-char prefixes to explicitly cast the values before inserting to a preference file.
     */
    const val PREF_PREFIX_TYPE_INT = "i__"
    const val PREF_PREFIX_TYPE_BOOLEAN = "b__"

    private val TAG = FullBackupImporter::class.java.simpleName

    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun importFromUri(context: Context,
                      attachmentSecret: AttachmentSecret,
                      db: SQLiteDatabase,
                      fileUri: Uri,
                      passphrase: String) {

        val baseInputStream = context.contentResolver.openInputStream(fileUri)
                ?: throw IOException("Cannot open an input stream for the file URI: $fileUri")

        var count = 0
        try {
            BackupRecordInputStream(baseInputStream, passphrase).use { inputStream ->
                db.beginTransaction()
                dropAllTables(db)
                var frame: BackupFrame
                while (!inputStream.readFrame().also { frame = it }.end) {
                    if (count++ % 100 == 0) EventBus.getDefault().post(BackupEvent.createProgress(count))
                    when {
                        frame.hasVersion() -> processVersion(db, frame.version)
                        frame.hasStatement() -> processStatement(db, frame.statement)
                        frame.hasPreference() -> processPreference(context, frame.preference)
                        frame.hasAttachment() -> processAttachment(context, attachmentSecret, db, frame.attachment, inputStream)
                        frame.hasAvatar() -> processAvatar(context, frame.avatar, inputStream)
                    }
                }
                trimEntriesForExpiredMessages(context, db)
                db.setTransactionSuccessful()
            }
        } finally {
            if (db.inTransaction()) {
                db.endTransaction()
            }
        }
        EventBus.getDefault().post(BackupEvent.createFinished())
    }

    @Throws(IOException::class)
    private fun processVersion(db: SQLiteDatabase, version: DatabaseVersion) {
        if (version.version > db.version) {
            throw DatabaseDowngradeException(db.version, version.version)
        }
        db.version = version.version
    }

    private fun processStatement(db: SQLiteDatabase, statement: SqlStatement) {
        val isForSmsFtsSecretTable = statement.statement.contains(SearchDatabase.SMS_FTS_TABLE_NAME + "_")
        val isForMmsFtsSecretTable = statement.statement.contains(SearchDatabase.MMS_FTS_TABLE_NAME + "_")
        val isForSqliteSecretTable = statement.statement.toLowerCase(Locale.ENGLISH).startsWith("create table sqlite_")
        if (isForSmsFtsSecretTable || isForMmsFtsSecretTable || isForSqliteSecretTable) {
            Log.i(TAG, "Ignoring import for statement: " + statement.statement)
            return
        }
        val parameters: MutableList<Any?> = LinkedList()
        for (parameter in statement.parametersList) {
            when {
                parameter.hasStringParamter() -> parameters.add(parameter.stringParamter)
                parameter.hasDoubleParameter() -> parameters.add(parameter.doubleParameter)
                parameter.hasIntegerParameter() -> parameters.add(parameter.integerParameter)
                parameter.hasBlobParameter() -> parameters.add(parameter.blobParameter.toByteArray())
                parameter.hasNullparameter() -> parameters.add(null)
            }
        }
        if (parameters.size > 0) {
            db.execSQL(statement.statement, parameters.toTypedArray())
        } else {
            db.execSQL(statement.statement)
        }
    }

    @Throws(IOException::class)
    private fun processAttachment(context: Context, attachmentSecret: AttachmentSecret,
                                  db: SQLiteDatabase, attachment: Attachment,
                                  inputStream: BackupRecordInputStream) {
        val partsDirectory = context.getDir(AttachmentDatabase.DIRECTORY, Context.MODE_PRIVATE)
        val dataFile = File.createTempFile("part", ".mms", partsDirectory)
        val output = ModernEncryptingPartOutputStream.createFor(attachmentSecret, dataFile, false)
        inputStream.readAttachmentTo(output.second, attachment.length)
        val contentValues = ContentValues()
        contentValues.put(AttachmentDatabase.DATA, dataFile.absolutePath)
        contentValues.put(AttachmentDatabase.THUMBNAIL, null as String?)
        contentValues.put(AttachmentDatabase.DATA_RANDOM, output.first)
        db.update(AttachmentDatabase.TABLE_NAME, contentValues,
                "${AttachmentDatabase.ROW_ID} = ? AND ${AttachmentDatabase.UNIQUE_ID} = ?",
                arrayOf(attachment.rowId.toString(), attachment.attachmentId.toString()))
    }

    @Throws(IOException::class)
    private fun processAvatar(context: Context, avatar: Avatar, inputStream: BackupRecordInputStream) {
        inputStream.readAttachmentTo(FileOutputStream(
                AvatarHelper.getAvatarFile(context, Address.fromExternal(context, avatar.name))), avatar.length)
    }

    @SuppressLint("ApplySharedPref")
    private fun processPreference(context: Context, preference: SharedPreference) {
        val preferences = context.getSharedPreferences(preference.file, 0)
        val key = preference.key
        val value = preference.value

        // See the comment next to PREF_PREFIX_TYPE_* constants.
        when {
            key.startsWith(PREF_PREFIX_TYPE_INT) ->
                preferences.edit().putInt(
                        key.substring(PREF_PREFIX_TYPE_INT.length),
                        value.toInt()
                ).commit()
            key.startsWith(PREF_PREFIX_TYPE_BOOLEAN) ->
                preferences.edit().putBoolean(
                        key.substring(PREF_PREFIX_TYPE_BOOLEAN.length),
                        value.toBoolean()
                ).commit()
            else ->
                preferences.edit().putString(key, value).commit()
        }
    }

    private fun dropAllTables(db: SQLiteDatabase) {
        db.rawQuery("SELECT name, type FROM sqlite_master", null).use { cursor ->
            while (cursor != null && cursor.moveToNext()) {
                val name = cursor.getString(0)
                val type = cursor.getString(1)
                if ("table" == type && !name.startsWith("sqlite_")) {
                    db.execSQL("DROP TABLE IF EXISTS $name")
                }
            }
        }
    }

    private fun trimEntriesForExpiredMessages(context: Context, db: SQLiteDatabase) {
        val trimmedCondition = " NOT IN (SELECT ${MmsDatabase.ID} FROM ${MmsDatabase.TABLE_NAME})"
        db.delete(GroupReceiptDatabase.TABLE_NAME, GroupReceiptDatabase.MMS_ID + trimmedCondition, null)
        val columns = arrayOf(AttachmentDatabase.ROW_ID, AttachmentDatabase.UNIQUE_ID)
        val where = AttachmentDatabase.MMS_ID + trimmedCondition
        db.query(AttachmentDatabase.TABLE_NAME, columns, where, null, null, null, null).use { cursor ->
            while (cursor != null && cursor.moveToNext()) {
                DatabaseFactory.getAttachmentDatabase(context)
                        .deleteAttachment(AttachmentId(cursor.getLong(0), cursor.getLong(1)))
            }
        }
        db.query(ThreadDatabase.TABLE_NAME, arrayOf(ThreadDatabase.ID),
                ThreadDatabase.EXPIRES_IN + " > 0", null, null, null, null).use { cursor ->
            while (cursor != null && cursor.moveToNext()) {
                DatabaseFactory.getThreadDatabase(context).update(cursor.getLong(0), false)
            }
        }
    }

    private class BackupRecordInputStream : Closeable {
        private val inputStream: InputStream
        private val cipher: Cipher
        private val mac: Mac
        private val cipherKey: ByteArray
        private val macKey: ByteArray
        private val iv: ByteArray

        private var counter = 0

        @Throws(IOException::class)
        constructor(inputStream: InputStream, passphrase: String) : super() {
            try {
                this.inputStream = inputStream
                val headerLengthBytes = ByteArray(4)
                Util.readFully(this.inputStream, headerLengthBytes)
                val headerLength = Conversions.byteArrayToInt(headerLengthBytes)
                val headerFrame = ByteArray(headerLength)
                Util.readFully(this.inputStream, headerFrame)
                val frame = BackupFrame.parseFrom(headerFrame)
                if (!frame.hasHeader()) {
                    throw IOException("Backup stream does not start with header!")
                }
                val header = frame.header
                iv = header.iv.toByteArray()
                if (iv.size != 16) {
                    throw IOException("Invalid IV length!")
                }
                val key = BackupUtil.computeBackupKey(passphrase, if (header.hasSalt()) header.salt.toByteArray() else null)
                val derived = HKDFv3().deriveSecrets(key, "Backup Export".toByteArray(), 64)
                val split = ByteUtil.split(derived, 32, 32)
                cipherKey = split[0]
                macKey = split[1]
                cipher = Cipher.getInstance("AES/CTR/NoPadding")
                mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(macKey, "HmacSHA256"))
                counter = Conversions.byteArrayToInt(iv)
            } catch (e: Exception) {
                when (e) {
                    is NoSuchAlgorithmException,
                    is NoSuchPaddingException,
                    is InvalidKeyException -> {
                        throw AssertionError(e)
                    }
                    else -> throw e
                }
            }
        }

        @Throws(IOException::class)
        fun readFrame(): BackupFrame {
            return readFrame(inputStream)
        }

        @Throws(IOException::class)
        fun readAttachmentTo(out: OutputStream, length: Int) {
            var length = length
            try {
                Conversions.intToByteArray(iv, 0, counter++)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cipherKey, "AES"), IvParameterSpec(iv))
                mac.update(iv)
                val buffer = ByteArray(8192)
                while (length > 0) {
                    val read = inputStream.read(buffer, 0, Math.min(buffer.size, length))
                    if (read == -1) throw IOException("File ended early!")
                    mac.update(buffer, 0, read)
                    val plaintext = cipher.update(buffer, 0, read)
                    if (plaintext != null) {
                        out.write(plaintext, 0, plaintext.size)
                    }
                    length -= read
                }
                val plaintext = cipher.doFinal()
                if (plaintext != null) {
                    out.write(plaintext, 0, plaintext.size)
                }
                out.close()
                val ourMac = ByteUtil.trim(mac.doFinal(), 10)
                val theirMac = ByteArray(10)
                try {
                    Util.readFully(inputStream, theirMac)
                } catch (e: IOException) {
                    throw IOException(e)
                }
                if (!MessageDigest.isEqual(ourMac, theirMac)) {
                    throw IOException("Bad MAC")
                }
            } catch (e: Exception) {
                when (e) {
                    is InvalidKeyException,
                    is InvalidAlgorithmParameterException,
                    is IllegalBlockSizeException,
                    is BadPaddingException -> {
                        throw AssertionError(e)
                    }
                    else -> throw e
                }
            }
        }

        @Throws(IOException::class)
        private fun readFrame(`in`: InputStream?): BackupFrame {
            return try {
                val length = ByteArray(4)
                Util.readFully(`in`, length)
                val frame = ByteArray(Conversions.byteArrayToInt(length))
                Util.readFully(`in`, frame)
                val theirMac = ByteArray(10)
                System.arraycopy(frame, frame.size - 10, theirMac, 0, theirMac.size)
                mac.update(frame, 0, frame.size - 10)
                val ourMac = ByteUtil.trim(mac.doFinal(), 10)
                if (!MessageDigest.isEqual(ourMac, theirMac)) {
                    throw IOException("Bad MAC")
                }
                Conversions.intToByteArray(iv, 0, counter++)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cipherKey, "AES"), IvParameterSpec(iv))
                val plaintext = cipher.doFinal(frame, 0, frame.size - 10)
                BackupFrame.parseFrom(plaintext)
            } catch (e: Exception) {
                when (e) {
                    is InvalidKeyException,
                    is InvalidAlgorithmParameterException,
                    is IllegalBlockSizeException,
                    is BadPaddingException -> {
                        throw AssertionError(e)
                    }
                    else -> throw e
                }
            }
        }

        @Throws(IOException::class)
        override fun close() {
            inputStream.close()
        }
    }

    class DatabaseDowngradeException internal constructor(currentVersion: Int, backupVersion: Int) :
            IOException("Tried to import a backup with version $backupVersion into a database with version $currentVersion")
}