package org.thoughtcrime.securesms.loki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.UiMode
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities

class ChangeUiModeDialog : DialogFragment() {

    companion object {
        const val TAG = "ChangeUiModeDialog"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val displayNameList = UiMode.values().map { getString(it.displayNameRes) }.toTypedArray()
        val activeUiMode = UiModeUtilities.getUserSelectedUiMode(context)

        return AlertDialog.Builder(context)
                .setSingleChoiceItems(displayNameList, activeUiMode.ordinal) { _, selectedItemIdx: Int ->
                    val uiMode = UiMode.values()[selectedItemIdx]
                    UiModeUtilities.setUserSelectedUiMode(context, uiMode)
                    dismiss()
                    requireActivity().recreate()
                }
                .setTitle(R.string.dialog_ui_mode_title)
                .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
                .create()
    }
}