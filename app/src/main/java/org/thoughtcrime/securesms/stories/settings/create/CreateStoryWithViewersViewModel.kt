package org.thoughtcrime.securesms.stories.settings.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.rx.RxStore

class CreateStoryWithViewersViewModel(
  private val repository: CreateStoryWithViewersRepository
) : ViewModel() {

  private val store = RxStore(CreateStoryWithViewersState())
  private val disposables = CompositeDisposable()

  val state: Flowable<CreateStoryWithViewersState> = store.stateFlowable
    .distinctUntilChanged()
    .observeOn(AndroidSchedulers.mainThread())

  override fun onCleared() {
    disposables.clear()
    store.dispose()
  }

  fun setLabel(label: CharSequence) {
    store.update { it.copy(label = label) }
  }

  fun create(members: Set<RecipientId>) {
    store.update { it.copy(saveState = CreateStoryWithViewersState.SaveState.Saving) }

    val label = store.state.label
    if (label.isEmpty()) {
      store.update {
        it.copy(
          error = CreateStoryWithViewersState.NameError.NO_LABEL,
          saveState = CreateStoryWithViewersState.SaveState.Init
        )
      }
    }

    disposables += repository.createList(label, members).subscribeBy(
      onSuccess = { recipientId ->
        store.update {
          it.copy(saveState = CreateStoryWithViewersState.SaveState.Saved(recipientId))
        }
      },
      onError = {
        store.update {
          it.copy(
            saveState = CreateStoryWithViewersState.SaveState.Init,
            error = CreateStoryWithViewersState.NameError.DUPLICATE_LABEL
          )
        }
      }
    )
  }

  class Factory(
    private val repository: CreateStoryWithViewersRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(CreateStoryWithViewersViewModel(repository)) as T
    }
  }
}
