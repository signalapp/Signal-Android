package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.content.DialogInterface
import android.widget.TextView
import androidx.core.app.DialogCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.concurrent.SimpleTask
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity

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
      .setPositiveButton(android.R.string.ok) { d: DialogInterface, w: Int -> d.dismiss() }
      .show()
  }

  fun displayChatSessionRefreshLearnMoreDialog(context: Context) {
    MaterialAlertDialogBuilder(context)
      .setView(R.layout.decryption_failed_dialog)
      .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
      .setNeutralButton(R.string.ConversationFragment_contact_us) { d, _ ->
        context.startActivity(AppSettingsActivity.help(context, 0))
        d.dismiss()
      }
      .show()
  }

  fun displaySafetyNumberLearnMoreDialog(fragment: Fragment, recipient: Recipient) {
    check(!recipient.isGroup)
    val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
      .setView(R.layout.safety_number_changed_learn_more_dialog)
      .setPositiveButton(R.string.ConversationFragment_verify) { d, _ ->
        SimpleTask.run(
          fragment.lifecycle,
          { AppDependencies.protocolStore.aci().identities().getIdentityRecord(recipient.id) },
          { identityRecord ->
            identityRecord.ifPresent {
              VerifyIdentityActivity.startOrShowExchangeMessagesDialog(fragment.requireContext(), identityRecord.get())
            }
            d.dismiss()
          }
        )
      }
      .setNegativeButton(R.string.ConversationFragment_not_now) { d, _ -> d.dismiss() }
      .create()

    dialog.setOnShowListener {
      val title: TextView = DialogCompat.requireViewById(dialog, R.id.safety_number_learn_more_title) as TextView
      val body: TextView = DialogCompat.requireViewById(dialog, R.id.safety_number_learn_more_body) as TextView

      title.text = fragment.getString(
        R.string.ConversationFragment_your_safety_number_with_s_changed,
        recipient.getDisplayName(fragment.requireContext())
      )

      body.text = fragment.getString(
        R.string.ConversationFragment_your_safety_number_with_s_changed_likey_because_they_reinstalled_signal,
        recipient.getDisplayName(fragment.requireContext())
      )
    }

    dialog.show()
  }

  fun displayInMemoryMessageDialog(context: Context, messageRecord: MessageRecord) = Unit

  fun displayMessageCouldNotBeSentDialog(context: Context, messageRecord: MessageRecord) {
    MaterialAlertDialogBuilder(context)
      .setMessage(R.string.conversation_activity__message_could_not_be_sent)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.conversation_activity__send) { _, _ ->
        SignalExecutors.BOUNDED.execute {
          MessageSender.resend(context, messageRecord)
        }
      }
      .show()
  }

  @JvmStatic
  fun displayDeleteDialog(context: Context, recipient: Recipient, onDelete: () -> Unit) {
    MaterialAlertDialogBuilder(context)
      .setNeutralButton(R.string.ConversationActivity_cancel, null)
      .apply {
        if (recipient.isGroup && recipient.isBlocked) {
          setTitle(R.string.ConversationActivity_delete_conversation)
          setMessage(R.string.ConversationActivity_this_conversation_will_be_deleted_from_all_of_your_devices)
          setPositiveButton(R.string.ConversationActivity_delete) { _, _ -> onDelete() }
        } else if (recipient.isGroup) {
          setTitle(R.string.ConversationActivity_delete_and_leave_group)
          setMessage(R.string.ConversationActivity_you_will_leave_this_group_and_it_will_be_deleted_from_all_of_your_devices)
          setNegativeButton(R.string.ConversationActivity_delete_and_leave) { _, _ -> onDelete() }
        } else {
          setTitle(R.string.ConversationActivity_delete_conversation)
          setMessage(R.string.ConversationActivity_this_conversation_will_be_deleted_from_all_of_your_devices)
          setNegativeButton(R.string.ConversationActivity_delete) { _, _ -> onDelete() }
        }
      }
      .show()
  }
}
