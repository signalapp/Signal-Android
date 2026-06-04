/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class EnterAepScreenEventHandlerTest {

  @Test
  fun `BackupKeyChanged - preserves entered display-equivalent characters while normalizing backup key`() {
    val updated = EnterAepScreenEventHandler.applyEvent(
      EnterAepState(),
      EnterAepEvents.BackupKeyChanged("a0O#=b")
    )

    assertThat(updated.enteredText).isEqualTo("a0O#=b")
    assertThat(updated.backupKey).isEqualTo("a0oo0b")
  }
}
