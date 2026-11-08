/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

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
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalStoreRule
import java.util.UUID

class AddAllowedMembersLayoutTest : SignalLayoutStressTest() {

  /**
   * Member rows draw their avatars with AvatarImageView, which reads the recipient cache out of AppDependencies and chat
   * colors out of SignalStore, and render names with Emojifier.
   */
  @get:Rule
  val dependencies: RuleChain = RuleChain
    .outerRule(MockAppDependenciesRule())
    .around(SignalStoreRule())
    .around(EmojiDependenciesRule(ApplicationProvider.getApplicationContext()))

  @Test
  fun `no members`() = assertPassesLayoutChecks {
    AddAllowedMembersScreen(state = state(members = emptyList()), onEvent = {})
  }

  @Test
  fun `with members`() = assertPassesLayoutChecks(ignoreTags = setOf(NotificationProfileComponentsTestTags.RECIPIENT_NAME)) {
    AddAllowedMembersScreen(
      state = state(
        members = listOf(
          recipient(1, "Alexandria Montgomery-Fitzgerald"),
          recipient(2, "Weekend hiking crew and assorted friends")
        )
      ),
      onEvent = {}
    )
  }

  private fun state(members: List<Recipient>): AddAllowedMembersState {
    return AddAllowedMembersState(
      profile = NotificationProfile(
        id = 1L,
        name = "Work",
        emoji = "",
        createdAt = 0L,
        schedule = NotificationProfileSchedule(id = 1L),
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
