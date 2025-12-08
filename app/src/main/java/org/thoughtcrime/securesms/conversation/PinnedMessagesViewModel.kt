package org.thoughtcrime.securesms.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.colors.GroupAuthorNameColorHelper
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.UnpinMessageJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * View model for the pinned messages bottom sheet
 */
class PinnedMessagesViewModel(
  application: Application,
  private val threadId: Long,
  private val conversationRecipientId: RecipientId
) : AndroidViewModel(application) {

  companion object {
    private val TAG = Log.tag(PinnedMessagesViewModel::class.java)
  }

  private val groupAuthorNameColorHelper = GroupAuthorNameColorHelper()
  private val repository = PinnedMessagesRepository()

  fun getMessages(): Observable<List<ConversationMessage>> {
    return repository
      .getPinnedMessage(getApplication(), threadId)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun getNameColorsMap(): Observable<Map<RecipientId, NameColor>> {
    return Observable.just(conversationRecipientId)
      .map { conversationRecipientId ->
        val conversationRecipient = Recipient.resolved(conversationRecipientId)

        if (conversationRecipient.groupId.isPresent) {
          groupAuthorNameColorHelper.getColorMap(conversationRecipient.groupId.get())
        } else {
          emptyMap()
        }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun unpinMessage() {
    viewModelScope.launch(Dispatchers.IO) {
      repository.getPinnedMessageRecords(threadId).map {
        val unpinJob = UnpinMessageJob.create(messageId = it.id)
        if (unpinJob != null) {
          AppDependencies.jobManager.add(unpinJob)
        } else {
          Log.w(TAG, "Unable to create unpin job for message ${it.id}, ignoring.")
        }
      }
    }
  }

  class Factory(private val application: Application, private val threadId: Long, private val conversationRecipientId: RecipientId) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(PinnedMessagesViewModel(application, threadId, conversationRecipientId)) as T
    }
  }
}
