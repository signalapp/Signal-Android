package org.thoughtcrime.securesms.preferences

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.recyclerview.widget.DividerItemDecoration
import network.loki.messenger.databinding.DialogListPreferenceBinding
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog

class ListPreferenceDialog(
    private val listPreference: ListPreference,
    private val dialogListener: () -> Unit
) : BaseDialog() {
    private lateinit var binding: DialogListPreferenceBinding

    override fun setContentView(builder: AlertDialog.Builder) {
        binding = DialogListPreferenceBinding.inflate(LayoutInflater.from(requireContext()))
        binding.titleTextView.text = listPreference.dialogTitle
        binding.messageTextView.text = listPreference.dialogMessage
        binding.closeButton.setOnClickListener {
            dismiss()
        }
        val options = listPreference.entryValues.zip(listPreference.entries) { value, title ->
            RadioOption(value.toString(), title.toString())
        }
        val valueIndex = listPreference.findIndexOfValue(listPreference.value)
        val optionAdapter = RadioOptionAdapter(valueIndex) {
            listPreference.value = it.value
            dismiss()
            dialogListener.invoke()
        }
        binding.recyclerView.apply {
            adapter = optionAdapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            setHasFixedSize(true)
        }
        optionAdapter.submitList(options)
        builder.setView(binding.root)
        builder.setCancelable(false)
    }

}