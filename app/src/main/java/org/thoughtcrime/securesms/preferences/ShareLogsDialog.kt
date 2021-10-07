package org.thoughtcrime.securesms.preferences

import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.dialog_share_logs.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog
import org.thoughtcrime.securesms.providers.BlobProvider

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
            val persistentLogger = ApplicationContext.getInstance(context).persistentLogger
            try {
                val logs = persistentLogger.logs.get()
                val fileName = "${Build.MANUFACTURER}-${Build.DEVICE}-API${Build.VERSION.SDK_INT}-v${BuildConfig.VERSION_NAME}.log"
                val logUri = BlobProvider().forData(logs.toByteArray())
                    .withFileName(fileName)
                    .withMimeType("text/plain")
                    .createForSingleSessionOnDisk(requireContext(),null)

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, logUri)
                    type = "text/plain"
                }

                dismiss()

                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            } catch (e: Exception) {
                Toast.makeText(context,"Error saving logs", Toast.LENGTH_LONG).show()
                dismiss()
            }
        }
    }

}