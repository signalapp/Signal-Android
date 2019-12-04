package org.thoughtcrime.securesms.loki

import android.os.Bundle
import android.util.Patterns
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_add_public_chat.*
import network.loki.messenger.R
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.TextSecurePreferences

class AddPublicChatActivity : PassphraseRequiredActionBarActivity() {
    private val dynamicTheme = DynamicTheme()

    override fun onPreCreate() {
        dynamicTheme.onCreate(this)
    }

    override fun onCreate(bundle: Bundle?, isReady: Boolean) {
        supportActionBar!!.setTitle(R.string.fragment_add_public_chat_title)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        setContentView(R.layout.activity_add_public_chat)
        updateUI(false)
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
        inputMethodManager.hideSoftInputFromWindow(urlEditText.windowToken, 0)
        val url = urlEditText.text.toString().toLowerCase().replace("http://", "https://")
        if (!Patterns.WEB_URL.matcher(url).matches() || !url.startsWith("https://")) {
            return Toast.makeText(this, R.string.fragment_add_public_chat_invalid_url_message, Toast.LENGTH_SHORT).show()
        }
        updateUI(true)
        val application = ApplicationContext.getInstance(this)
        val channel: Long = 1
        val displayName = TextSecurePreferences.getProfileName(this)
        val lokiPublicChatAPI = application.lokiPublicChatAPI!!
        application.lokiPublicChatManager.addChat(url, channel).successUi {
            lokiPublicChatAPI.getMessages(channel, url)
            lokiPublicChatAPI.setDisplayName(displayName, url)
            val profileKey: ByteArray = ProfileKeyUtil.getProfileKey(this)
            val profileUrl: String? = TextSecurePreferences.getProfileAvatarUrl(this)
            lokiPublicChatAPI.setProfilePicture(url, profileKey, profileUrl)
            finish()
        }.failUi {
            updateUI(false)
            Toast.makeText(this, R.string.fragment_add_public_chat_connection_failed_message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(isConnecting: Boolean) {
        addButton.isEnabled = !isConnecting
        val text = if (isConnecting) R.string.fragment_add_public_chat_add_button_title_2 else R.string.fragment_add_public_chat_add_button_title_1
        addButton.setText(text)
        urlEditText.isEnabled = !isConnecting
    }
}