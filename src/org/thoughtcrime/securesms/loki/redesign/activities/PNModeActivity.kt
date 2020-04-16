package org.thoughtcrime.securesms.loki.redesign.activities

import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.activity_display_name.registerButton
import kotlinx.android.synthetic.main.activity_pn_mode.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.redesign.utilities.setUpActionBarSessionLogo

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
        // TODO: Implement
    }
    // endregion
}