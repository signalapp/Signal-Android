package org.thoughtcrime.securesms.loki.dialogs

import android.app.Dialog
import android.content.DialogInterface
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
import kotlinx.coroutines.*
import network.loki.messenger.R
import nl.komponents.kovenant.Promise
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.KeyPairUtilities
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol
import java.util.concurrent.Executors

class ClearAllDataDialog : DialogFragment() {

    enum class Steps {
        INFO_PROMPT,
        NETWORK_PROMPT,
        DELETING
    }

    var clearJob: Job? = null
        set(value) {
            field = value
        }

    var step = Steps.INFO_PROMPT
        set(value) {
            field = value
            updateUI()
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_clear_all_data, null)
        contentView.cancelButton.setOnClickListener {
            if (step == Steps.NETWORK_PROMPT) {
                clearAllData(false)
            } else {
                dismiss()
            }
        }
        contentView.clearAllDataButton.setOnClickListener {
            when(step) {
                Steps.INFO_PROMPT -> step = Steps.NETWORK_PROMPT
                Steps.NETWORK_PROMPT -> {
                    clearAllData(true)
                }
                Steps.DELETING -> { /* do nothing intentionally */ }
            }
        }
        builder.setView(contentView)
        builder.setCancelable(false)
        val result = builder.create()
        result.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return result
    }

    private fun updateUI() {

        dialog?.let { view ->

            val isLoading = step == Steps.DELETING

            when (step) {
                Steps.INFO_PROMPT -> {
                    view.dialogDescriptionText.setText(R.string.dialog_clear_all_data_explanation)
                    view.cancelButton.setText(R.string.cancel)
                }
                else -> {
                    view.dialogDescriptionText.setText(R.string.dialog_clear_all_data_network_explanation)
                    view.cancelButton.setText(R.string.dialog_clear_all_data_local_only)
                }
            }

            view.cancelButton.isVisible = !isLoading
            view.clearAllDataButton.isVisible = !isLoading
            view.progressBar.isVisible = isLoading

            view.setCanceledOnTouchOutside(!isLoading)
            isCancelable = !isLoading

        }
    }

    private fun clearAllData(deleteNetworkMessages: Boolean) {
        if (KeyPairUtilities.hasV2KeyPair(requireContext())) {
            clearJob = lifecycleScope.launch(Dispatchers.IO) {
                val previousStep = step
                withContext(Dispatchers.Main) {
                    step = Steps.DELETING
                }

                if (!deleteNetworkMessages) {
                    try {
                        MultiDeviceProtocol.forceSyncConfigurationNowIfNeeded(requireContext()).get()
                    } catch (e: Exception) {
                        Log.e("Loki", "Failed to force sync", e)
                    }
                    ApplicationContext.getInstance(context).clearAllData(false)
                    withContext(Dispatchers.Main) {
                        dismiss()
                    }
                } else {
                    // finish
                    val result = try {
                        SnodeAPI.deleteAllMessages(requireContext()).get()
                    } catch (e: Exception) {
                        null
                    }

                    if (result == null || result.values.any { !it } || result.isEmpty()) {
                        // didn't succeed (at least one)
                        withContext(Dispatchers.Main) {
                            step = previousStep
                        }
                    } else if (result.values.all { it }) {
                        // don't force sync because all the messages are deleted?
                        ApplicationContext.getInstance(context).clearAllData(false)
                        withContext(Dispatchers.Main) {
                            dismiss()
                        }
                    }
                }
            }
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