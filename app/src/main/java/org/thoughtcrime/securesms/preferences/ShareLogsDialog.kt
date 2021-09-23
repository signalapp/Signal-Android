package org.thoughtcrime.securesms.preferences

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.dialog_share_logs.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog

class ShareLogsDialog : BaseDialog() {

    private var shareJob: Job? = null

    override fun setContentView(builder: AlertDialog.Builder) {
        val contentView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_share_logs, null)
        contentView.cancelButton.setOnClickListener {
            dismiss()
        }
        contentView.shareButton.setOnClickListener {
            // start the export and share
            shareLogs()
        }
        builder.setView(contentView)
        builder.setCancelable(false)
    }

    private fun shareLogs() {
        shareJob?.cancel()
        shareJob = lifecycleScope.launch(Dispatchers.IO) {

        }
    }

}