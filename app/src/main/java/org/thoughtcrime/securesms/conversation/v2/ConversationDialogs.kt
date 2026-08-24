package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.content.DialogInterface
import android.widget.TextView
import androidx.core.app.DialogCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.concurrent.SimpleTask
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.conversation.v2.data.DeletedMessageTombstoneCache
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.MessageConstraintsUtil
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity

/**
 * Centralized object for displaying dialogs to the user from the
 * conversation fragment.
 */
object ConversationDialogs {

  private val TAG = Log.tag(ConversationDialogs::class.java)

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

  fun displayCannotStartGroupCallDueToNoLongerAMemberDialog(context: Context) {
    MaterialAlertDialogBuilder(context).setTitle(R.string.ConversationActivity_cant_start_group_call)
      .setMessage(R.string.CallLogFragment__cant_start_call_no_longer_a_member)
      .setPositiveButton(android.R.string.ok) { d: DialogInterface, _: Int -> d.dismiss() }
      .show()
  }

  fun displayCannotStartGroupCallDueToGroupEndedDialog(context: Context) {
    MaterialAlertDialogBuilder(context).setTitle(R.string.ConversationActivity_cant_start_group_call)
      .setMessage(R.string.conversation_activity__group_action_not_allowed_group_ended)
      .setPositiveButton(android.R.string.ok) { d: DialogInterface, _: Int -> d.dismiss() }
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

  fun displayInMemoryMessageDialog(context: Context, messageRecord: MessageRecord) {
    if (messageRecord is InMemoryMessageRecord.DeletedMessageTombstone) {
      displayDeletedMessageTombstoneDialog(context, messageRecord)
    }
  }

  /**
   * Dialog which is displayed when the user taps on a deleted message tombstone, allowing them
   * to promote the local deletion to a "delete for everyone".
   */
  private fun displayDeletedMessageTombstoneDialog(context: Context, messageRecord: InMemoryMessageRecord.DeletedMessageTombstone) {
    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.ConversationFragment_delete_for_everyone_title)
      .setMessage(R.string.ConversationFragment_this_message_will_be_deleted_for_everyone_in_the_conversation)
      .setPositiveButton(R.string.ConversationFragment_delete_for_everyone) { _, _ ->
        promoteTombstoneToRemoteDelete(messageRecord)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun promoteTombstoneToRemoteDelete(messageRecord: InMemoryMessageRecord.DeletedMessageTombstone) {
    SignalExecutors.BOUNDED.execute {
      if (!MessageConstraintsUtil.isValidRemoteDeleteSend(messageRecord.dateSent, System.currentTimeMillis())) {
        Log.w(TAG, "No longer eligible for a remote delete. Ignoring.")
        DeletedMessageTombstoneCache.remove(messageRecord.threadId, messageRecord.id)
        return@execute
      }

      val restored = SignalDatabase.messages.restoreDeletedOutgoingMessage(
        messageId = messageRecord.id,
        threadId = messageRecord.threadId,
        toRecipientId = messageRecord.toRecipient.id,
        dateSent = messageRecord.dateSent,
        dateReceived = messageRecord.dateReceived,
        type = messageRecord.type,
        expiresIn = messageRecord.expiresIn,
        expireStarted = messageRecord.expireStarted,
        expireTimerVersion = messageRecord.expireTimerVersion
      )

      if (restored) {
        DeletedMessageTombstoneCache.remove(messageRecord.threadId, messageRecord.id)
        MessageSender.sendRemoteDelete(messageRecord.id)
      } else {
        Log.w(TAG, "Failed to restore deleted message ${messageRecord.id} for remote delete.")
      }
    }
  }

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

  fun displayTerminatedGroupSendFailedDialog(context: Context, messageRecord: MessageRecord) {
    MaterialAlertDialogBuilder(context)
      .setMessage(R.string.conversation_activity__send_failed_group_ended)
      .setNegativeButton(R.string.ConversationFragment_delete_for_me) { _, _ ->
        SignalExecutors.BOUNDED.execute {
          SignalDatabase.messages.deleteMessage(messageRecord.id)
        }
      }
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }

  fun displayDeletionFailedDialog(context: Context, messageRecord: MessageRecord, canRetry: Boolean) {
    if (canRetry) {
      MaterialAlertDialogBuilder(context)
        .setMessage(R.string.conversation_activity__message_failed_to_delete_retry)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.conversation_activity__send) { _, _ ->
          SignalExecutors.BOUNDED.execute {
            MessageSender.resendAdminDelete(messageRecord, messageRecord.networkFailures.map { it.recipientId })
          }
        }
        .show()
    } else {
      MaterialAlertDialogBuilder(context)
        .setMessage(R.string.conversation_activity__message_failed_to_delete)
        .setPositiveButton(android.R.string.ok, null)
        .show()
    }
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
