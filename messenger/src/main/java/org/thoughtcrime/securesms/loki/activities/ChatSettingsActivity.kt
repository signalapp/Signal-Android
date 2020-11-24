package org.thoughtcrime.securesms.loki.activities

import android.os.Bundle
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.preferences.ChatsPreferenceFragment

class ChatSettingsActivity : PassphraseRequiredActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_fragment_wrapper)
        supportActionBar!!.title = resources.getString(R.string.activity_chat_settings_title)
        val fragment = ChatsPreferenceFragment()
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentContainer, fragment)
        transaction.commit()
    }
}