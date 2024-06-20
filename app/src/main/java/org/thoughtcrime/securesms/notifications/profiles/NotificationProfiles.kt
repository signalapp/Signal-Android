package org.thoughtcrime.securesms.notifications.profiles

import android.content.Context
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.NotificationProfileValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.formatHours
import org.thoughtcrime.securesms.util.toLocalDateTime
import org.thoughtcrime.securesms.util.toLocalTime
import org.thoughtcrime.securesms.util.toMillis
import org.thoughtcrime.securesms.util.toOffset
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Helper for determining the single, currently active Notification Profile (if any) and also how to describe
 * how long the active profile will be on for.
 */
object NotificationProfiles {

  @JvmStatic
  @JvmOverloads
  fun getActiveProfile(profiles: List<NotificationProfile>, now: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): NotificationProfile? {
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

    if (manualProfile == null || scheduledProfile == null) {
      return manualProfile ?: scheduledProfile
    }

    return if (manualProfile == scheduledProfile) {
      manualProfile
    } else {
      scheduledProfile
    }
  }

  fun getActiveProfileDescription(context: Context, profile: NotificationProfile, now: Long = System.currentTimeMillis()): String {
    val storeValues: NotificationProfileValues = SignalStore.notificationProfile

    if (profile.id == storeValues.manuallyEnabledProfile) {
      if (storeValues.manuallyEnabledUntil.isForever()) {
        return context.getString(R.string.NotificationProfilesFragment__on)
      } else if (now < storeValues.manuallyEnabledUntil) {
        return context.getString(R.string.NotificationProfileSelection__on_until_s, storeValues.manuallyEnabledUntil.toLocalTime().formatHours(context))
      }
    }

    return context.getString(R.string.NotificationProfileSelection__on_until_s, profile.schedule.endTime().formatHours(context))
  }

  private fun Long.isForever(): Boolean {
    return this == Long.MAX_VALUE
  }
}
