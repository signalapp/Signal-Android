package org.thoughtcrime.securesms.onboarding

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
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityPnModeBinding
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.ThemeUtil
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.home.HomeActivity
import org.thoughtcrime.securesms.util.GlowViewUtilities
import org.thoughtcrime.securesms.util.PNModeView
import org.thoughtcrime.securesms.util.disableClipping
import org.thoughtcrime.securesms.util.getAccentColor
import org.thoughtcrime.securesms.util.getColorWithID
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.util.show

class PNModeActivity : BaseActionBarActivity() {
    private lateinit var binding: ActivityPnModeBinding
    private var selectedOptionView: PNModeView? = null

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo(true)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, true)
        binding = ActivityPnModeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        with(binding) {
            contentView.disableClipping()
            fcmOptionView.setOnClickListener { toggleFCM() }
            fcmOptionView.mainColor = ThemeUtil.getThemedColor(root.context, R.attr.colorPrimary)
            fcmOptionView.strokeColor = resources.getColorWithID(R.color.pn_option_border, theme)
            backgroundPollingOptionView.setOnClickListener { toggleBackgroundPolling() }
            backgroundPollingOptionView.mainColor = ThemeUtil.getThemedColor(root.context, R.attr.colorPrimary)
            backgroundPollingOptionView.strokeColor = resources.getColorWithID(R.color.pn_option_border, theme)
            registerButton.setOnClickListener { register() }
        }
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
        when(item.itemId) {
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

    private fun toggleFCM() = with(binding) {
        val accentColor = getAccentColor()
        when (selectedOptionView) {
            null -> {
                performTransition(R.drawable.pn_option_background_select_transition, fcmOptionView)
                GlowViewUtilities.animateShadowColorChange(fcmOptionView, resources.getColorWithID(R.color.transparent, theme), accentColor)
                animateStrokeColorChange(fcmOptionView, resources.getColorWithID(R.color.pn_option_border, theme), accentColor)
                selectedOptionView = fcmOptionView
            }
            fcmOptionView -> {
                performTransition(R.drawable.pn_option_background_deselect_transition, fcmOptionView)
                GlowViewUtilities.animateShadowColorChange(fcmOptionView, accentColor, resources.getColorWithID(R.color.transparent, theme))
                animateStrokeColorChange(fcmOptionView, accentColor, resources.getColorWithID(R.color.pn_option_border, theme))
                selectedOptionView = null
            }
            backgroundPollingOptionView -> {
                performTransition(R.drawable.pn_option_background_select_transition, fcmOptionView)
                GlowViewUtilities.animateShadowColorChange(fcmOptionView, resources.getColorWithID(R.color.transparent, theme), accentColor)
                animateStrokeColorChange(fcmOptionView, resources.getColorWithID(R.color.pn_option_border, theme), accentColor)
                performTransition(R.drawable.pn_option_background_deselect_transition, backgroundPollingOptionView)
                GlowViewUtilities.animateShadowColorChange(backgroundPollingOptionView, accentColor, resources.getColorWithID(R.color.transparent, theme))
                animateStrokeColorChange(backgroundPollingOptionView, accentColor, resources.getColorWithID(R.color.pn_option_border, theme))
                selectedOptionView = fcmOptionView
            }
        }
    }

    private fun toggleBackgroundPolling() = with(binding) {
        val accentColor = getAccentColor()
        when (selectedOptionView) {
            null -> {
                performTransition(R.drawable.pn_option_background_select_transition, backgroundPollingOptionView)
                GlowViewUtilities.animateShadowColorChange(backgroundPollingOptionView, resources.getColorWithID(R.color.transparent, theme), accentColor)
                animateStrokeColorChange(backgroundPollingOptionView, resources.getColorWithID(R.color.pn_option_border, theme), accentColor)
                selectedOptionView = backgroundPollingOptionView
            }
            backgroundPollingOptionView -> {
                performTransition(R.drawable.pn_option_background_deselect_transition, backgroundPollingOptionView)
                GlowViewUtilities.animateShadowColorChange(backgroundPollingOptionView, accentColor, resources.getColorWithID(R.color.transparent, theme))
                animateStrokeColorChange(backgroundPollingOptionView, accentColor, resources.getColorWithID(R.color.pn_option_border, theme))
                selectedOptionView = null
            }
            fcmOptionView -> {
                performTransition(R.drawable.pn_option_background_select_transition, backgroundPollingOptionView)
                GlowViewUtilities.animateShadowColorChange(backgroundPollingOptionView, resources.getColorWithID(R.color.transparent, theme), accentColor)
                animateStrokeColorChange(backgroundPollingOptionView, resources.getColorWithID(R.color.pn_option_border, theme), accentColor)
                performTransition(R.drawable.pn_option_background_deselect_transition, fcmOptionView)
                GlowViewUtilities.animateShadowColorChange(fcmOptionView, accentColor, resources.getColorWithID(R.color.transparent, theme))
                animateStrokeColorChange(fcmOptionView, accentColor, resources.getColorWithID(R.color.pn_option_border, theme))
                selectedOptionView = backgroundPollingOptionView
            }
        }
    }

    private fun animateStrokeColorChange(bubble: PNModeView, @ColorInt startColor: Int, @ColorInt endColor: Int) {
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
        TextSecurePreferences.setIsUsingFCM(this, (selectedOptionView == binding.fcmOptionView))
        val application = ApplicationContext.getInstance(this)
        application.startPollingIfNeeded()
        application.registerForFCMIfNeeded(true)
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        show(intent)
    }
    // endregion
}