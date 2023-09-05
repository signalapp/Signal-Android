package org.thoughtcrime.securesms.util

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask

object DeleteDialog {

  /**
   * Displays a deletion dialog for the given set of message records.
   *
   * @param context           Android Context
   * @param messageRecords    The message records to delete
   * @param title             The dialog title
   * @param message           The dialog message, or null
   * @param forceRemoteDelete Allow remote deletion, even if it would normally be disallowed
   *
   * @return a Single, who's value is a pair that notes whether or not a deletion attempt
   * happened at all, as well as if a thread deletion occurred.
   */
  fun show(
    context: Context,
    messageRecords: Set<MessageRecord>,
    title: CharSequence = context.resources.getQuantityString(R.plurals.ConversationFragment_delete_selected_messages, messageRecords.size, messageRecords.size),
    message: CharSequence? = null,
    forceRemoteDelete: Boolean = false
  ): Single<Pair<Boolean, Boolean>> = Single.create { emitter ->
    val builder = MaterialAlertDialogBuilder(context)

    builder.setTitle(title)
    builder.setMessage(message)
    builder.setCancelable(true)

    val isNoteToSelfDelete = isNoteToSelfDelete(messageRecords)

    if (forceRemoteDelete) {
      builder.setPositiveButton(R.string.ConversationFragment_delete_for_everyone) { _, _ -> deleteForEveryone(messageRecords, emitter) }
    } else {
      builder.setPositiveButton(if (isNoteToSelfDelete) R.string.ConversationFragment_delete_on_this_device else R.string.ConversationFragment_delete_for_me) { _, _ ->
        DeleteProgressDialogAsyncTask(context, messageRecords) {
          emitter.onSuccess(Pair(true, it))
        }.executeOnExecutor(SignalExecutors.BOUNDED)
      }

      if (MessageConstraintsUtil.isValidRemoteDeleteSend(messageRecords, System.currentTimeMillis()) && (!isNoteToSelfDelete || TextSecurePreferences.isMultiDevice(context))) {
        builder.setNeutralButton(if (isNoteToSelfDelete) R.string.ConversationFragment_delete_everywhere else R.string.ConversationFragment_delete_for_everyone) { _, _ -> handleDeleteForEveryone(context, messageRecords, emitter) }
      }
    }

    builder.setNegativeButton(android.R.string.cancel) { _, _ -> emitter.onSuccess(Pair(false, false)) }
    builder.setOnCancelListener { emitter.onSuccess(Pair(false, false)) }
    builder.show()
  }

  private fun isNoteToSelfDelete(messageRecords: Set<MessageRecord>): Boolean {
    return messageRecords.all { messageRecord: MessageRecord -> messageRecord.isOutgoing && messageRecord.toRecipient.isSelf }
  }

  private fun handleDeleteForEveryone(context: Context, messageRecords: Set<MessageRecord>, emitter: SingleEmitter<Pair<Boolean, Boolean>>) {
    if (SignalStore.uiHints().hasConfirmedDeleteForEveryoneOnce()) {
      deleteForEveryone(messageRecords, emitter)
    } else {
      MaterialAlertDialogBuilder(context)
        .setMessage(R.string.ConversationFragment_this_message_will_be_deleted_for_everyone_in_the_conversation)
        .setPositiveButton(R.string.ConversationFragment_delete_for_everyone) { _, _ ->
          SignalStore.uiHints().markHasConfirmedDeleteForEveryoneOnce()
          deleteForEveryone(messageRecords, emitter)
        }
        .setNegativeButton(android.R.string.cancel) { _, _ -> emitter.onSuccess(Pair(false, false)) }
        .setOnCancelListener { emitter.onSuccess(Pair(false, false)) }
        .show()
    }
  }

  private fun deleteForEveryone(messageRecords: Set<MessageRecord>, emitter: SingleEmitter<Pair<Boolean, Boolean>>) {
    SignalExecutors.BOUNDED.execute {
      messageRecords.forEach { message ->
        MessageSender.sendRemoteDelete(message.id)
      }

      emitter.onSuccess(Pair(true, false))
    }
  }

  private class DeleteProgressDialogAsyncTask(
    context: Context,
    private val messageRecords: Set<MessageRecord>,
    private val onDeletionCompleted: ((Boolean) -> Unit)
  ) : ProgressDialogAsyncTask<Void, Void, Boolean>(
    context,
    R.string.ConversationFragment_deleting,
    R.string.ConversationFragment_deleting_messages
  ) {
    override fun doInBackground(vararg params: Void?): Boolean {
      return messageRecords.map { record ->
        if (record.isMms) {
          SignalDatabase.messages.deleteMessage(record.id)
        } else {
          SignalDatabase.messages.deleteMessage(record.id)
        }
      }.any { it }
    }

    override fun onPostExecute(result: Boolean?) {
      super.onPostExecute(result)
      onDeletionCompleted(result == true)
    }
  }
}
