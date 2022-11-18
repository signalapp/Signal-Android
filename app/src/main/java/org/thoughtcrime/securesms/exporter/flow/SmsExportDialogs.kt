package org.thoughtcrime.securesms.exporter.flow

import android.content.Context
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase

object SmsExportDialogs {
  @JvmStatic
  fun showSmsRemovalDialog(context: Context, view: View) {
    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.RemoveSmsMessagesDialogFragment__remove_sms_messages)
      .setMessage(R.string.RemoveSmsMessagesDialogFragment__you_can_now_remove_sms_messages_from_signal)
      .setPositiveButton(R.string.RemoveSmsMessagesDialogFragment__keep_messages) { _, _ ->
        Snackbar.make(view, R.string.SmsSettingsFragment__you_can_remove_sms_messages_from_signal_in_settings, Snackbar.LENGTH_SHORT).show()
      }
      .setNegativeButton(R.string.RemoveSmsMessagesDialogFragment__remove_messages) { _, _ ->
        SignalExecutors.BOUNDED.execute {
          SignalDatabase.sms.deleteExportedMessages()
          SignalDatabase.mms.deleteExportedMessages()
        }
        Snackbar.make(view, R.string.SmsSettingsFragment__removing_sms_messages_from_signal, Snackbar.LENGTH_SHORT).show()
      }
      .show()
  }

  @JvmStatic
  fun showSmsReExportDialog(context: Context, continueCallback: Runnable) {
    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.ReExportSmsMessagesDialogFragment__export_sms_again)
      .setMessage(R.string.ReExportSmsMessagesDialogFragment__you_already_exported_your_sms_messages)
      .setPositiveButton(R.string.ReExportSmsMessagesDialogFragment__continue) { _, _ -> continueCallback.run() }
      .setNegativeButton(R.string.ReExportSmsMessagesDialogFragment__cancel, null)
      .show()
  }
}
