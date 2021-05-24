package org.thoughtcrime.securesms.loki.activities

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.activity_settings.*
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ProfilePictureUtilities
import org.session.libsession.utilities.SSKEnvironment.ProfileManagerProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.ProfileKeyUtil
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.avatar.AvatarSelection
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.dialogs.ChangeUiModeDialog
import org.thoughtcrime.securesms.loki.dialogs.ClearAllDataDialog
import org.thoughtcrime.securesms.loki.dialogs.SeedDialog
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities
import org.thoughtcrime.securesms.loki.utilities.push
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.profiles.ProfileMediaConstraints
import org.thoughtcrime.securesms.util.BitmapDecodingException
import org.thoughtcrime.securesms.util.BitmapUtil
import java.io.File
import java.security.SecureRandom
import java.util.*

class SettingsActivity : PassphraseRequiredActionBarActivity() {
    private var displayNameEditActionMode: ActionMode? = null
        set(value) { field = value; handleDisplayNameEditActionModeChanged() }
    private lateinit var glide: GlideRequests
    private var displayNameToBeUploaded: String? = null
    private var profilePictureToBeUploaded: ByteArray? = null
    private var tempFile: File? = null

    private val hexEncodedPublicKey: String
        get() {
            return TextSecurePreferences.getLocalNumber(this)!!
        }

