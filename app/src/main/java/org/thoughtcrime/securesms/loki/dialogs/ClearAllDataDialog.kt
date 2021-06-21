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

class ClearAllDataDialog(val deleteNetworkMessages: Boolean) : DialogFragment() {

    var clearJob: Job? = null
        set(value) {
            field = value
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

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }

    override fun onStart() {
        super.onStart()
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)
    }

    private fun updateUI(isLoading: Boolean) {
        dialog?.let { view ->
            view.cancelButton.isVisible = !isLoading
            view.clearAllDataButton.isVisible = !isLoading
            view.progressBar.isVisible = isLoading
        }
    }

    private fun clearAllData() {
        if (KeyPairUtilities.hasV2KeyPair(requireContext())) {
            clearJob = lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    updateUI(true)
                }

                if (!deleteNetworkMessages) {
                    try {
                        MultiDeviceProtocol.forceSyncConfigurationNowIfNeeded(requireContext()).get()
                        ApplicationContext.getInstance(context).clearAllData(false)
                        withContext(Dispatchers.Main) {
                            dismiss()
                        }
                    } catch (e: Exception) {
                        Log.e("Loki", "Failed to force sync", e)
                        withContext(Dispatchers.Main) {
                            updateUI(false)
                        }
                    }
                } else {
                    // finish
                    val promises = try {
                        SnodeAPI.deleteAllMessages(requireContext()).get()
                    } catch (e: Exception) {
                        null
                    }

                    val rawResponses = promises?.map {
                        try {
                            it.get()
                        } catch (e: Exception) {
                            null
                        }
                    } ?: listOf(null)
                    // TODO: process the responses here
                    if (rawResponses.all { it != null }) {
                        // don't force sync because all the messages are deleted?
                        ApplicationContext.getInstance(context).clearAllData(false)
                        withContext(Dispatchers.Main) {
                            dismiss()
                        }
                    } else if (rawResponses.any { it == null || it["failed"] as? Boolean == true }) {
                        // didn't succeed (at least one)
                        withContext(Dispatchers.Main) {
                            updateUI(false)
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