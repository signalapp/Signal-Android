package org.thoughtcrime.securesms.stories.settings.privacy

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.stories.settings.my.MyStorySettingsRepository
import org.thoughtcrime.securesms.util.rx.RxStore

class ChooseInitialMyStoryMembershipViewModel @JvmOverloads constructor(
  private val repository: MyStorySettingsRepository = MyStorySettingsRepository()
) : ViewModel() {

  private val store = RxStore(ChooseInitialMyStoryMembershipState())
  private val disposables = CompositeDisposable()

  val state: Flowable<ChooseInitialMyStoryMembershipState> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  init {
    disposables += repository.observeChooseInitialPrivacy()
      .distinctUntilChanged()
      .subscribeBy(onNext = { state ->
        store.update { state.copy(hasUserPerformedManualSelection = it.hasUserPerformedManualSelection) }
      })
  }

  override fun onCleared() {
    disposables.clear()
    store.dispose()
  }

  fun select(selection: DistributionListPrivacyMode): Single<DistributionListPrivacyMode> {
    return repository.setPrivacyMode(selection)
      .toSingleDefault(selection)
      .doAfterSuccess { _ ->
        store.update { it.copy(hasUserPerformedManualSelection = true) }
      }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun save(): Single<RecipientId> {
    return Single.fromCallable<RecipientId> {
      SignalStore.story.userHasBeenNotifiedAboutStories = true
      Stories.onStorySettingsChanged(Recipient.self().id)
      store.state.recipientId!!
    }.observeOn(AndroidSchedulers.mainThread())
  }
}
