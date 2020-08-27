package org.thoughtcrime.securesms.loki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.thoughtcrime.securesms.loki.utilities.UiMode
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities

//TODO Use localized string resources.
class ChangeUiModeDialog : DialogFragment() {

    companion object {
        const val TAG = "ChangeUiModeDialog"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val displayNameList = UiMode.values().map { it.displayName }.toTypedArray()
        val activeUiMode = UiModeUtilities.getUserSelectedUiMode(context)

        return AlertDialog.Builder(context)
                .setSingleChoiceItems(displayNameList, activeUiMode.ordinal) { _, selectedItemIdx: Int ->
                    val uiMode = UiMode.values()[selectedItemIdx]
                    UiModeUtilities.setUserSelectedUiMode(context, uiMode)
                    dismiss()
                    requireActivity().recreate()
                }
                .setTitle("Application theme")
                .setNegativeButton("Cancel") { _, _ -> dismiss() }
                .create()

    }
}