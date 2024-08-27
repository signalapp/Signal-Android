package org.thoughtcrime.securesms.badges.gifts.viewgift.received

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.badges.gifts.viewgift.ViewGiftRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError.BadgeRedemptionError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.DatabaseObserver.MessageObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.InAppPaymentRedemptionJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.requireGiftBadge
import org.thoughtcrime.securesms.util.rx.RxStore
import java.util.concurrent.TimeUnit

class ViewReceivedGiftViewModel(
  sentFrom: RecipientId,
  private val messageId: Long,
  repository: ViewGiftRepository,
  val badgeRepository: BadgeRepository
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(ViewReceivedGiftViewModel::class.java)
  }

  private val store = RxStore(ViewReceivedGiftState())
  private val disposables = CompositeDisposable()

  val state: Flowable<ViewReceivedGiftState> = store.stateFlowable

  init {
    disposables += Recipient.observable(sentFrom).subscribe { recipient ->
      store.update { it.copy(recipient = recipient) }
    }

    disposables += repository.getGiftBadge(messageId).subscribe { giftBadge ->
      store.update {
        it.copy(giftBadge = giftBadge)
      }
    }

    disposables += repository
      .getGiftBadge(messageId)
      .firstOrError()
      .flatMap { repository.getBadge(it) }
      .subscribe { badge ->
        val otherBadges = Recipient.self().badges.filterNot { it.id == badge.id }
        val hasOtherBadges = otherBadges.isNotEmpty()
        val displayingBadges = SignalStore.inAppPayments.getDisplayBadgesOnProfile()
        val displayingOtherBadges = hasOtherBadges && displayingBadges

        store.update {
          it.copy(
            badge = badge,
            hasOtherBadges = hasOtherBadges,
            displayingOtherBadges = displayingOtherBadges,
            controlState = if (displayingBadges) ViewReceivedGiftState.ControlState.FEATURE else ViewReceivedGiftState.ControlState.DISPLAY
          )
        }
      }
  }

  override fun onCleared() {
    disposables.dispose()
    store.dispose()
  }

  fun setChecked(isChecked: Boolean) {
    store.update { state ->
      state.copy(
        userCheckSelection = isChecked
      )
    }
  }

  fun redeem(): Completable {
    val snapshot = store.state

    return if (snapshot.controlState != null && snapshot.badge != null) {
      if (snapshot.controlState == ViewReceivedGiftState.ControlState.DISPLAY) {
        badgeRepository.setVisibilityForAllBadges(snapshot.getControlChecked()).andThen(awaitRedemptionCompletion(false))
      } else if (snapshot.getControlChecked()) {
        awaitRedemptionCompletion(true)
      } else {
        awaitRedemptionCompletion(false)
      }
    } else {
      Completable.error(Exception("Cannot enqueue a redemption without a control state or badge."))
    }
  }

  private fun awaitRedemptionCompletion(setAsPrimary: Boolean): Completable {
    return Completable.create { emitter ->
      val messageObserver = MessageObserver { messageId ->
        val message = SignalDatabase.messages.getMessageRecord(messageId.id)
        when (message.requireGiftBadge().redemptionState) {
          GiftBadge.RedemptionState.REDEEMED -> emitter.onComplete()
          GiftBadge.RedemptionState.FAILED -> emitter.onError(DonationError.genericBadgeRedemptionFailure(DonationErrorSource.GIFT_REDEMPTION))
          else -> Unit
        }
      }

      AppDependencies.jobManager.add(InAppPaymentRedemptionJob.create(MessageId(messageId), setAsPrimary))
      AppDependencies.databaseObserver.registerMessageUpdateObserver(messageObserver)
      emitter.setCancellable {
        AppDependencies.databaseObserver.unregisterObserver(messageObserver)
      }
    }.timeout(10, TimeUnit.SECONDS, Completable.error(BadgeRedemptionError.TimeoutWaitingForTokenError(DonationErrorSource.GIFT_REDEMPTION)))
  }

  class Factory(
    private val sentFrom: RecipientId,
    private val messageId: Long,
    private val repository: ViewGiftRepository,
    private val badgeRepository: BadgeRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(ViewReceivedGiftViewModel(sentFrom, messageId, repository, badgeRepository)) as T
    }
  }
}
