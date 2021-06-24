package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.dialog_link_preview.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog

class LinkPreviewDialog() : BaseDialog() {

    override fun setContentView(builder: AlertDialog.Builder) {
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_link_preview, null)
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.enableLinkPreviewsButton.setOnClickListener { enable() }
        builder.setView(contentView)
    }

    private fun enable() {
        // TODO: Implement
    }
}