package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles

/**
 * [NotificationProfilesScreen] state.
 */
data class NotificationProfilesState(
  /** The user's profiles, newest first, or null until they have been read. */
  val profiles: List<NotificationProfile>? = null,
  /** The profile that is currently on, if any. */
  val activeProfileId: Long? = null,
  /** How long the active profile stays on, or null if none is on. */
  val activeStatus: NotificationProfiles.ActiveStatus? = null
) {
  override fun toString(): String = "NotificationProfilesState(profiles=${profiles?.size}, activeProfileId=$activeProfileId, activeStatus=$activeStatus)"
}
