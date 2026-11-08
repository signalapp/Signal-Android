/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.crypto

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.mockk.mockk
import org.junit.Test

class SealedSenderAccessTest {

  private val story = SealedSenderAccess.Story(mockk())

  @Test
  fun `story has no header and no fallback`() {
    assertThat(story.applyHeader()).isEqualTo(false)
    assertThat(story.switchToFallback()).isNull()
    assertFailure { story.header }.isInstanceOf(UnsupportedOperationException::class)
  }

  @Test
  fun `story access is only valid for stories`() {
    assertThat(SealedSenderAccess.isUnrestrictedForStory(story)).isEqualTo(true)
    assertThat(SealedSenderAccess.isUnrestrictedForStory(null)).isEqualTo(false)
  }
}
