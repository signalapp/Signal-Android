package org.thoughtcrime.securesms.loki.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.loki.utilities.push
import org.thoughtcrime.securesms.loki.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.loki.views.FakeChatView
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util

class LandingActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)
        setUpActionBarSessionLogo(true)
        findViewById<FakeChatView>(R.id.fakeChatView).startAnimating()

        findViewById<View>(R.id.registerButton).setOnClickListener { register() }
        findViewById<View>(R.id.restoreButton).setOnClickListener { restoreFromRecoveryPhrase() }
        findViewById<View>(R.id.restoreBackupButton).setOnClickListener { restoreFromBackup() }

        if (TextSecurePreferences.getWasUnlinked(this)) {
            Toast.makeText(this, R.string.activity_landing_device_unlinked_dialog_title, Toast.LENGTH_LONG).show()
        }

        // Setup essentials for a new user.
        IdentityKeyUtil.generateIdentityKeyPair(this)

        TextSecurePreferences.setLastExperienceVersionCode(this, Util.getCanonicalVersionCode())
        TextSecurePreferences.setPasswordDisabled(this, true)
        TextSecurePreferences.setReadReceiptsEnabled(this, true)
        TextSecurePreferences.setTypingIndicatorsEnabled(this, true)

        //AC: This is a temporary workaround to trick the old code that the screen is unlocked.
        KeyCachingService.setMasterSecret(applicationContext, Object())
    }

    private fun register() {
        val intent = Intent(this, RegisterActivity::class.java)
        push(intent)
    }

    private fun restoreFromRecoveryPhrase() {
        val intent = Intent(this, RecoveryPhraseRestoreActivity::class.java)
        push(intent)
    }

    private fun restoreFromBackup() {
        val intent = Intent(this, BackupRestoreActivity::class.java)
        push(intent)
    }

    private fun reset() {
        IdentityKeyUtil.delete(this, IdentityKeyUtil.LOKI_SEED)
        TextSecurePreferences.removeLocalNumber(this)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, false)
        val application = ApplicationContext.getInstance(this)
        application.stopPolling()
    }
}