package org.thoughtcrime.securesms.megaphone

import android.app.Application
import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import org.json.JSONArray
import org.json.JSONException
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.CheckoutFlowActivity
import org.thoughtcrime.securesms.database.RemoteMegaphoneTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord.ActionId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.megaphone.RemoteMegaphoneRepository.Action
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.LocaleRemoteConfig
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.VersionTracker
import java.util.Objects
import kotlin.math.min
import kotlin.time.Duration.Companion.days

/**
 * Access point for interacting with Remote Megaphones.
 */
object RemoteMegaphoneRepository {

  private val TAG = Log.tag(RemoteMegaphoneRepository::class.java)

  private val db: RemoteMegaphoneTable = SignalDatabase.remoteMegaphones
  private val context: Application = AppDependencies.application

  private val snooze: Action = Action { _, controller, remote ->
    controller.onMegaphoneSnooze(Megaphones.Event.REMOTE_MEGAPHONE)
    SignalExecutors.BOUNDED_IO.execute {
      db.snooze(remote)
    }
  }

  private val finish: Action = Action { context, controller, remote ->
    if (remote.imageUri != null) {
      BlobProvider.getInstance().delete(context, remote.imageUri)
    }
    controller.onMegaphoneSnooze(Megaphones.Event.REMOTE_MEGAPHONE)
    SignalExecutors.BOUNDED_IO.execute {
      db.markFinished(remote.uuid)
    }
  }

  private val donate: Action = Action { context, controller, remote ->
    controller.onMegaphoneNavigationRequested(CheckoutFlowActivity.createIntent(context, InAppPaymentType.ONE_TIME_DONATION))
    snooze.run(context, controller, remote)
  }

  private val donateForFriend: Action = Action { context, controller, remote ->
    controller.onMegaphoneNavigationRequested(CheckoutFlowActivity.createIntent(context, InAppPaymentType.ONE_TIME_GIFT))
    snooze.run(context, controller, remote)
  }

  private val actions = mapOf(
    ActionId.SNOOZE.id to snooze,
    ActionId.FINISH.id to finish,
    ActionId.DONATE.id to donate,
    ActionId.DONATE_FOR_FRIEND.id to donateForFriend
  )

  @WorkerThread
  @JvmStatic
  fun hasRemoteMegaphoneToShow(canShowLocalDonate: Boolean): Boolean {
    val record = getRemoteMegaphoneToShow()

    return if (record == null) {
      false
    } else if (record.primaryActionId?.isDonateAction == true) {
      canShowLocalDonate
    } else {
      true
    }
  }

  @WorkerThread
  @JvmStatic
  fun getRemoteMegaphoneToShow(now: Long = System.currentTimeMillis()): RemoteMegaphoneRecord? {
    return db.getPotentialMegaphonesAndClearOld(now)
      .asSequence()
      .filter { it.imageUrl == null || it.imageUri != null }
      .filter { it.countries == null || LocaleRemoteConfig.shouldShowReleaseNote(it.uuid, it.countries) }
      .filter { it.conditionalId == null || checkCondition(it.conditionalId) }
      .filter { it.snoozedAt == 0L || checkSnooze(it, now) }
      .firstOrNull()
  }

  @AnyThread
  @JvmStatic
  fun getAction(action: ActionId): Action {
    return actions[action.id] ?: finish
  }

  @AnyThread
  @JvmStatic
  fun markShown(uuid: String) {
    SignalExecutors.BOUNDED_IO.execute {
      db.markShown(uuid)
    }
  }

  private fun checkCondition(conditionalId: String): Boolean {
    return when (conditionalId) {
      "standard_donate" -> shouldShowDonateMegaphone()
      "internal_user" -> RemoteConfig.internalUser
      else -> false
    }
  }

  private fun checkSnooze(record: RemoteMegaphoneRecord, now: Long): Boolean {
    if (record.seenCount == 0) {
      return true
    }

    val gaps: JSONArray? = record.getDataForAction(ActionId.SNOOZE)?.getJSONArray("snoozeDurationDays")?.takeIf { it.length() > 0 }
    val gapDays: Int? = gaps?.getIntOrNull(record.seenCount - 1)

    return gapDays == null || (record.snoozedAt + gapDays.days.inWholeMilliseconds <= now)
  }

  private fun shouldShowDonateMegaphone(): Boolean {
    return VersionTracker.getDaysSinceFirstInstalled(context) >= 7 &&
      InAppDonations.hasAtLeastOnePaymentMethodAvailable() &&
      !InAppPaymentsRepository.hasPendingDonation() &&
      Recipient.self()
        .badges
        .stream()
        .filter { obj: Badge? -> Objects.nonNull(obj) }
        .noneMatch { (_, category): Badge -> category === Badge.Category.Donor }
  }

  fun interface Action {
    fun run(context: Context, controller: MegaphoneActionController, remoteMegaphone: RemoteMegaphoneRecord)
  }

  /**
   * Gets the int at the specified index, or last index of array if larger then array length, or null if unable to parse json
   */
  private fun JSONArray.getIntOrNull(index: Int): Int? {
    return try {
      getInt(min(index, length() - 1))
    } catch (e: JSONException) {
      Log.w(TAG, "Unable to parse", e)
      null
    }
  }
}
