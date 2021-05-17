package org.thoughtcrime.securesms.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import network.loki.messenger.R
import org.greenrobot.eventbus.EventBus
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.ByteUtil
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.backup.BackupEvent
import org.thoughtcrime.securesms.backup.BackupPassphrase
import org.thoughtcrime.securesms.backup.BackupProtos.SharedPreference
import org.thoughtcrime.securesms.backup.FullBackupExporter
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.session.libsession.utilities.IdentityKeyUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.database.BackupFileRecord
import org.thoughtcrime.securesms.service.LocalBackupListener
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

object BackupUtil {
    private const val MASTER_SECRET_UTIL_PREFERENCES_NAME = "SecureSMS-Preferences"
    private const val TAG = "BackupUtil"
    const val BACKUP_FILE_MIME_TYPE = "application/session-backup"
    const val BACKUP_PASSPHRASE_LENGTH = 30

    fun getBackupRecords(context: Context): List<SharedPreference> {
        val prefName = MASTER_SECRET_UTIL_PREFERENCES_NAME
        val preferences = context.getSharedPreferences(prefName, 0)
        val prefList = LinkedList<SharedPreference>()
        prefList.add(SharedPreference.newBuilder()
                .setFile(prefName)
                .setKey(IdentityKeyUtil.IDENTITY_PUBLIC_KEY_PREF)
                .setValue(preferences.getString(IdentityKeyUtil.IDENTITY_PUBLIC_KEY_PREF, null))
                .build())
        prefList.add(SharedPreference.newBuilder()
                .setFile(prefName)
                .setKey(IdentityKeyUtil.IDENTITY_PRIVATE_KEY_PREF)
                .setValue(preferences.getString(IdentityKeyUtil.IDENTITY_PRIVATE_KEY_PREF, null))
                .build())
        if (preferences.contains(IdentityKeyUtil.ED25519_PUBLIC_KEY)) {
            prefList.add(SharedPreference.newBuilder()
                    .setFile(prefName)
                    .setKey(IdentityKeyUtil.ED25519_PUBLIC_KEY)
                    .setValue(preferences.getString(IdentityKeyUtil.ED25519_PUBLIC_KEY, null))
                    .build())
        }
        if (preferences.contains(IdentityKeyUtil.ED25519_SECRET_KEY)) {
            prefList.add(SharedPreference.newBuilder()
                    .setFile(prefName)
                    .setKey(IdentityKeyUtil.ED25519_SECRET_KEY)
                    .setValue(preferences.getString(IdentityKeyUtil.ED25519_SECRET_KEY, null))
                    .build())
        }
        prefList.add(SharedPreference.newBuilder()
                .setFile(prefName)
                .setKey(IdentityKeyUtil.LOKI_SEED)
                .setValue(preferences.getString(IdentityKeyUtil.LOKI_SEED, null))
                .build())
        return prefList
    }

    /**
     * Set app-wide configuration to enable the backups and schedule them.
     *
     * Make sure that the backup dir is selected prior activating the backup.
     * Use [BackupDirSelector] or [setBackupDirUri] manually.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun enableBackups(context: Context, password: String) {
        val backupDir = getBackupDirUri(context)
        if (backupDir == null || !validateDirAccess(context, backupDir)) {
            throw IOException("Backup dir is not set or invalid.")
        }

        BackupPassphrase.set(context, password)
        TextSecurePreferences.setBackupEnabled(context, true)
        LocalBackupListener.schedule(context)
    }

    /**
     * Set app-wide configuration to disable the backups.
     *
     * This call resets the backup dir value.
     * Make sure to call [setBackupDirUri] prior next call to [enableBackups].
     *
     * @param deleteBackupFiles if true, deletes all the previously created backup files
     * (if the app has access to them)
     */
    @JvmStatic
    fun disableBackups(context: Context, deleteBackupFiles: Boolean) {
        BackupPassphrase.set(context, null)
        TextSecurePreferences.setBackupEnabled(context, false)
        if (deleteBackupFiles) {
            deleteAllBackupFiles(context)
        }
        setBackupDirUri(context, null)
    }

    @JvmStatic
    fun getLastBackupTimeString(context: Context, locale: Locale): String {
        val timestamp = DatabaseFactory.getLokiBackupFilesDatabase(context).getLastBackupFileTime()
        if (timestamp == null) {
            return context.getString(R.string.BackupUtil_never)
        }
        return DateUtils.getExtendedRelativeTimeSpanString(context, locale, timestamp.time)
    }

    @JvmStatic
    fun getLastBackup(context: Context): BackupFileRecord? {
        return DatabaseFactory.getLokiBackupFilesDatabase(context).getLastBackupFile()
    }

    @JvmStatic
    fun generateBackupPassphrase(): Array<String> {
        val random = ByteArray(BACKUP_PASSPHRASE_LENGTH).also { SecureRandom().nextBytes(it) }
        return Array(6) { i ->
            String.format("%05d", ByteUtil.byteArray5ToLong(random, i * 5) % 100000)
        }
    }

    @JvmStatic
    fun validateDirAccess(context: Context, dirUri: Uri): Boolean {
        val hasWritePermission = context.contentResolver.persistedUriPermissions.any {
            it.isWritePermission && it.uri == dirUri
        }
        if (!hasWritePermission) return false

        val document = DocumentFile.fromTreeUri(context, dirUri)
        if (document == null || !document.exists()) {
            return false
        }

        return true
    }

    @JvmStatic
    fun getBackupDirUri(context: Context): Uri? {
        val dirUriString = TextSecurePreferences.getBackupSaveDir(context) ?: return null
        return Uri.parse(dirUriString)
    }

