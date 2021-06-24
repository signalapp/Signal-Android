package org.thoughtcrime.securesms.loki.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import kotlinx.android.synthetic.main.dialog_clear_all_data.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol
import org.session.libsession.utilities.KeyPairUtilities
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities

class ClearAllDataDialog : BaseDialog() {

    override fun setContentView(builder: AlertDialog.Builder) {
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_clear_all_data, null)
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.clearAllDataButton.setOnClickListener { clearAllData() }
        builder.setView(contentView)
    }

    private fun clearAllData() {
        if (KeyPairUtilities.hasV2KeyPair(requireContext())) {
            MultiDeviceProtocol.forceSyncConfigurationNowIfNeeded(requireContext())
            ApplicationContext.getInstance(context).clearAllData(false)
        } else {
            val dialog = AlertDialog.Builder(requireContext())
            val message = "We’ve upgraded the way Session IDs are generated, so you will be unable to restore your current Session ID."
            dialog.setMessage(message)
            dialog.setPositiveButton("Yes") { _, _ ->
                ApplicationContext.getInstance(context).clearAllData(false)
            }
            dialog.setNegativeButton("Cancel") { _, _ ->
                // Do nothing
            }
            dialog.create().show()
        }
    }
}