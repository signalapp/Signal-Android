package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles

data class NotificationProfilesState(
  val profiles: List<NotificationProfile>,
  val activeProfile: NotificationProfile? = NotificationProfiles.getActiveProfile(profiles)
)
