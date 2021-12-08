package org.thoughtcrime.securesms.components.settings.app.notifications.manual

import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import java.util.Calendar

data class NotificationProfileSelectionState(
  val notificationProfiles: List<NotificationProfile> = listOf(),
  val expandedId: Long = -1L,
  val timeSlotB: Calendar
)