    companion object {
        const val updatedProfileResultCode = 1234
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_settings)
        val displayName = TextSecurePreferences.getProfileName(this) ?: hexEncodedPublicKey
        glide = GlideApp.with(this)
        profilePictureView.glide = glide
        profilePictureView.publicKey = hexEncodedPublicKey
        profilePictureView.displayName = displayName
        profilePictureView.isLarge = true
        profilePictureView.update()
        profilePictureView.setOnClickListener { showEditProfilePictureUI() }
        ctnGroupNameSection.setOnClickListener { startActionMode(DisplayNameEditActionModeCallback()) }
        btnGroupNameDisplay.text = displayName
        publicKeyTextView.text = hexEncodedPublicKey
        copyButton.setOnClickListener { copyPublicKey() }
        shareButton.setOnClickListener { sharePublicKey() }
        privacyButton.setOnClickListener { showPrivacySettings() }
        notificationsButton.setOnClickListener { showNotificationSettings() }
        chatsButton.setOnClickListener { showChatSettings() }
        sendInvitationButton.setOnClickListener { sendInvitation() }
        helpTranslateButton.setOnClickListener { helpTranslate() }
        seedButton.setOnClickListener { showSeed() }
        clearAllDataButton.setOnClickListener { clearAllData() }
        versionTextView.text = String.format(getString(R.string.version_s), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_general, menu)
        // Update UI mode menu icon
        val uiMode = UiModeUtilities.getUserSelectedUiMode(this)
        menu.findItem(R.id.action_change_theme).icon!!.level = uiMode.ordinal
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_qr_code -> {
                showQRCode()
                true
            }
            R.id.action_change_theme -> {
                ChangeUiModeDialog().show(supportFragmentManager, ChangeUiModeDialog.TAG)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            AvatarSelection.REQUEST_CODE_AVATAR -> {
                if (resultCode != Activity.RESULT_OK) {
                    return
                }
                val outputFile = Uri.fromFile(File(cacheDir, "cropped"))
                var inputFile: Uri? = data?.data
                if (inputFile == null && tempFile != null) {
                    inputFile = Uri.fromFile(tempFile)
                }
                AvatarSelection.circularCropImage(this, inputFile, outputFile, R.string.CropImageActivity_profile_avatar)
            }
            AvatarSelection.REQUEST_CODE_CROP_IMAGE -> {
                if (resultCode != Activity.RESULT_OK) {
                    return
                }
                AsyncTask.execute {
                    try {
                        profilePictureToBeUploaded = BitmapUtil.createScaledBytes(this@SettingsActivity, AvatarSelection.getResultUri(data), ProfileMediaConstraints()).bitmap
                        Handler(Looper.getMainLooper()).post {
                            updateProfile(true)
                        }
                    } catch (e: BitmapDecodingException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }
    // endregion

    // region Updating
    private fun handleDisplayNameEditActionModeChanged() {
        val isEditingDisplayName = this.displayNameEditActionMode !== null

        btnGroupNameDisplay.visibility = if (isEditingDisplayName) View.INVISIBLE else View.VISIBLE
        displayNameEditText.visibility = if (isEditingDisplayName) View.VISIBLE else View.INVISIBLE

        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isEditingDisplayName) {
            displayNameEditText.setText(btnGroupNameDisplay.text)
            displayNameEditText.selectAll()
            displayNameEditText.requestFocus()
            inputMethodManager.showSoftInput(displayNameEditText, 0)
        } else {
            inputMethodManager.hideSoftInputFromWindow(displayNameEditText.windowToken, 0)
        }
    }

    private fun updateProfile(isUpdatingProfilePicture: Boolean) {
        loader.isVisible = true
        val promises = mutableListOf<Promise<*, Exception>>()
        val displayName = displayNameToBeUploaded
        if (displayName != null) {
            TextSecurePreferences.setProfileName(this, displayName)
        }
        val profilePicture = profilePictureToBeUploaded
        val encodedProfileKey = ProfileKeyUtil.generateEncodedProfileKey(this)
        if (isUpdatingProfilePicture && profilePicture != null) {
            promises.add(ProfilePictureUtilities.upload(profilePicture, encodedProfileKey, this))
        }
        val compoundPromise = all(promises)
        compoundPromise.successUi { // Do this on the UI thread so that it happens before the alwaysUi clause below
            if (isUpdatingProfilePicture && profilePicture != null) {
                AvatarHelper.setAvatar(this, Address.fromSerialized(TextSecurePreferences.getLocalNumber(this)!!), profilePicture)
                TextSecurePreferences.setProfileAvatarId(this, SecureRandom().nextInt())
                TextSecurePreferences.setLastProfilePictureUpload(this, Date().time)
                ProfileKeyUtil.setEncodedProfileKey(this, encodedProfileKey)
            }
            if (profilePicture != null || displayName != null) {
                MultiDeviceProtocol.forceSyncConfigurationNowIfNeeded(this@SettingsActivity)
            }
        }
        compoundPromise.alwaysUi {
            if (displayName != null) {
                btnGroupNameDisplay.text = displayName
            }
            if (isUpdatingProfilePicture && profilePicture != null) {
                profilePictureView.recycle() // Clear the cached image before updating
                profilePictureView.update()
            }
            displayNameToBeUploaded = null
            profilePictureToBeUploaded = null
            loader.isVisible = false
        }
    }
    // endregion

    // region Interaction

    /**
     * @return true if the update was successful.
     */
    private fun saveDisplayName(): Boolean {
        val displayName = displayNameEditText.text.toString().trim()
        if (displayName.isEmpty()) {
            Toast.makeText(this, R.string.activity_settings_display_name_missing_error, Toast.LENGTH_SHORT).show()
            return false
        }
        if (displayName.toByteArray().size > ProfileManagerProtocol.Companion.NAME_PADDED_LENGTH) {
            Toast.makeText(this, R.string.activity_settings_display_name_too_long_error, Toast.LENGTH_SHORT).show()
            return false
        }
//        isEditingDisplayName = false
        displayNameToBeUploaded = displayName
        updateProfile(false)
        return true
    }

    private fun showQRCode() {
        val intent = Intent(this, QRCodeActivity::class.java)
        push(intent)
    }

    private fun showEditProfilePictureUI() {
        // Ask for an optional camera permission.
        Permissions.with(this)
                .request(Manifest.permission.CAMERA)
                .onAnyResult {
                    tempFile = AvatarSelection.startAvatarSelection(this, false, true)
                }
                .execute()
    }

    private fun copyPublicKey() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Session ID", hexEncodedPublicKey)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun sharePublicKey() {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.putExtra(Intent.EXTRA_TEXT, hexEncodedPublicKey)
        intent.type = "text/plain"
        startActivity(intent)
    }

    private fun showPrivacySettings() {
        val intent = Intent(this, PrivacySettingsActivity::class.java)
        push(intent)
    }

    private fun showNotificationSettings() {
        val intent = Intent(this, NotificationSettingsActivity::class.java)
        push(intent)
    }

    private fun showChatSettings() {
        val intent = Intent(this, ChatSettingsActivity::class.java)
        push(intent)
    }

    private fun sendInvitation() {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        val invitation = "Hey, I've been using Session to chat with complete privacy and security. Come join me! Download it at https://getsession.org/. My Session ID is $hexEncodedPublicKey!"
        intent.putExtra(Intent.EXTRA_TEXT, invitation)
        intent.type = "text/plain"
        startActivity(intent)
    }

    private fun helpTranslate() {
        try {
            val url = "https://crowdin.com/project/session-android"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open URL", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSeed() {
        SeedDialog().show(supportFragmentManager, "Recovery Phrase Dialog")
    }

    private fun clearAllData() {
        ClearAllDataDialog().show(supportFragmentManager, "Clear All Data Dialog")
    }
    // endregion

    private inner class DisplayNameEditActionModeCallback: ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.title = getString(R.string.activity_settings_display_name_edit_text_hint)
            mode.menuInflater.inflate(R.menu.menu_apply, menu)
            this@SettingsActivity.displayNameEditActionMode = mode
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            this@SettingsActivity.displayNameEditActionMode = null
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.applyButton -> {
                    if (this@SettingsActivity.saveDisplayName()) {
                        mode.finish()
                    }
                    return true
                }
            }
            return false;
        }
    }
}