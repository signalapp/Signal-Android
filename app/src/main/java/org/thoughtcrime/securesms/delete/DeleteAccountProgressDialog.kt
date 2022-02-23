package org.thoughtcrime.securesms.delete

import android.content.Context
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R

/**
 * Dialog which shows one of two states:
 *
 * 1. A "Leaving Groups" state with a determinate progress bar which updates as we leave groups
 * 1. A "Deleting Account" state with an indeterminate progress bar
 */
class DeleteAccountProgressDialog private constructor(private val alertDialog: AlertDialog) {

  val title: TextView = alertDialog.findViewById(R.id.delete_account_progress_dialog_title)!!
  val message: TextView = alertDialog.findViewById(R.id.delete_account_progress_dialog_message)!!
  val progressBar: ProgressBar = alertDialog.findViewById(R.id.delete_account_progress_dialog_spinner)!!

  fun presentCancelingSubscription() {
    title.setText(R.string.DeleteAccountFragment__deleting_account)
    message.setText(R.string.DeleteAccountFragment__canceling_your_subscription)
    progressBar.isIndeterminate = true
  }

  fun presentLeavingGroups(leaveGroupsProgress: DeleteAccountEvent.LeaveGroupsProgress) {
    title.setText(R.string.DeleteAccountFragment__leaving_groups)
    message.setText(R.string.DeleteAccountFragment__depending_on_the_number_of_groups)
    progressBar.isIndeterminate = false
    progressBar.max = leaveGroupsProgress.totalCount
    progressBar.progress = leaveGroupsProgress.leaveCount
  }

  fun presentDeletingAccount() {
    title.setText(R.string.DeleteAccountFragment__deleting_account)
    message.setText(R.string.DeleteAccountFragment__deleting_all_user_data_and_resetting)
    progressBar.isIndeterminate = true
  }

  fun dismiss() {
    alertDialog.dismiss()
  }

  companion object {
    @JvmStatic
    fun show(context: Context): DeleteAccountProgressDialog {
      return DeleteAccountProgressDialog(
        MaterialAlertDialogBuilder(context)
          .setView(R.layout.delete_account_progress_dialog)
          .show()
      )
    }
  }
}
