package org.thoughtcrime.securesms.stories.viewer.reply.direct

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.livedata.Store

class StoryDirectReplyViewModel(
  context: Context,
  private val storyId: Long,
  private val groupDirectReplyRecipientId: RecipientId?,
  private val repository: StoryDirectReplyRepository
) : ViewModel() {

  private val context = context.applicationContext

  private val store = Store(StoryDirectReplyState())
  private val disposables = CompositeDisposable()

  val state: LiveData<StoryDirectReplyState> = store.stateLiveData

  init {
    if (groupDirectReplyRecipientId != null) {
      store.update(Recipient.live(groupDirectReplyRecipientId).liveDataResolved) { recipient, state ->
        state.copy(recipient = recipient)
      }
    }

    disposables += repository.getStoryPost(storyId).subscribe { record ->
      store.update { it.copy(storyRecord = record) }
    }
  }

  fun send(charSequence: CharSequence): Completable {
    return repository.send(context, storyId, groupDirectReplyRecipientId, charSequence)
  }

  override fun onCleared() {
    super.onCleared()
    disposables.clear()
  }

  class Factory(
    private val storyId: Long,
    private val groupDirectReplyRecipientId: RecipientId?,
    private val repository: StoryDirectReplyRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(
        StoryDirectReplyViewModel(ApplicationDependencies.getApplication(), storyId, groupDirectReplyRecipientId, repository)
      ) as T
    }
  }
}
