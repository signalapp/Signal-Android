package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R

/**
 * Centralized object for displaying dialogs to the user from the
 * conversation fragment.
 */
object ConversationDialogs {
  /**
   * Dialog which is displayed when the user attempts to start a video call
   * as a non-admin in an announcement group.
   */
  fun displayCannotStartGroupCallDueToPermissionsDialog(context: Context) {
    MaterialAlertDialogBuilder(context).setTitle(R.string.ConversationActivity_cant_start_group_call)
      .setMessage(R.string.ConversationActivity_only_admins_of_this_group_can_start_a_call)
      .setPositiveButton(R.string.ok) { d: DialogInterface, w: Int -> d.dismiss() }
      .show()
  }
}
