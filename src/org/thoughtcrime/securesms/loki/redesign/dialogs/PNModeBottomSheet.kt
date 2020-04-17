package org.thoughtcrime.securesms.loki.redesign.dialogs

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.design.widget.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.fragment_pn_mode_bottom_sheet.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.TextSecurePreferences

class PNModeBottomSheet : BottomSheetDialogFragment() {
    private var selectedOptionView: LinearLayout? = null
    var onConfirmTapped: ((Boolean) -> Unit)? = null
    var onSkipTapped: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.SessionBottomSheetDialogTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pn_mode_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fcmOptionView.setOnClickListener { toggleFCM() }
        backgroundPollingOptionView.setOnClickListener { toggleBackgroundPolling() }
        confirmButton.setOnClickListener { confirm() }
        skipButton.setOnClickListener { onSkipTapped?.invoke() }
    }

    override fun onDismiss(dialog: DialogInterface?) {
        TextSecurePreferences.setHasSeenPNModeSheet(context, true)
        super.onDismiss(dialog)
    }

    // region Animation
    private fun performTransition(@DrawableRes transitionID: Int, subject: View) {
        val drawable = resources.getDrawable(transitionID, context!!.theme) as TransitionDrawable
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

    private fun confirm() {
        if (selectedOptionView == null) {
            val dialog = AlertDialog.Builder(context)
            dialog.setTitle(R.string.sheet_pn_mode_no_option_picked_dialog_title)
            dialog.setPositiveButton(R.string.ok) { _, _ -> }
            dialog.create().show()
            return
        }
        onConfirmTapped?.invoke(selectedOptionView == fcmOptionView)
    }
    // endregion
}