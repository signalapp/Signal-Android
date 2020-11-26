package org.thoughtcrime.securesms.loki.activities

import android.os.Bundle
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.preferences.AppProtectionPreferenceFragment

class PrivacySettingsActivity : PassphraseRequiredActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_fragment_wrapper)
        val fragment = AppProtectionPreferenceFragment()
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentContainer, fragment)
        transaction.commit()
    }
}