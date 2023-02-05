package org.thoughtcrime.securesms.stories.viewer.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.messagedetails.MessageDetailsRepository
import org.thoughtcrime.securesms.util.rx.RxStore

/**
 * Gathers and stores the StoryInfoState which is used to render the story info sheet.
 */
class StoryInfoViewModel(storyId: Long, repository: MessageDetailsRepository = MessageDetailsRepository()) : ViewModel() {

  private val store = RxStore(StoryInfoState())
  private val disposables = CompositeDisposable()

  val state: Flowable<StoryInfoState> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  init {
    disposables += store.update(repository.getMessageDetails(MessageId(storyId)).toFlowable(BackpressureStrategy.LATEST)) { messageDetails, storyInfoState ->
      storyInfoState.copy(
        messageDetails = messageDetails
      )
    }
  }

  override fun onCleared() {
    disposables.clear()
    store.dispose()
  }

  class Factory(private val storyId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryInfoViewModel(storyId)) as T
    }
  }
}
