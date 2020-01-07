package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_settings.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.redesign.utilities.push
import org.thoughtcrime.securesms.loki.toPx
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.TextSecurePreferences

class SettingsActivity : PassphraseRequiredActionBarActivity() {
    private lateinit var glide: GlideRequests
    private var isEditingDisplayName = false
        set(value) { field = value; handleIsEditingDisplayNameChanged() }
    private var displayNameToBeUploaded: String? = null

    private val hexEncodedPublicKey: String
        get() {
            val masterHexEncodedPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(this)
            val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this)
            return masterHexEncodedPublicKey ?: userHexEncodedPublicKey
        }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        // Set content view
        setContentView(R.layout.activity_settings)
        // Set custom toolbar
        setSupportActionBar(toolbar)
        cancelButton.setOnClickListener { cancelEditingDisplayName() }
        saveButton.setOnClickListener { saveDisplayName() }
        showQRCodeButton.setOnClickListener { showQRCode() }
        // Set up Glide
        glide = GlideApp.with(this)
        // Set up profile picture view
        profilePictureView.glide = glide
        profilePictureView.hexEncodedPublicKey = hexEncodedPublicKey
        profilePictureView.isLarge = true
        profilePictureView.update()
        // Set up display name container
        displayNameContainer.setOnClickListener { showEditDisplayNameUI() }
        // Set up display name text view
        displayNameTextView.text = DatabaseFactory.getLokiUserDatabase(this).getDisplayName(hexEncodedPublicKey)
        // Set up public key text view
        publicKeyTextView.text = hexEncodedPublicKey
        // Set up copy button
        copyButton.setOnClickListener { copyPublicKey() }
        // Set up share button
        shareButton.setOnClickListener { sharePublicKey() }
    }
    // endregion

    // region Updating
    private fun handleIsEditingDisplayNameChanged() {
        cancelButton.visibility = if (isEditingDisplayName) View.VISIBLE else View.GONE
        showQRCodeButton.visibility = if (isEditingDisplayName) View.GONE else View.VISIBLE
        saveButton.visibility = if (isEditingDisplayName) View.VISIBLE else View.GONE
        displayNameTextView.visibility = if (isEditingDisplayName) View.INVISIBLE else View.VISIBLE
        displayNameEditText.visibility = if (isEditingDisplayName) View.VISIBLE else View.INVISIBLE
        val titleTextViewLayoutParams = titleTextView.layoutParams as LinearLayout.LayoutParams
        titleTextViewLayoutParams.leftMargin = if (isEditingDisplayName) toPx(16, resources) else 0
        titleTextView.layoutParams = titleTextViewLayoutParams
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isEditingDisplayName) {
            displayNameEditText.requestFocus()
            inputMethodManager.showSoftInput(displayNameEditText, 0)
        } else {
            inputMethodManager.hideSoftInputFromWindow(displayNameEditText.windowToken, 0)
        }
    }

    private fun updateProfile(isUpdatingDisplayName: Boolean, isUpdatingProfilePicture: Boolean) {
        val displayName = displayNameToBeUploaded ?: TextSecurePreferences.getProfileName(this)
        TextSecurePreferences.setProfileName(this, displayName)
        val publicChatAPI = ApplicationContext.getInstance(this).lokiPublicChatAPI
        if (publicChatAPI != null) {
            val servers = DatabaseFactory.getLokiThreadDatabase(this).getAllPublicChatServers()
            for (server in servers) {
                publicChatAPI.setDisplayName(displayName, server)
            }
        }
        displayNameTextView.text = displayName
        displayNameToBeUploaded = null
    }
    // endregion

    // region Interaction
    private fun cancelEditingDisplayName() {
        isEditingDisplayName = false
    }

    private fun saveDisplayName() {
        val displayName = displayNameEditText.text.trim().toString()
        // TODO: Validation
        isEditingDisplayName = false
        displayNameToBeUploaded = displayName
        updateProfile(true, false)
    }

    private fun showQRCode() {
        val intent = Intent(this, QRCodeActivity::class.java)
        push(intent)
    }

    private fun showEditDisplayNameUI() {
        isEditingDisplayName = true
    }

    private fun copyPublicKey() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Session ID", hexEncodedPublicKey)
        clipboard.primaryClip = clip
        Toast.makeText(this, R.string.activity_register_public_key_copied_message, Toast.LENGTH_SHORT).show()
    }

    private fun sharePublicKey() {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.putExtra(Intent.EXTRA_TEXT, hexEncodedPublicKey)
        intent.type = "text/plain"
        startActivity(intent)
    }
    // endregion
}