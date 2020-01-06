package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_settings.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.TextSecurePreferences

class SettingsActivity : PassphraseRequiredActionBarActivity() {
    private lateinit var glide: GlideRequests

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
        // Set title
        supportActionBar!!.title = "Settings"
        // Set up Glide
        glide = GlideApp.with(this)
        // Set up profile picture view
        profilePictureView.glide = glide
        profilePictureView.hexEncodedPublicKey = hexEncodedPublicKey
        profilePictureView.isLarge = true
        profilePictureView.update()
        // Set up display name text view
        displayNameTextView.text = DatabaseFactory.getLokiUserDatabase(this).getDisplayName(hexEncodedPublicKey)
        // Set up public key text view
        publicKeyTextView.text = hexEncodedPublicKey
        // Set up copy button
        copyButton.setOnClickListener { copyPublicKey() }
        // Set up share button
        shareButton.setOnClickListener { sharePublicKey() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.showQRCodeItem -> showQRCode()
            else -> { /* Do nothing */ }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showQRCode() {
        // TODO: Implement
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