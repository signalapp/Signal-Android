package org.thoughtcrime.securesms.preferences

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.DialogClearAllDataBinding
import org.session.libsession.snode.SnodeAPI
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

class ClearAllDataDialog : BaseDialog() {
    private lateinit var binding: DialogClearAllDataBinding

    enum class Steps {
        INFO_PROMPT,
        NETWORK_PROMPT,
        DELETING
    }

    var clearJob: Job? = null

    var step = Steps.INFO_PROMPT
        set(value) {
            field = value
            updateUI()
        }

    override fun setContentView(builder: AlertDialog.Builder) {
        binding = DialogClearAllDataBinding.inflate(LayoutInflater.from(requireContext()))
        val device = RadioOption("deviceOnly", requireContext().getString(R.string.dialog_clear_all_data_clear_device_only))
        val network = RadioOption("deviceAndNetwork", requireContext().getString(R.string.dialog_clear_all_data_clear_device_and_network))
        var selectedOption = device
        val optionAdapter = RadioOptionAdapter { selectedOption = it }
        binding.recyclerView.apply {
            adapter = optionAdapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            setHasFixedSize(true)
        }
        optionAdapter.submitList(listOf(device, network))
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        binding.clearAllDataButton.setOnClickListener {
            when(step) {
                Steps.INFO_PROMPT -> if (selectedOption == network) {
                    step = Steps.NETWORK_PROMPT
                } else {
                    clearAllData(false)
                }
                Steps.NETWORK_PROMPT -> clearAllData(true)
                Steps.DELETING -> { /* do nothing intentionally */ }
            }
        }
        builder.setView(binding.root)
        builder.setCancelable(false)
    }

    private fun updateUI() {
        dialog?.let {
            val isLoading = step == Steps.DELETING

            when (step) {
                Steps.INFO_PROMPT -> {
                    binding.dialogDescriptionText.setText(R.string.dialog_clear_all_data_message)
                }
                Steps.NETWORK_PROMPT -> {
                    binding.dialogDescriptionText.setText(R.string.dialog_clear_all_data_clear_device_and_network_confirmation)
                }
                Steps.DELETING -> { /* do nothing intentionally */ }
            }
            binding.recyclerView.isGone = step == Steps.NETWORK_PROMPT
            binding.cancelButton.isVisible = !isLoading
            binding.clearAllDataButton.isVisible = !isLoading
            binding.progressBar.isVisible = isLoading

            it.setCanceledOnTouchOutside(!isLoading)
            isCancelable = !isLoading
        }
    }

    private fun clearAllData(deleteNetworkMessages: Boolean) {
        clearJob = lifecycleScope.launch(Dispatchers.IO) {
            val previousStep = step
            withContext(Dispatchers.Main) {
                step = Steps.DELETING
            }

            if (!deleteNetworkMessages) {
                try {
                    ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(requireContext()).get()
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
                    SnodeAPI.deleteAllMessages().get()
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
    }
}