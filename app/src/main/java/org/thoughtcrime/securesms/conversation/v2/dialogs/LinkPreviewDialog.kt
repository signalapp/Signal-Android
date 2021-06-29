package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.dialog_link_preview.view.*
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog

/** Shown the first time the user inputs a URL that could generate a link preview, to
 * let them know that Session offers the ability to send and receive link previews. */
class LinkPreviewDialog(private val onEnabled: () -> Unit) : BaseDialog() {

    override fun setContentView(builder: AlertDialog.Builder) {
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_link_preview, null)
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.enableLinkPreviewsButton.setOnClickListener { enable() }
        builder.setView(contentView)
    }

    private fun enable() {
        TextSecurePreferences.setLinkPreviewsEnabled(requireContext(), true)
        dismiss()
        onEnabled()
    }
}