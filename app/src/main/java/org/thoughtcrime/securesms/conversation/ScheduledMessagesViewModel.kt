package org.thoughtcrime.securesms.conversation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import org.signal.core.util.logging.Log

class ScheduledMessagesViewModel @JvmOverloads constructor(
  private val threadId: Long,
  private val repository: ScheduledMessagesRepository = ScheduledMessagesRepository()
) : ViewModel() {

  fun getMessages(context: Context): Observable<List<ConversationMessage>> {
    return repository.getScheduledMessages(context, threadId)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun rescheduleMessage(messageId: Long, scheduleTime: Long) {
    repository.rescheduleMessage(threadId, messageId, scheduleTime)
  }

  companion object {
    private val TAG = Log.tag(ScheduledMessagesViewModel::class.java)
  }

  class Factory(private val threadId: Long) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(ScheduledMessagesViewModel(threadId)) as T
    }
  }
}
