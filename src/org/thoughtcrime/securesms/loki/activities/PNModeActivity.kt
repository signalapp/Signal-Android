package org.thoughtcrime.securesms.loki.activities

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.activity_display_name.registerButton
import kotlinx.android.synthetic.main.activity_pn_mode.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.loki.utilities.show
import org.thoughtcrime.securesms.util.TextSecurePreferences

class PNModeActivity : BaseActionBarActivity() {
    private var selectedOptionView: LinearLayout? = null

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        setContentView(R.layout.activity_pn_mode)
        fcmOptionView.setOnClickListener { toggleFCM() }
        backgroundPollingOptionView.setOnClickListener { toggleBackgroundPolling() }
        registerButton.setOnClickListener { register() }
    }
    // endregion

    // region Animation
    private fun performTransition(@DrawableRes transitionID: Int, subject: View) {
        val drawable = resources.getDrawable(transitionID, theme) as TransitionDrawable
        subject.background = drawable
        drawable.startTransition(250)
    }
    // endregion

    // region Interaction
    private fun toggleFCM() {
        when (selectedOptionView) {
            null -> {
                performTransition(R.drawable.pn_option_background_select_transition, fcmOptionView)
                selectedOptionView = fcmOptionView
            }
            fcmOptionView -> {
                performTransition(R.drawable.pn_option_background_deselect_transition, fcmOptionView)
                selectedOptionView = null
            }
            backgroundPollingOptionView -> {
                performTransition(R.drawable.pn_option_background_select_transition, fcmOptionView)
                performTransition(R.drawable.pn_option_background_deselect_transition, backgroundPollingOptionView)
                selectedOptionView = fcmOptionView
            }
        }
    }

    private fun toggleBackgroundPolling() {
        when (selectedOptionView) {
            null -> {
                performTransition(R.drawable.pn_option_background_select_transition, backgroundPollingOptionView)
                selectedOptionView = backgroundPollingOptionView
            }
            backgroundPollingOptionView -> {
                performTransition(R.drawable.pn_option_background_deselect_transition, backgroundPollingOptionView)
                selectedOptionView = null
            }
            fcmOptionView -> {
                performTransition(R.drawable.pn_option_background_select_transition, backgroundPollingOptionView)
                performTransition(R.drawable.pn_option_background_deselect_transition, fcmOptionView)
                selectedOptionView = backgroundPollingOptionView
            }
        }
    }

    private fun register() {
        if (selectedOptionView == null) {
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle(R.string.activity_pn_mode_no_option_picked_dialog_title)
            dialog.setPositiveButton(R.string.ok) { _, _ -> }
            dialog.create().show()
            return
        }
        val displayName = TextSecurePreferences.getProfileName(this)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, true)
        TextSecurePreferences.setPromptedPushRegistration(this, true)
        TextSecurePreferences.setIsUsingFCM(this, (selectedOptionView == fcmOptionView))
        TextSecurePreferences.setHasSeenPNModeSheet(this, true) // Shouldn't be shown to users who've done the new onboarding
        val application = ApplicationContext.getInstance(this)
        application.setUpStorageAPIIfNeeded()
        application.setUpP2PAPI()
        val publicChatAPI = ApplicationContext.getInstance(this).lokiPublicChatAPI
        if (publicChatAPI != null) {
            // TODO: This won't be necessary anymore when we don't auto-join the Loki Public Chat anymore
            application.createDefaultPublicChatsIfNeeded()
            val servers = DatabaseFactory.getLokiThreadDatabase(this).getAllPublicChatServers()
            servers.forEach { publicChatAPI.setDisplayName(displayName, it) }
        }
        application.registerForFCMIfNeeded(true)
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        show(intent)
    }
    // endregion
}