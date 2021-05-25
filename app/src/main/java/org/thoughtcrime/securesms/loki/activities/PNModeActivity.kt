package org.thoughtcrime.securesms.loki.activities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import kotlinx.android.synthetic.main.activity_display_name.registerButton
import kotlinx.android.synthetic.main.activity_pn_mode.*
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.utilities.disableClipping
import org.thoughtcrime.securesms.loki.utilities.getColorWithID
import org.thoughtcrime.securesms.loki.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.loki.utilities.show
import org.thoughtcrime.securesms.loki.views.GlowViewUtilities
import org.thoughtcrime.securesms.loki.views.PNModeView

class PNModeActivity : BaseActionBarActivity() {
    private var selectedOptionView: PNModeView? = null

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo(true)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, true)
        setContentView(R.layout.activity_pn_mode)
        contentView.disableClipping()
        fcmOptionView.setOnClickListener { toggleFCM() }
        fcmOptionView.mainColor = resources.getColorWithID(R.color.pn_option_background, theme)
        fcmOptionView.strokeColor = resources.getColorWithID(R.color.pn_option_border, theme)
        backgroundPollingOptionView.setOnClickListener { toggleBackgroundPolling() }
        backgroundPollingOptionView.mainColor = resources.getColorWithID(R.color.pn_option_background, theme)
        backgroundPollingOptionView.strokeColor = resources.getColorWithID(R.color.pn_option_border, theme)
        registerButton.setOnClickListener { register() }
        toggleFCM()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_pn_mode, menu)
        return true
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
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when(id) {
            R.id.learnMoreButton -> learnMore()
            else -> { /* Do nothing */ }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun learnMore() {
        try {
            val url = "https://getsession.org/faq/#privacy"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFCM() {
        when (selectedOptionView) {
            null -> {
                performTransition(R.drawable.pn_option_background_select_transition, fcmOptionView)
                GlowViewUtilities.animateShadowColorChange(this, fcmOptionView, R.color.transparent, R.color.accent)
                animateStrokeColorChange(fcmOptionView, R.color.pn_option_border, R.color.accent)
                selectedOptionView = fcmOptionView
            }
            fcmOptionView -> {
                performTransition(R.drawable.pn_option_background_deselect_transition, fcmOptionView)
                GlowViewUtilities.animateShadowColorChange(this, fcmOptionView, R.color.accent, R.color.transparent)
                animateStrokeColorChange(fcmOptionView, R.color.accent, R.color.pn_option_border)
                selectedOptionView = null
            }
            backgroundPollingOptionView -> {
                performTransition(R.drawable.pn_option_background_select_transition, fcmOptionView)
                GlowViewUtilities.animateShadowColorChange(this, fcmOptionView, R.color.transparent, R.color.accent)
                animateStrokeColorChange(fcmOptionView, R.color.pn_option_border, R.color.accent)
                performTransition(R.drawable.pn_option_background_deselect_transition, backgroundPollingOptionView)
                GlowViewUtilities.animateShadowColorChange(this, backgroundPollingOptionView, R.color.accent, R.color.transparent)
                animateStrokeColorChange(backgroundPollingOptionView, R.color.accent, R.color.pn_option_border)
                selectedOptionView = fcmOptionView
            }
        }
    }

    private fun toggleBackgroundPolling() {
        when (selectedOptionView) {
            null -> {
                performTransition(R.drawable.pn_option_background_select_transition, backgroundPollingOptionView)
                GlowViewUtilities.animateShadowColorChange(this, backgroundPollingOptionView, R.color.transparent, R.color.accent)
                animateStrokeColorChange(backgroundPollingOptionView, R.color.pn_option_border, R.color.accent)
                selectedOptionView = backgroundPollingOptionView
            }
            backgroundPollingOptionView -> {
                performTransition(R.drawable.pn_option_background_deselect_transition, backgroundPollingOptionView)
                GlowViewUtilities.animateShadowColorChange(this, backgroundPollingOptionView, R.color.accent, R.color.transparent)
                animateStrokeColorChange(backgroundPollingOptionView, R.color.accent, R.color.pn_option_border)
                selectedOptionView = null
            }
            fcmOptionView -> {
                performTransition(R.drawable.pn_option_background_select_transition, backgroundPollingOptionView)
                GlowViewUtilities.animateShadowColorChange(this, backgroundPollingOptionView, R.color.transparent, R.color.accent)
                animateStrokeColorChange(backgroundPollingOptionView, R.color.pn_option_border, R.color.accent)
                performTransition(R.drawable.pn_option_background_deselect_transition, fcmOptionView)
                GlowViewUtilities.animateShadowColorChange(this, fcmOptionView, R.color.accent, R.color.transparent)
                animateStrokeColorChange(fcmOptionView, R.color.accent, R.color.pn_option_border)
                selectedOptionView = backgroundPollingOptionView
            }
        }
    }

    private fun animateStrokeColorChange(bubble: PNModeView, @ColorRes startColorID: Int, @ColorRes endColorID: Int) {
        val startColor = resources.getColorWithID(startColorID, theme)
        val endColor = resources.getColorWithID(endColorID, theme)
        val animation = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor)
        animation.duration = 250
        animation.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            bubble.strokeColor = color
        }
        animation.start()
    }

    private fun register() {
        if (selectedOptionView == null) {
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle(R.string.activity_pn_mode_no_option_picked_dialog_title)
            dialog.setPositiveButton(R.string.ok) { _, _ -> }
            dialog.create().show()
            return
        }
        TextSecurePreferences.setIsUsingFCM(this, (selectedOptionView == fcmOptionView))
        val application = ApplicationContext.getInstance(this)
        application.startPollingIfNeeded()
        application.registerForFCMIfNeeded(true)
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        show(intent)
    }
    // endregion
}