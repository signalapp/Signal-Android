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
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.DonationReceiptRedemptionJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.rx.RxStore
import java.util.concurrent.CountDownLatch
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
        val displayingBadges = SignalStore.donationsValues().getDisplayBadgesOnProfile()
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
    return Completable.create {
      Log.i(TAG, "Enqueuing gift redemption and awaiting result...", true)

      var finalJobState: JobTracker.JobState? = null
      val countDownLatch = CountDownLatch(1)

      DonationReceiptRedemptionJob.createJobChainForGift(messageId, setAsPrimary).enqueue { _, state ->
        if (state.isComplete) {
          finalJobState = state
          countDownLatch.countDown()
        }
      }

      try {
        if (countDownLatch.await(10, TimeUnit.SECONDS)) {
          when (finalJobState) {
            JobTracker.JobState.SUCCESS -> {
              Log.d(TAG, "Gift redemption job chain succeeded.", true)
              it.onComplete()
            }
            JobTracker.JobState.FAILURE -> {
              Log.d(TAG, "Gift redemption job chain failed permanently.", true)
              it.onError(DonationError.genericBadgeRedemptionFailure(DonationErrorSource.GIFT_REDEMPTION))
            }
            else -> {
              Log.w(TAG, "Gift redemption job chain ignored due to in-progress jobs.", true)
              it.onError(DonationError.timeoutWaitingForToken(DonationErrorSource.GIFT_REDEMPTION))
            }
          }
        } else {
          Log.w(TAG, "Timeout awaiting for gift token redemption and profile refresh", true)
          it.onError(DonationError.timeoutWaitingForToken(DonationErrorSource.GIFT_REDEMPTION))
        }
      } catch (e: InterruptedException) {
        Log.w(TAG, "Interrupted awaiting for gift token redemption and profile refresh", true)
        it.onError(DonationError.timeoutWaitingForToken(DonationErrorSource.GIFT_REDEMPTION))
      }
    }
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
