package org.thoughtcrime.securesms.loki.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_landing.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.loki.utilities.push
import org.thoughtcrime.securesms.loki.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.util.TextSecurePreferences

class LandingActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)
        setUpActionBarSessionLogo(true)
        fakeChatView.startAnimating()
        registerButton.setOnClickListener { register() }
        restoreButton.setOnClickListener { restoreFromRecoveryPhrase() }
        restoreBackupButton.setOnClickListener { restoreFromBackup() }
        if (TextSecurePreferences.getWasUnlinked(this)) {
            Toast.makeText(this, R.string.activity_landing_device_unlinked_dialog_title, Toast.LENGTH_LONG).show()
        }
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
        TextSecurePreferences.setPromptedPushRegistration(this, false)
        val application = ApplicationContext.getInstance(this)
        application.stopPolling()
    }
}