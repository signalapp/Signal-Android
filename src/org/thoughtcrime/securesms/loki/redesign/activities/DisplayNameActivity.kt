package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_display_name_v2.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.redesign.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.loki.redesign.utilities.show
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.crypto.ProfileCipher

class DisplayNameActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        setContentView(R.layout.activity_display_name_v2)
        displayNameEditText.imeOptions = displayNameEditText.imeOptions or 16777216 // Always use incognito keyboard
        registerButton.setOnClickListener { register() }
    }

    private fun register() {
        val displayName = displayNameEditText.text.toString().trim()
        if (displayName.isEmpty()) {
            return Toast.makeText(this, "Please pick a display name", Toast.LENGTH_SHORT).show()
        }
        if (!displayName.matches(Regex("[a-zA-Z0-9_]+"))) {
            return Toast.makeText(this, "Please pick a display name that consists of only a-z, A-Z, 0-9 and _ characters", Toast.LENGTH_SHORT).show()
        }
        if (displayName.toByteArray().size > ProfileCipher.NAME_PADDED_LENGTH) {
            return Toast.makeText(this, "Please pick a shorter display name", Toast.LENGTH_SHORT).show()
        }
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(displayNameEditText.windowToken, 0)
        TextSecurePreferences.setProfileName(this, displayName)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, true)
        TextSecurePreferences.setPromptedPushRegistration(this, true)
        val application = ApplicationContext.getInstance(this)
        application.setUpStorageAPIIfNeeded()
        application.setUpP2PAPI()
        val publicChatAPI = ApplicationContext.getInstance(this).lokiPublicChatAPI
        if (publicChatAPI != null) {
            // TODO: This won't be necessary anymore when we don't auto-join the Loki Public Chat anymore
            application.createDefaultPublicChatsIfNeeded()
            val servers = DatabaseFactory.getLokiThreadDatabase(this).getAllPublicChatServers()
            servers.forEach { publicChatAPI.setDisplayName(displayName, it) }
        }
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        show(intent)
    }
}