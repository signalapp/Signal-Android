package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.dialog_send_seed.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog

/** Shown if the user is about to send their recovery phrase to someone. */
class SendSeedDialog(private val proceed: (() -> Unit)? = null) : BaseDialog() {

    override fun setContentView(builder: AlertDialog.Builder) {
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_send_seed, null)
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.sendSeedButton.setOnClickListener { send() }
        builder.setView(contentView)
    }

    private fun send() {
        proceed?.invoke()
        dismiss()
    }
}