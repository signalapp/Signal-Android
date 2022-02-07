package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import network.loki.messenger.databinding.DialogLinkPreviewBinding
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog

/** Shown the first time the user inputs a URL that could generate a link preview, to
 * let them know that Session offers the ability to send and receive link previews. */
class LinkPreviewDialog(private val onEnabled: () -> Unit) : BaseDialog() {

    override fun setContentView(builder: AlertDialog.Builder) {
        val binding = DialogLinkPreviewBinding.inflate(LayoutInflater.from(requireContext()))
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.enableLinkPreviewsButton.setOnClickListener { enable() }
        builder.setView(binding.root)
    }

    private fun enable() {
        TextSecurePreferences.setLinkPreviewsEnabled(requireContext(), true)
        dismiss()
        onEnabled()
    }
}