package org.thoughtcrime.securesms.notifications.profiles

import android.content.Context
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.core.util.toLocalDateTime
import org.signal.core.util.toLocalTime
import org.signal.core.util.toMillis
import org.signal.core.util.toOffset
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.NotificationProfileValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.formatHours
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Helper for determining the single, currently active Notification Profile (if any) and also how to describe
 * how long the active profile will be on for.
 */
object NotificationProfiles {

  val TAG = Log.tag(NotificationProfiles::class.java)

  @JvmStatic
  @JvmOverloads
  fun getActiveProfile(profiles: List<NotificationProfile>, now: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault(), shouldSync: Boolean = false): NotificationProfile? {
    val storeValues: NotificationProfileValues = SignalStore.notificationProfile
    val localNow: LocalDateTime = now.toLocalDateTime(zoneId)

    val manualProfile: NotificationProfile? = if (now < storeValues.manuallyEnabledUntil) {
      profiles.firstOrNull { it.id == storeValues.manuallyEnabledProfile }
    } else {
      null
    }

    val scheduledProfile: NotificationProfile? = profiles.sortedDescending().filter { it.schedule.isCurrentlyActive(now, zoneId) }.firstOrNull { profile ->
      profile.schedule.startDateTime(localNow).toMillis(zoneId.toOffset()) > storeValues.manuallyDisabledAt
    }

    if (shouldSync && shouldClearManualOverride(manualProfile, scheduledProfile)) {
      SignalExecutors.UNBOUNDED.execute {
        SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
        StorageSyncHelper.scheduleSyncForDataChange()
      }
    }

    if (manualProfile == null || scheduledProfile == null) {
      return manualProfile ?: scheduledProfile
    }

    return manualProfile
  }

  private fun shouldClearManualOverride(manualProfile: NotificationProfile?, scheduledProfile: NotificationProfile?): Boolean {
    val storeValues: NotificationProfileValues = SignalStore.notificationProfile
    var shouldScheduleSync = false

    if (manualProfile == null && storeValues.manuallyEnabledProfile != 0L) {
      Log.i(TAG, "Clearing override: ${storeValues.manuallyEnabledProfile} and ${storeValues.manuallyEnabledUntil}")
      storeValues.manuallyEnabledProfile = 0
      storeValues.manuallyEnabledUntil = 0
      shouldScheduleSync = true
    }

    if (scheduledProfile != null && storeValues.manuallyDisabledAt != 0L) {
      Log.i(TAG, "Clearing override: ${storeValues.manuallyDisabledAt}")
      storeValues.manuallyDisabledAt = 0
      shouldScheduleSync = true
    }

    return shouldScheduleSync
  }

  /**
   * How long [profile], which must be the active profile, stays on: indefinitely after being turned on by hand, or until a time.
   */
  fun getActiveStatus(profile: NotificationProfile, now: Long = System.currentTimeMillis()): ActiveStatus {
    val storeValues: NotificationProfileValues = SignalStore.notificationProfile

    if (profile.id == storeValues.manuallyEnabledProfile) {
      if (storeValues.manuallyEnabledUntil.isForever()) {
        return ActiveStatus.On
      } else if (now < storeValues.manuallyEnabledUntil) {
        return ActiveStatus.OnUntil(storeValues.manuallyEnabledUntil.toLocalTime())
      }
    }

    return ActiveStatus.OnUntil(profile.schedule.endTime())
  }

  fun getActiveProfileDescription(context: Context, profile: NotificationProfile, now: Long = System.currentTimeMillis()): String {
    return when (val status = getActiveStatus(profile, now)) {
      ActiveStatus.On -> context.getString(R.string.NotificationProfilesFragment__on)
      is ActiveStatus.OnUntil -> context.getString(R.string.NotificationProfileSelection__on_until_s, status.until.formatHours(context))
    }
  }

  private fun Long.isForever(): Boolean {
    return this == Long.MAX_VALUE
  }

  /** How long the active profile stays on. */
  sealed interface ActiveStatus {
    /** Turned on by hand with no end time. */
    data object On : ActiveStatus

    /** On until [until], either a manual end time or the end of the schedule. */
    data class OnUntil(val until: LocalTime) : ActiveStatus
  }
}
