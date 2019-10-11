package org.thoughtcrime.securesms.loki

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_account_details.*
import kotlinx.android.synthetic.main.fragment_add_public_chat.*
import network.loki.messenger.R
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.ConversationActivity
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.LokiGroupChatAPI
import org.whispersystems.signalservice.loki.utilities.Analytics
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation

class AddPublicChatActivity : PassphraseRequiredActionBarActivity() {
    private val dynamicTheme = DynamicTheme()

    override fun onPreCreate() {
        dynamicTheme.onCreate(this)
    }

    override fun onCreate(bundle: Bundle?, isReady: Boolean) {
        supportActionBar!!.setTitle(R.string.fragment_add_public_chat_title)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        setContentView(R.layout.fragment_add_public_chat)
        setButtonEnabled(true)
        addButton.setOnClickListener { addPublicChatIfPossible() }
    }

    public override fun onResume() {
        super.onResume()
        dynamicTheme.onResume(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun addPublicChatIfPossible() {
        val inputMethodManager = getSystemService(BaseActionBarActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(serverUrlEditText.windowToken, 0)

        val url = serverUrlEditText.text.toString().toLowerCase().replace("http://", "https://")
        if (!Patterns.WEB_URL.matcher(url).matches() || !url.startsWith("https://")) { return Toast.makeText(this, R.string.fragment_add_public_chat_invalid_url_message, Toast.LENGTH_SHORT).show() }

        setButtonEnabled(false)

        ApplicationContext.getInstance(this).lokiPublicChatManager.addChat(url, 1).successUi {
            Toast.makeText(this, R.string.fragment_add_public_chat_success_message, Toast.LENGTH_SHORT).show()
            finish()
        }.failUi {
            setButtonEnabled(true)
            Toast.makeText(this, R.string.fragment_add_public_chat_failed_connect_message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setButtonEnabled(enabled: Boolean) {
        addButton.isEnabled = enabled
        val text = if (enabled) R.string.fragment_add_public_chat_add_button_title else R.string.fragment_add_public_chat_adding_server_button_title
        addButton.setText(text)
        serverUrlEditText.isEnabled = enabled
    }
}