package org.thoughtcrime.securesms.loki.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.dialog_clear_all_data.*
import kotlinx.android.synthetic.main.dialog_clear_all_data.view.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeDeleteMessage
import org.session.libsession.utilities.KeyPairUtilities

class ClearAllDataDialog : DialogFragment() {

    var clearJob: Job? = null
    set(value) {
        field = value
        updateUI()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_clear_all_data, null)
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.clearAllDataButton.setOnClickListener { clearAllData() }
        builder.setView(contentView)
        builder.setCancelable(false)
        val result = builder.create()
        result.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return result
    }

    private fun updateUI() {
        if (clearJob?.isActive == true) {
            // clear background job is running, prevent interaction
            dialog?.let { view ->
                view.cancelButton.isVisible = false
                view.clearAllDataButton.isVisible = false
            }
        } else {
            dialog?.let { view ->
                view.cancelButton.isVisible = false
                view.clearAllDataButton.isVisible = false
            }
        }
    }

    private fun clearAllData() {
        if (KeyPairUtilities.hasV2KeyPair(requireContext())) {
            clearJob = lifecycleScope.launch {
                delay(5_000)
                // finish
                val userPublicKey = MessagingModuleConfiguration.shared.storage.getUserPublicKey()


                val deleteMessage = SnodeDeleteMessage(userKey, System.currentTimeMillis(), )
                SnodeAPI.deleteAllMessages()
                // TODO: re-add the clear data here
                //ApplicationContext.getInstance(context).clearAllData(false)
            }
        } else {
            val dialog = AlertDialog.Builder(requireContext())
            val message = "We’ve upgraded the way Session IDs are generated, so you will be unable to restore your current Session ID."
            dialog.setMessage(message)
            dialog.setPositiveButton("Yes") { _, _ ->
                // TODO: re-add the clear data here
                // ApplicationContext.getInstance(context).clearAllData(false)
            }
            dialog.setNegativeButton("Cancel") { _, _ ->
                // Do nothing
            }
            dialog.create().show()
        }
    }
}