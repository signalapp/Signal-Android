/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import java.time.LocalTime
import java.util.UUID

class NotificationProfilesLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `empty`() = assertPassesLayoutChecks {
    NotificationProfilesScreen(state = NotificationProfilesState(profiles = emptyList()), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    NotificationProfilesScreen(
      state = NotificationProfilesState(
        profiles = listOf(
          profile(3, "Weekend mornings at the cabin with family"),
          profile(2, "Sleep"),
          profile(1, "Work")
        ),
        activeProfileId = 3,
        activeStatus = NotificationProfiles.ActiveStatus.OnUntil(LocalTime.of(17, 0))
      ),
      onEvent = {}
    )
  }

  /** No emoji: EmojiUtil.convertToDrawable never returns under Robolectric, so rows use the moon icon instead. */
  private fun profile(id: Long, name: String): NotificationProfile {
    return NotificationProfile(
      id = id,
      name = name,
      emoji = "",
      createdAt = id,
      schedule = NotificationProfileSchedule(id = id),
      notificationProfileId = NotificationProfileId(UUID(0L, id))
    )
  }
}
