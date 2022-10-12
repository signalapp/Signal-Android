package org.thoughtcrime.securesms.preferences

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class ChangeUiModeDialog : DialogFragment() {

    companion object {
        const val TAG = "ChangeUiModeDialog"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        return android.app.AlertDialog.Builder(context)
            .setTitle("TODO: remove this")
            .show()
    }
}