    @JvmStatic
    fun setBackupDirUri(context: Context, uriString: String?) {
        TextSecurePreferences.setBackupSaveDir(context, uriString)
    }

    /**
     * @return The selected backup directory if it's valid (exists, is writable).
     */
    @JvmStatic
    fun getSelectedBackupDirIfValid(context: Context): Uri? {
        val dirUri = getBackupDirUri(context)

        if (dirUri == null) {
            Log.v(TAG, "The backup dir wasn't selected yet.")
            return null
        }
        if (!validateDirAccess(context, dirUri)) {
            Log.v(TAG, "Cannot validate the access to the dir $dirUri.")
            return null
        }

        return dirUri;
    }

    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun createBackupFile(context: Context): BackupFileRecord {
        val backupPassword = BackupPassphrase.get(context)
                ?: throw IOException("Backup password is null")

        val dirUri = getSelectedBackupDirIfValid(context)
                ?: throw IOException("Backup save directory is not selected or invalid")

        val date = Date()
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(date)
        val fileName = String.format("session-%s.backup", timestamp)

        val fileUri = DocumentsContract.createDocument(
                context.contentResolver,
                DocumentFile.fromTreeUri(context, dirUri)!!.uri,
                BACKUP_FILE_MIME_TYPE,
                fileName)

        if (fileUri == null) {
            Toast.makeText(context, "Cannot create writable file in the dir $dirUri", Toast.LENGTH_LONG).show()
            throw IOException("Cannot create writable file in the dir $dirUri")
        }

        try {
            FullBackupExporter.export(context,
                    AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                    DatabaseFactory.getBackupDatabase(context),
                    fileUri,
                    backupPassword)
        } catch (e: Exception) {
            // Delete the backup file on any error.
            DocumentsContract.deleteDocument(context.contentResolver, fileUri)
            throw e
        }

        //TODO Use real file size.
        val record = DatabaseFactory.getLokiBackupFilesDatabase(context)
                .insertBackupFile(BackupFileRecord(fileUri, -1, date))

        Log.v(TAG, "A backup file was created: $fileUri")

        return record
    }

    @JvmStatic
    @JvmOverloads
    fun deleteAllBackupFiles(context: Context, except: Collection<BackupFileRecord>? = null) {
        val db = DatabaseFactory.getLokiBackupFilesDatabase(context)
        db.getBackupFiles().forEach { record ->
            if (except != null && except.contains(record)) return@forEach

            // Try to delete the related file. The operation may fail in many cases
            // (the user moved/deleted the file, revoked the write permission, etc), so that's OK.
            try {
                val result = DocumentsContract.deleteDocument(context.contentResolver, record.uri)
                if (!result) {
                    Log.w(TAG, "Failed to delete backup file: ${record.uri}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete backup file: ${record.uri}", e)
            }

            db.deleteBackupFile(record)

            Log.v(TAG, "Backup file was deleted: ${record.uri}")
        }
    }

    @JvmStatic
    fun computeBackupKey(passphrase: String, salt: ByteArray?): ByteArray {
        return try {
            EventBus.getDefault().post(BackupEvent.createProgress(0))
            val digest = MessageDigest.getInstance("SHA-512")
            val input = passphrase.replace(" ", "").toByteArray()
            var hash: ByteArray = input
            if (salt != null) digest.update(salt)
            for (i in 0..249999) {
                if (i % 1000 == 0) EventBus.getDefault().post(BackupEvent.createProgress(0))
                digest.update(hash)
                hash = digest.digest(input)
            }
            ByteUtil.trim(hash, 32)
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        }
    }
}

/**
 * An utility class to help perform backup directory selection requests.
 *
 * An instance of this class should be created per an [Activity] or [Fragment]
 * and [onActivityResult] should be called appropriately.
 */
class BackupDirSelector(private val contextProvider: ContextProvider) {

    companion object {
        private const val REQUEST_CODE_SAVE_DIR = 7844
    }

    private val context: Context get() = contextProvider.getContext()

    private var listener: Listener? = null

    constructor(activity: Activity) :
            this(ActivityContextProvider(activity))

    constructor(fragment: Fragment) :
            this(FragmentContextProvider(fragment))

    /**
     * Performs ACTION_OPEN_DOCUMENT_TREE intent to select backup directory URI.
     * If the directory is already selected and valid, the request will be skipped.
     * @param force if true, the previous selection is ignored and the user is requested to select another directory.
     * @param onSelectedListener an optional action to perform once the directory is selected.
     */
    fun selectBackupDir(force: Boolean, onSelectedListener: Listener? = null) {
        if (!force) {
            val dirUri = BackupUtil.getSelectedBackupDirIfValid(context)
            if (dirUri != null && onSelectedListener != null) {
                onSelectedListener.onBackupDirSelected(dirUri)
            }
            return
        }

        // Let user pick the dir.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

        // Request read/write permission grant for the dir.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

        // Set the default dir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val dirUri = BackupUtil.getBackupDirUri(context)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, dirUri
                    ?: Uri.fromFile(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)))
        }

        if (onSelectedListener != null) {
            this.listener = onSelectedListener
        }

        contextProvider.startActivityForResult(intent, REQUEST_CODE_SAVE_DIR)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE_SAVE_DIR) return

        if (resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            // Acquire persistent access permissions for the file selected.
            val persistentFlags: Int = data.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context.contentResolver.takePersistableUriPermission(data.data!!, persistentFlags)

            BackupUtil.setBackupDirUri(context, data.dataString)

            listener?.onBackupDirSelected(data.data!!)
        }

        listener = null
    }

    @FunctionalInterface
    interface Listener {
        fun onBackupDirSelected(uri: Uri)
    }
}