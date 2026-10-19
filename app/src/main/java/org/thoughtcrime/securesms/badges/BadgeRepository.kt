package org.thoughtcrime.securesms.badges

import androidx.annotation.WorkerThread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.ProfileUtil

object BadgeRepository {

  private val TAG = Log.tag(BadgeRepository::class.java)

  /**
   * Sets the visibility for each badge on a user's profile, and uploads them to the server.
   * Does not write to the local database. The caller must either do that themselves or schedule
   * a refresh own profile job.
   *
   * @return A list of the badges, properly modified to either visible or not visible, according to user preferences.
   */
  @WorkerThread
  fun setVisibilityForAllBadgesSync(
    displayBadgesOnProfile: Boolean,
    selfBadges: List<Badge>
  ): NetworkResult<List<Badge>> {
    Log.d(TAG, "[setVisibilityForAllBadgesSync] Setting badge visibility...", true)

    val recipientTable: RecipientTable = SignalDatabase.recipients
    val badges = selfBadges.map { it.copy(visible = displayBadgesOnProfile) }

    Log.d(TAG, "[setVisibilityForAllBadgesSync] Uploading profile...", true)
    return uploadProfile(badges).map {
      SignalStore.inAppPayments.setDisplayBadgesOnProfile(displayBadgesOnProfile)
      recipientTable.markNeedsSync(Recipient.self().id)

      Log.d(TAG, "[setVisibilityForAllBadgesSync] Requesting data change sync...", true)
      StorageSyncHelper.scheduleSyncForDataChange()

      badges
    }
  }

  suspend fun setVisibilityForAllBadges(
    displayBadgesOnProfile: Boolean,
    selfBadges: List<Badge> = Recipient.self().badges
  ): NetworkResult<Unit> = withContext(SignalDispatchers.IO) {
    setVisibilityForAllBadgesSync(displayBadgesOnProfile, selfBadges).map {
      Log.d(TAG, "[setVisibilityForAllBadges] Enqueueing profile refresh...", true)
      AppDependencies.jobManager
        .startChain(RefreshOwnProfileJob())
        .then(MultiDeviceProfileContentUpdateJob())
        .enqueue()
    }
  }

  suspend fun setFeaturedBadge(featuredBadge: Badge): NetworkResult<Unit> = withContext(SignalDispatchers.IO) {
    val badges = Recipient.self().badges
    val reOrderedBadges = listOf(featuredBadge.copy(visible = true)) + (badges.filterNot { it.id == featuredBadge.id })

    Log.d(TAG, "[setFeaturedBadge] Uploading profile with reordered badges...", true)
    return@withContext uploadProfile(reOrderedBadges).runIfSuccessful {
      Log.d(TAG, "[setFeaturedBadge] Enqueueing profile refresh...", true)
      AppDependencies.jobManager
        .startChain(RefreshOwnProfileJob())
        .then(MultiDeviceProfileContentUpdateJob())
        .enqueue()
    }
  }

  /**
   * Applies the badge toggle shown after a donation. Runs outside of any UI scope so that it completes after the sheet is gone.
   */
  fun commitDonationBadgeToggle(toggle: DonationBadgeToggle, badge: Badge, isChecked: Boolean) {
    SignalExecutors.UNBOUNDED.execute {
      when (toggle) {
        DonationBadgeToggle.DISPLAY_ON_PROFILE -> {
          val result = runBlocking { setVisibilityForAllBadges(isChecked) }
          if (result !is NetworkResult.Success) {
            Log.w(TAG, "Failure while updating badge visibility", result.getCause())
          }
        }

        DonationBadgeToggle.MAKE_FEATURED -> {
          if (!isChecked) {
            return@execute
          }

          val result = runBlocking { setFeaturedBadge(badge) }
          if (result !is NetworkResult.Success) {
            Log.w(TAG, "Failure while updating featured badge", result.getCause())
          }
        }
      }
    }
  }

  private fun uploadProfile(badges: List<Badge>): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      ProfileUtil.uploadProfileWithBadges(AppDependencies.application, badges)
    }
  }

  enum class DonationBadgeToggle {
    DISPLAY_ON_PROFILE,
    MAKE_FEATURED
  }
}
