package org.thoughtcrime.securesms.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.service.KeyCachingService

class LandingActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)
        setUpActionBarSessionLogo(true)
        findViewById<FakeChatView>(R.id.fakeChatView).startAnimating()
        findViewById<View>(R.id.registerButton).setOnClickListener { register() }
        findViewById<View>(R.id.restoreButton).setOnClickListener { restore() }
        findViewById<View>(R.id.linkButton).setOnClickListener { link() }
        IdentityKeyUtil.generateIdentityKeyPair(this)
        TextSecurePreferences.setPasswordDisabled(this, true)
        // AC: This is a temporary workaround to trick the old code that the screen is unlocked.
        KeyCachingService.setMasterSecret(applicationContext, Object())
    }

    private fun register() {
        val intent = Intent(this, RegisterActivity::class.java)
        push(intent)
    }

    private fun restore() {
        val intent = Intent(this, RecoveryPhraseRestoreActivity::class.java)
        push(intent)
    }

    private fun link() {
        val intent = Intent(this, LinkDeviceActivity::class.java)
        push(intent)
    }
}