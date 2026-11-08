/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.vector

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars

class VectorAvatarCreationLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `default avatar`() = assertPassesLayoutChecks {
    VectorAvatarCreationScreen(
      state = VectorAvatarCreationState(Avatar.Vector(Avatars.defaultAvatarsForSelf.keys.first(), Avatars.colors[2], Avatar.DatabaseId.NotSet)),
      onEvent = {}
    )
  }
}
