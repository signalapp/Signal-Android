package org.thoughtcrime.securesms.loki.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.session.libsession.utilities.IdentityKeyUtil
import org.thoughtcrime.securesms.loki.utilities.push
import org.thoughtcrime.securesms.loki.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.loki.views.FakeChatView
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.Util

import org.session.libsession.utilities.TextSecurePreferences

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
        TextSecurePreferences.setLastExperienceVersionCode(this, Util.getCanonicalVersionCode())
        TextSecurePreferences.setPasswordDisabled(this, true)
        TextSecurePreferences.setReadReceiptsEnabled(this, true)
        TextSecurePreferences.setTypingIndicatorsEnabled(this, true)
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