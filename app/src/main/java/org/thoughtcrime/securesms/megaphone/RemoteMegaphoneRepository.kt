package org.thoughtcrime.securesms.megaphone

import android.app.Application
import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.database.RemoteMegaphoneDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord.ActionId
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.megaphone.RemoteMegaphoneRepository.Action
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.LocaleFeatureFlags
import org.thoughtcrime.securesms.util.PlayServicesUtil
import org.thoughtcrime.securesms.util.VersionTracker
import java.util.Objects

/**
 * Access point for interacting with Remote Megaphones.
 */
object RemoteMegaphoneRepository {

  private val db: RemoteMegaphoneDatabase = SignalDatabase.remoteMegaphones
  private val context: Application = ApplicationDependencies.getApplication()

  private val snooze: Action = Action { _, controller, _ -> controller.onMegaphoneSnooze(Megaphones.Event.REMOTE_MEGAPHONE) }

  private val finish: Action = Action { context, controller, remote ->
    if (remote.imageUri != null) {
      BlobProvider.getInstance().delete(context, remote.imageUri)
    }
    controller.onMegaphoneSnooze(Megaphones.Event.REMOTE_MEGAPHONE)
    db.markFinished(remote.uuid)
  }

  private val donate: Action = Action { context, controller, remote ->
    controller.onMegaphoneNavigationRequested(AppSettingsActivity.manageSubscriptions(context))
    finish.run(context, controller, remote)
  }

  private val actions = mapOf(
    ActionId.SNOOZE.id to snooze,
    ActionId.FINISH.id to finish,
    ActionId.DONATE.id to donate
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
  fun getRemoteMegaphoneToShow(): RemoteMegaphoneRecord? {
    return db.getPotentialMegaphonesAndClearOld()
      .asSequence()
      .filter { it.imageUrl == null || it.imageUri != null }
      .filter { it.countries == null || LocaleFeatureFlags.shouldShowReleaseNote(it.uuid, it.countries) }
      .filter { it.conditionalId == null || checkCondition(it.conditionalId) }
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
      else -> false
    }
  }

  private fun shouldShowDonateMegaphone(): Boolean {
    return VersionTracker.getDaysSinceFirstInstalled(context) >= 7 &&
      PlayServicesUtil.getPlayServicesStatus(context) == PlayServicesUtil.PlayServicesStatus.SUCCESS &&
      Recipient.self()
        .badges
        .stream()
        .filter { obj: Badge? -> Objects.nonNull(obj) }
        .noneMatch { (_, category): Badge -> category === Badge.Category.Donor }
  }

  fun interface Action {
    fun run(context: Context, controller: MegaphoneActionController, remoteMegaphone: RemoteMegaphoneRecord)
  }
}
