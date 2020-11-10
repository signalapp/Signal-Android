package org.thoughtcrime.securesms.loki.activities

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.common.util.Strings
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityBackupRestoreBinding
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.backup.FullBackupImporter
import org.thoughtcrime.securesms.backup.FullBackupImporter.DatabaseDowngradeException
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.loki.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.loki.utilities.show
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.IOException

class BackupRestoreActivity : BaseActionBarActivity() {

    companion object {
        private const val TAG = "BackupRestoreActivity"
        private const val REQUEST_CODE_BACKUP_FILE = 779955
    }

    private val viewModel by viewModels<BackupRestoreViewModel>()

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        val dataBinding = DataBindingUtil.setContentView<ActivityBackupRestoreBinding>(this, R.layout.activity_backup_restore)
        dataBinding.lifecycleOwner = this
        dataBinding.viewModel = viewModel
//        setContentView(R.layout.activity_backup_restore)

        dataBinding.restoreButton.setOnClickListener { restore() }

        dataBinding.buttonSelectFile.setOnClickListener {
            // Let user pick a file.
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
//                type = BackupUtil.BACKUP_FILE_MIME_TYPE
                type = "*/*"
            }
            startActivityForResult(intent, REQUEST_CODE_BACKUP_FILE)
        }

        dataBinding.backupCode.addTextChangedListener { text -> viewModel.backupPassphrase.value = text.toString() }

        //region Legal info views
        val termsExplanation = SpannableStringBuilder("By using this service, you agree to our Terms of Service and Privacy Policy")
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 40, 56, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(object : ClickableSpan() {

            override fun onClick(widget: View) {
                openURL("https://getsession.org/terms-of-service/")
            }
        }, 40, 56, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(StyleSpan(Typeface.BOLD), 61, 75, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsExplanation.setSpan(object : ClickableSpan() {

            override fun onClick(widget: View) {
                openURL("https://getsession.org/privacy-policy/")
            }
        }, 61, 75, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        dataBinding.termsTextView.movementMethod = LinkMovementMethod.getInstance()
        dataBinding.termsTextView.text = termsExplanation
        //endregion
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_BACKUP_FILE -> {
                if (resultCode == Activity.RESULT_OK && data != null && data.data != null) {
//                  // Acquire persistent access permissions for the file selected.
//                  val persistentFlags: Int = data.flags and
//                          (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
//                  context.contentResolver.takePersistableUriPermission(data.data!!, persistentFlags)

                    viewModel.onBackupFileSelected(data.data!!)
                }
            }
        }

    }
    // endregion

    // region Interaction
    private fun restore() {
        if (viewModel.backupFile.value == null && Strings.isEmptyOrWhitespace(viewModel.backupPassphrase.value)) return

        val backupFile = viewModel.backupFile.value!!
        val passphrase = viewModel.backupPassphrase.value!!.trim()

        object : AsyncTask<Void?, Void?, BackupImportResult>() {
            override fun doInBackground(vararg params: Void?): BackupImportResult {
                return try {
                    val context: Context = this@BackupRestoreActivity
                    val database = DatabaseFactory.getBackupDatabase(context)
                    FullBackupImporter.importFromUri(
                            context,
                            AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                            DatabaseFactory.getBackupDatabase(context),
                            backupFile,
                            passphrase
                    )
                    DatabaseFactory.upgradeRestored(context, database)
                    NotificationChannels.restoreContactNotificationChannels(context)
                    TextSecurePreferences.setRestorationTime(context, System.currentTimeMillis())

                    BackupImportResult.SUCCESS
                } catch (e: DatabaseDowngradeException) {
                    Log.w(TAG, "Failed due to the backup being from a newer version of Signal.", e)
                    BackupImportResult.FAILURE_VERSION_DOWNGRADE
                } catch (e: IOException) {
                    Log.w(TAG, e)
                    BackupImportResult.FAILURE_UNKNOWN
                }
            }

            override fun onPostExecute(result: BackupImportResult) {
                val context = this@BackupRestoreActivity
                when (result) {
                    BackupImportResult.SUCCESS -> {
                        TextSecurePreferences.setHasViewedSeed(context, true)
                        TextSecurePreferences.setHasSeenWelcomeScreen(context, true)
                        TextSecurePreferences.setPromptedPushRegistration(context, true)
                        TextSecurePreferences.setHasSeenMultiDeviceRemovalSheet(context)
                        TextSecurePreferences.setHasSeenLightThemeIntroSheet(context)
                        val application = ApplicationContext.getInstance(context)
                        application.setUpStorageAPIIfNeeded()
                        application.setUpP2PAPIIfNeeded()

                        HomeActivity.requestResetAllSessionsOnStartup(context)

                        val intent = Intent(context, HomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        show(intent)
                    }
                    BackupImportResult.FAILURE_VERSION_DOWNGRADE ->
                        Toast.makeText(context, R.string.RegistrationActivity_backup_failure_downgrade, Toast.LENGTH_LONG).show()
                    BackupImportResult.FAILURE_UNKNOWN ->
                        Toast.makeText(context, R.string.RegistrationActivity_incorrect_backup_passphrase, Toast.LENGTH_LONG).show()
                }
            }
        }.execute()
    }

    private fun openURL(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
        }
    }


    enum class BackupImportResult {
        SUCCESS, FAILURE_VERSION_DOWNGRADE, FAILURE_UNKNOWN
    }
    // endregion
}

class BackupRestoreViewModel(application: Application): AndroidViewModel(application) {

    companion object {
        @JvmStatic
        fun uriToFileName(view: View, fileUri: Uri?): String? {
            fileUri ?: return null

            view.context.contentResolver.query(fileUri, null, null, null, null).use {
                val nameIndex = it!!.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                it.moveToFirst()
                return it.getString(nameIndex)
            }
        }

        @JvmStatic
        fun validateData(fileUri: Uri?, passphrase: String?): Boolean {
            return fileUri != null && !Strings.isEmptyOrWhitespace(passphrase)
        }
    }

    val backupFile = MutableLiveData<Uri>()
    val backupPassphrase = MutableLiveData<String>("000000000000000000000000000000")

    fun onBackupFileSelected(backupFile: Uri) {
        //TODO Check if backup file is correct.
        this.backupFile.value = backupFile
    }
}