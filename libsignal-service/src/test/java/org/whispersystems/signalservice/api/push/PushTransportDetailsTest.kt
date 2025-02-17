/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.push

import assertk.assertThat
import assertk.assertions.hasSize
import org.junit.Test
import org.whispersystems.signalservice.internal.push.PushTransportDetails

class PushTransportDetailsTest {
  private val transportV3 = PushTransportDetails()

  @Test
  fun testV3Padding() {
    (0 until 159).forEach { i ->
      val message = ByteArray(i)
      assertThat(transportV3.getPaddedMessageBody(message)).hasSize(159)
    }

    (159 until 319).forEach { i ->
      val message = ByteArray(i)
      assertThat(transportV3.getPaddedMessageBody(message)).hasSize(319)
    }

    (319 until 479).forEach { i ->
      val message = ByteArray(i)
      assertThat(transportV3.getPaddedMessageBody(message)).hasSize(479)
    }
  }
}
