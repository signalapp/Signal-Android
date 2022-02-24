package org.thoughtcrime.securesms.mediasend.v2.text.send

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryPostCreationState
import org.thoughtcrime.securesms.util.livedata.Store

class TextStoryPostSendViewModel(private val repository: TextStoryPostSendRepository) : ViewModel() {

  private val store = Store(TextStoryPostSendState.INIT)
  private val disposables = CompositeDisposable()

  val state: LiveData<TextStoryPostSendState> = store.stateLiveData

  override fun onCleared() {
    disposables.clear()
  }

  fun isFirstSendToAStory(contactSearchKeys: Set<ContactSearchKey>): Boolean {
    store.update {
      TextStoryPostSendState.SENDING
    }

    return repository.isFirstSendToStory(contactSearchKeys)
  }

  fun onSendCancelled() {
    store.update {
      TextStoryPostSendState.INIT
    }
  }

  fun onSend(contactSearchKeys: Set<ContactSearchKey>, textStoryPostCreationState: TextStoryPostCreationState, linkPreviewState: LinkPreviewViewModel.LinkPreviewState) {
    store.update {
      TextStoryPostSendState.SENDING
    }

    disposables += repository.send(contactSearchKeys, textStoryPostCreationState, linkPreviewState.linkPreview.orNull()).subscribeBy(
      onComplete = {
        store.update { TextStoryPostSendState.SENT }
      },
      onError = {
        // TODO [stories] -- Error of some sort.
        store.update { TextStoryPostSendState.INIT }
      }
    )
  }

  class Factory(private val repository: TextStoryPostSendRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(TextStoryPostSendViewModel(repository)) as T
    }
  }
}
