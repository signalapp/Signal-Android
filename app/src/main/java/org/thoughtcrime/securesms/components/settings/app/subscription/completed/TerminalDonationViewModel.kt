/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.completed

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient

class TerminalDonationViewModel(
  donationCompleted: TerminalDonationQueue.TerminalDonation,
  repository: TerminalDonationRepository = TerminalDonationRepository(),
  private val badgeRepository: BadgeRepository
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(TerminalDonationViewModel::class.java)
  }

  private val disposables = CompositeDisposable()

  private val internalBadge = mutableStateOf<Badge?>(null)
  private val internalToggleChecked = mutableStateOf(false)
  private val internalToggleType = mutableStateOf(ToggleType.NONE)

  val badge: State<Badge?> = internalBadge
  val isToggleChecked: State<Boolean> = internalToggleChecked
  val toggleType: State<ToggleType> = internalToggleType

  init {
    disposables += repository.getBadge(donationCompleted)
      .map { badge ->
        val hasOtherBadges = Recipient.self().badges.filterNot { it.id == badge.id }.isNotEmpty()
        val isDisplayingBadges = SignalStore.inAppPayments.getDisplayBadgesOnProfile()

        val toggleType = when {
          hasOtherBadges && isDisplayingBadges -> ToggleType.MAKE_FEATURED_BADGE
          else -> ToggleType.DISPLAY_ON_PROFILE
        }

        badge to toggleType
      }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onSuccess = { (badge, toggleType) ->
          internalBadge.value = badge
          internalToggleType.value = toggleType
        }
      )
  }

  fun onToggleCheckChanged(isChecked: Boolean) {
    internalToggleChecked.value = isChecked
  }

  /**
   * Note that the intention here is that these are able to complete outside of the scope of the ViewModel's lifecycle.
   */
  @SuppressLint("CheckResult")
  fun commitToggleState() {
    when (toggleType.value) {
      ToggleType.NONE -> Unit
      ToggleType.MAKE_FEATURED_BADGE -> {
        badgeRepository.setVisibilityForAllBadges(isToggleChecked.value).subscribeBy(
          onError = {
            Log.w(TAG, "Failure while updating badge visibility", it)
          }
        )
      }

      ToggleType.DISPLAY_ON_PROFILE -> {
        val badge = this.badge.value
        if (badge == null) {
          Log.w(TAG, "No badge!")
          return
        }

        badgeRepository.setFeaturedBadge(badge).subscribeBy(
          onError = {
            Log.w(TAG, "Failure while updating featured badge", it)
          }
        )
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  enum class ToggleType(@StringRes val copyId: Int) {
    NONE(-1),
    MAKE_FEATURED_BADGE(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__make_featured_badge),
    DISPLAY_ON_PROFILE(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__display_on_profile)
  }
}
