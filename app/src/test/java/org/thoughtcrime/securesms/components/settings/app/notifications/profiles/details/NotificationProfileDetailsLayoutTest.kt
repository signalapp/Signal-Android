/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.emoji.EmojiDependenciesRule
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfileComponentsTestTags
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalStoreRule
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

class NotificationProfileDetailsLayoutTest : SignalLayoutStressTest() {

  /**
   * Member rows draw their avatars with AvatarImageView, which reads the recipient cache out of AppDependencies and chat
   * colors out of SignalStore, and render names with Emojifier.
   */
  @get:Rule
  val dependencies: RuleChain = RuleChain
    .outerRule(MockAppDependenciesRule())
    .around(SignalStoreRule())
    .around(EmojiDependenciesRule(ApplicationProvider.getApplicationContext()))

  private val members = listOf(
    recipient(1, "Alexandria Montgomery-Fitzgerald"),
    recipient(2, "Weekend hiking crew and assorted friends"),
    recipient(3, "Mom"),
    recipient(4, "Dad"),
    recipient(5, "Grandma"),
    recipient(6, "Work")
  )

  @Test
  fun `loading`() = assertPassesLayoutChecks {
    NotificationProfileDetailsScreen(state = NotificationProfileDetailsState(), onEvent = {})
  }

  @Test
  fun `off with no members or schedule`() = assertPassesLayoutChecks {
    NotificationProfileDetailsScreen(state = state(members = emptyList(), schedule = NotificationProfileSchedule(id = 1L)), onEvent = {})
  }

  @Test
  fun `on with a schedule and collapsed members`() = assertPassesLayoutChecks(ignoreTags = setOf(NotificationProfileComponentsTestTags.RECIPIENT_NAME)) {
    NotificationProfileDetailsScreen(
      state = state(members = members).copy(activeStatus = NotificationProfiles.ActiveStatus.OnUntil(LocalTime.of(17, 0))),
      onEvent = {}
    )
  }

  @Test
  fun `expanded members`() = assertPassesLayoutChecks(ignoreTags = setOf(NotificationProfileComponentsTestTags.RECIPIENT_NAME)) {
    NotificationProfileDetailsScreen(state = state(members = members).copy(expanded = true), onEvent = {})
  }

  @Test
  fun `confirm delete`() = assertPassesLayoutChecks {
    NotificationProfileDetailsScreen(state = state(members = emptyList()).copy(dialog = NotificationProfileDetailsState.Dialog.ConfirmDelete), onEvent = {})
  }

  private fun state(
    members: List<Recipient>,
    schedule: NotificationProfileSchedule = NotificationProfileSchedule(
      id = 1L,
      enabled = true,
      start = 2145,
      end = 1215,
      daysEnabled = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    )
  ): NotificationProfileDetailsState {
    return NotificationProfileDetailsState(
      profile = NotificationProfile(
        id = 1L,
        name = "Weekday evenings at home",
        emoji = "",
        createdAt = 0L,
        schedule = schedule,
        allowedMembers = members.map { it.id }.toSet(),
        notificationProfileId = NotificationProfileId(UUID(0L, 1L))
      ),
      members = members
    )
  }

  private fun recipient(id: Long, name: String): Recipient {
    return Recipient(
      id = RecipientId.from(id),
      isResolving = false,
      registeredValue = RecipientTable.RegisteredState.REGISTERED,
      systemContactName = name
    )
  }
}
