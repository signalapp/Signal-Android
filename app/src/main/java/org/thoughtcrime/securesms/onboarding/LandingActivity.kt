package org.thoughtcrime.securesms.onboarding

import android.content.Intent
import android.os.Bundle
import network.loki.messenger.databinding.ActivityLandingBinding
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo

class LandingActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpActionBarSessionLogo(true)
        with(binding) {
            fakeChatView.startAnimating()
            registerButton.setOnClickListener { register() }
            restoreButton.setOnClickListener { restore() }
            linkButton.setOnClickListener { link() }
        }
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