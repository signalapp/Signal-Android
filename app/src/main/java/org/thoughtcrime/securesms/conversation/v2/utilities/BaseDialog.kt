package org.thoughtcrime.securesms.conversation.v2.utilities

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities

open class BaseDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        setContentView(builder)
        val result = builder.create()
        result.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val isLightMode = UiModeUtilities.isDayUiMode(requireContext())
        result.window?.setDimAmount(if (isLightMode) 0.1f else 0.75f)
        return result
    }

    open fun setContentView(builder: AlertDialog.Builder) {
        // To be overridden by subclasses
    }
}