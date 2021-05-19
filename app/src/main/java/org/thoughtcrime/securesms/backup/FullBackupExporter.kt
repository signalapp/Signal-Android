package org.thoughtcrime.securesms.backup

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.annimon.stream.function.Consumer
import com.annimon.stream.function.Predicate
import com.google.protobuf.ByteString
import net.sqlcipher.database.SQLiteDatabase
import org.greenrobot.eventbus.EventBus

import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.utilities.Conversions

import org.thoughtcrime.securesms.backup.BackupProtos.*
import org.thoughtcrime.securesms.crypto.AttachmentSecret
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream
import org.thoughtcrime.securesms.database.*
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.loki.database.LokiAPIDatabase
import org.thoughtcrime.securesms.loki.database.LokiBackupFilesDatabase
import org.thoughtcrime.securesms.util.BackupUtil
import org.session.libsession.utilities.Util
import org.session.libsignal.crypto.kdf.HKDFv3
import org.session.libsignal.utilities.ByteUtil
import java.io.*
import java.lang.Exception
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object FullBackupExporter {
    private val TAG = FullBackupExporter::class.java.simpleName

    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun export(context: Context,
               attachmentSecret: AttachmentSecret,
               input: SQLiteDatabase,
               fileUri: Uri,
               passphrase: String) {

        val baseOutputStream = context.contentResolver.openOutputStream(fileUri)
                ?: throw IOException("Cannot open an output stream for the file URI: $fileUri")

        var count = 0
        try {
            BackupFrameOutputStream(baseOutputStream, passphrase).use { outputStream ->
                outputStream.writeDatabaseVersion(input.version)
                val tables = exportSchema(input, outputStream)
                for (table in tables) if (shouldExportTable(table)) {
                    count = when (table) {
                        SmsDatabase.TABLE_NAME, MmsDatabase.TABLE_NAME -> {
                            exportTable(table, input, outputStream,
                                    { cursor: Cursor ->
                                        cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsColumns.EXPIRES_IN)) <= 0
                                    },
                                    null,
                                    count)
                        }
                        GroupReceiptDatabase.TABLE_NAME -> {
                            exportTable(table, input, outputStream,
                                    { cursor: Cursor ->
                                        isForNonExpiringMessage(input, cursor.getLong(cursor.getColumnIndexOrThrow(GroupReceiptDatabase.MMS_ID)))
                                    },
                                    null,
                                    count)
                        }
                        AttachmentDatabase.TABLE_NAME -> {
                            exportTable(table, input, outputStream,
                                    { cursor: Cursor ->
                                        isForNonExpiringMessage(input, cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.MMS_ID)))
                                    },
                                    { cursor: Cursor ->
                                        exportAttachment(attachmentSecret, cursor, outputStream)
                                    },
                                    count)
                        }
                        else -> {
                            exportTable(table, input, outputStream, null, null, count)
                        }
                    }
                }
                for (preference in BackupUtil.getBackupRecords(context)) {
                    EventBus.getDefault().post(BackupEvent.createProgress(++count))
                    outputStream.writePreferenceEntry(preference)
                }
                for (preference in BackupPreferences.getBackupRecords(context)) {
                    EventBus.getDefault().post(BackupEvent.createProgress(++count))
                    outputStream.writePreferenceEntry(preference)
                }
                for (avatar in AvatarHelper.getAvatarFiles(context)) {
                    EventBus.getDefault().post(BackupEvent.createProgress(++count))
                    outputStream.writeAvatar(avatar.name, FileInputStream(avatar), avatar.length())
                }
                outputStream.writeEnd()
            }
            EventBus.getDefault().post(BackupEvent.createFinished())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make full backup.", e)
            EventBus.getDefault().post(BackupEvent.createFinished(e))
            throw e
        }
    }

    private inline fun shouldExportTable(table: String): Boolean {
        return  table != PushDatabase.TABLE_NAME &&

                table != LokiBackupFilesDatabase.TABLE_NAME &&
                table != LokiAPIDatabase.openGroupProfilePictureTable &&

                table != JobDatabase.Jobs.TABLE_NAME &&
                table != JobDatabase.Constraints.TABLE_NAME &&
                table != JobDatabase.Dependencies.TABLE_NAME &&

                !table.startsWith(SearchDatabase.SMS_FTS_TABLE_NAME) &&
                !table.startsWith(SearchDatabase.MMS_FTS_TABLE_NAME) &&
                !table.startsWith("sqlite_")
    }

    @Throws(IOException::class)
    private fun exportSchema(input: SQLiteDatabase, outputStream: BackupFrameOutputStream): List<String> {
        val tables: MutableList<String> = LinkedList()
        input.rawQuery("SELECT sql, name, type FROM sqlite_master", null).use { cursor ->
            while (cursor != null && cursor.moveToNext()) {
                val sql = cursor.getString(0)
                val name = cursor.getString(1)
                val type = cursor.getString(2)
                if (sql != null) {
                    val isSmsFtsSecretTable = name != null && name != SearchDatabase.SMS_FTS_TABLE_NAME && name.startsWith(SearchDatabase.SMS_FTS_TABLE_NAME)
                    val isMmsFtsSecretTable = name != null && name != SearchDatabase.MMS_FTS_TABLE_NAME && name.startsWith(SearchDatabase.MMS_FTS_TABLE_NAME)
                    if (!isSmsFtsSecretTable && !isMmsFtsSecretTable) {
                        if ("table" == type) {
                            tables.add(name)
                        }
                        outputStream.writeSql(SqlStatement.newBuilder().setStatement(cursor.getString(0)).build())
                    }
                }
            }
        }
        return tables
    }

    @Throws(IOException::class)
    private fun exportTable(table: String,
                            input: SQLiteDatabase,
                            outputStream: BackupFrameOutputStream,
                            predicate: Predicate<Cursor>?,
                            postProcess: Consumer<Cursor>?,
                            count: Int): Int {
        var count = count
        val template = "INSERT INTO $table VALUES "
        input.rawQuery("SELECT * FROM $table", null).use { cursor ->
            while (cursor != null && cursor.moveToNext()) {
                EventBus.getDefault().post(BackupEvent.createProgress(++count))
                if (predicate != null && !predicate.test(cursor)) continue

                val statement = StringBuilder(template)
                val statementBuilder = SqlStatement.newBuilder()
                statement.append('(')
                for (i in 0 until cursor.columnCount) {
                    statement.append('?')
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_STRING -> {
                            statementBuilder.addParameters(SqlStatement.SqlParameter.newBuilder()
                                    .setStringParamter(cursor.getString(i)))
                        }
                        Cursor.FIELD_TYPE_FLOAT -> {
                            statementBuilder.addParameters(SqlStatement.SqlParameter.newBuilder()
                                    .setDoubleParameter(cursor.getDouble(i)))
                        }
                        Cursor.FIELD_TYPE_INTEGER -> {
                            statementBuilder.addParameters(SqlStatement.SqlParameter.newBuilder()
                                    .setIntegerParameter(cursor.getLong(i)))
                        }
                        Cursor.FIELD_TYPE_BLOB -> {
                            statementBuilder.addParameters(SqlStatement.SqlParameter.newBuilder()
                                    .setBlobParameter(ByteString.copyFrom(cursor.getBlob(i))))
                        }
                        Cursor.FIELD_TYPE_NULL -> {
                            statementBuilder.addParameters(SqlStatement.SqlParameter.newBuilder()
                                    .setNullparameter(true))
                        }
                        else -> {
                            throw AssertionError("unknown type?" + cursor.getType(i))
                        }
                    }
                    if (i < cursor.columnCount - 1) {
                        statement.append(',')
                    }
                }
                statement.append(')')
                outputStream.writeSql(statementBuilder.setStatement(statement.toString()).build())
                postProcess?.accept(cursor)
            }
        }
        return count
    }

    private fun exportAttachment(attachmentSecret: AttachmentSecret, cursor: Cursor, outputStream: BackupFrameOutputStream) {
        try {
            val rowId = cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.ROW_ID))
            val uniqueId = cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.UNIQUE_ID))
            var size = cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.SIZE))
            val data = cursor.getString(cursor.getColumnIndexOrThrow(AttachmentDatabase.DATA))
            val random = cursor.getBlob(cursor.getColumnIndexOrThrow(AttachmentDatabase.DATA_RANDOM))
            if (!TextUtils.isEmpty(data) && size <= 0) {
                size = calculateVeryOldStreamLength(attachmentSecret, random, data)
            }
            if (!TextUtils.isEmpty(data) && size > 0) {
                val inputStream: InputStream = if (random != null && random.size == 32) {
                    ModernDecryptingPartInputStream.createFor(attachmentSecret, random, File(data), 0)
                } else {
                    ClassicDecryptingPartInputStream.createFor(attachmentSecret, File(data))
                }
                outputStream.writeAttachment(AttachmentId(rowId, uniqueId), inputStream, size)
            }
        } catch (e: IOException) {
            Log.w(TAG, e)
        }
    }

    @Throws(IOException::class)
    private fun calculateVeryOldStreamLength(attachmentSecret: AttachmentSecret, random: ByteArray?, data: String): Long {
        var result: Long = 0
        val inputStream: InputStream = if (random != null && random.size == 32) {
            ModernDecryptingPartInputStream.createFor(attachmentSecret, random, File(data), 0)
        } else {
            ClassicDecryptingPartInputStream.createFor(attachmentSecret, File(data))
        }
        var read: Int
        val buffer = ByteArray(8192)
        while (inputStream.read(buffer, 0, buffer.size).also { read = it } != -1) {
            result += read.toLong()
        }
        return result
    }

    private fun isForNonExpiringMessage(db: SQLiteDatabase, mmsId: Long): Boolean {
        val columns = arrayOf(MmsDatabase.EXPIRES_IN)
        val where = MmsDatabase.ID + " = ?"
        val args = arrayOf(mmsId.toString())
        db.query(MmsDatabase.TABLE_NAME, columns, where, args, null, null, null).use { mmsCursor ->
            if (mmsCursor != null && mmsCursor.moveToFirst()) {
                return mmsCursor.getLong(0) == 0L
            }
        }
        return false
    }

    private class BackupFrameOutputStream : Closeable, Flushable {

        private val outputStream: OutputStream
        private var cipher: Cipher
        private var mac: Mac
        private val cipherKey: ByteArray
        private val macKey: ByteArray
        private val iv: ByteArray

        private var counter: Int = 0

        constructor(outputStream: OutputStream, passphrase: String) : super() {
            try {
                val salt = Util.getSecretBytes(32)
                val key = BackupUtil.computeBackupKey(passphrase, salt)
                val derived = HKDFv3().deriveSecrets(key, "Backup Export".toByteArray(), 64)
                val split = ByteUtil.split(derived, 32, 32)
                cipherKey = split[0]
                macKey = split[1]
                cipher = Cipher.getInstance("AES/CTR/NoPadding")
                mac = Mac.getInstance("HmacSHA256")
                this.outputStream = outputStream
                iv = Util.getSecretBytes(16)
                counter = Conversions.byteArrayToInt(iv)
                mac.init(SecretKeySpec(macKey, "HmacSHA256"))
                val header = BackupFrame.newBuilder().setHeader(Header.newBuilder()
                        .setIv(ByteString.copyFrom(iv))
                        .setSalt(ByteString.copyFrom(salt)))
                        .build().toByteArray()
                outputStream.write(Conversions.intToByteArray(header.size))
                outputStream.write(header)
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
        fun writeSql(statement: SqlStatement) {
            write(outputStream, BackupFrame.newBuilder().setStatement(statement).build())
        }

        @Throws(IOException::class)
        fun writePreferenceEntry(preference: SharedPreference?) {
            write(outputStream, BackupFrame.newBuilder().setPreference(preference).build())
        }

        @Throws(IOException::class)
        fun writeAvatar(avatarName: String, inputStream: InputStream, size: Long) {
            write(outputStream, BackupFrame.newBuilder()
                    .setAvatar(Avatar.newBuilder()
                            .setName(avatarName)
                            .setLength(Util.toIntExact(size))
                            .build())
                    .build())
            writeStream(inputStream)
        }

        @Throws(IOException::class)
        fun writeAttachment(attachmentId: AttachmentId, inputStream: InputStream, size: Long) {
            write(outputStream, BackupFrame.newBuilder()
                    .setAttachment(Attachment.newBuilder()
                            .setRowId(attachmentId.rowId)
                            .setAttachmentId(attachmentId.uniqueId)
                            .setLength(Util.toIntExact(size))
                            .build())
                    .build())
            writeStream(inputStream)
        }

        @Throws(IOException::class)
        fun writeSticker(rowId: Long, inputStream: InputStream, size: Long) {
            write(outputStream, BackupFrame.newBuilder()
                    .setSticker(Sticker.newBuilder()
                            .setRowId(rowId)
                            .setLength(Util.toIntExact(size))
                            .build())
                    .build())
            writeStream(inputStream)
        }

        @Throws(IOException::class)
        fun writeDatabaseVersion(version: Int) {
            write(outputStream, BackupFrame.newBuilder()
                    .setVersion(DatabaseVersion.newBuilder().setVersion(version))
                    .build())
        }

        @Throws(IOException::class)
        fun writeEnd() {
            write(outputStream, BackupFrame.newBuilder().setEnd(true).build())
        }

        @Throws(IOException::class)
        private fun writeStream(inputStream: InputStream) {
            try {
                Conversions.intToByteArray(iv, 0, counter++)
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cipherKey, "AES"), IvParameterSpec(iv))
                mac.update(iv)
                val buffer = ByteArray(8192)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    val ciphertext = cipher.update(buffer, 0, read)
                    if (ciphertext != null) {
                        outputStream.write(ciphertext)
                        mac.update(ciphertext)
                    }
                }
                val remainder = cipher.doFinal()
                outputStream.write(remainder)
                mac.update(remainder)
                val attachmentDigest = mac.doFinal()
                outputStream.write(attachmentDigest, 0, 10)
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
        private fun write(out: OutputStream, frame: BackupFrame) {
            try {
                Conversions.intToByteArray(iv, 0, counter++)
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cipherKey, "AES"), IvParameterSpec(iv))
                val frameCiphertext = cipher.doFinal(frame.toByteArray())
                val frameMac = mac.doFinal(frameCiphertext)
                val length = Conversions.intToByteArray(frameCiphertext.size + 10)
                out.write(length)
                out.write(frameCiphertext)
                out.write(frameMac, 0, 10)
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
        override fun flush() {
            outputStream.flush()
        }

        @Throws(IOException::class)
        override fun close() {
            outputStream.close()
        }
    }
}