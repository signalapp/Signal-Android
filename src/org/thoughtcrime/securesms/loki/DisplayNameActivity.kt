package org.thoughtcrime.securesms.loki

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import kotlinx.android.synthetic.main.activity_display_name.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.ConversationListActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.crypto.ProfileCipher
import org.whispersystems.signalservice.loki.utilities.Analytics

class DisplayNameActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_name)
        nextButton.setOnClickListener { continueIfPossible() }
        Analytics.shared.track("Display Name Screen Viewed")
    }

    private fun continueIfPossible() {
        val name = nameEditText.text.toString()
        if (name.isEmpty()) {
            return nameEditText.input.setError("Invalid")
        }
        if (name.toByteArray().size > ProfileCipher.NAME_PADDED_LENGTH) {
            return nameEditText.input.setError("Too Long")
        } else {
            Analytics.shared.track("Display Name Updated")
            TextSecurePreferences.setProfileName(this, name)
        }
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(nameEditText.windowToken, 0)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, true)
        TextSecurePreferences.setPromptedPushRegistration(this, true)
        val application = ApplicationContext.getInstance(this)
        application.setUpP2PAPI()
        application.startLongPollingIfNeeded()
        application.setUpStorageAPIIfNeeded()
        startActivity(Intent(this, ConversationListActivity::class.java))
        finish()
        val publicChatAPI = ApplicationContext.getInstance(this).lokiPublicChatAPI
        if (publicChatAPI != null) {
            val servers = DatabaseFactory.getLokiThreadDatabase(this).getAllPublicChatServers()
            servers.forEach { publicChatAPI.setDisplayName(name, it) }
        }
    }
}