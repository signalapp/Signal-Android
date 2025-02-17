/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.Test
import org.whispersystems.signalservice.internal.util.JsonUtil

class GroupStaleDevicesTest {
  @Test
  fun testSimpleParse() {
    val json = """
      [
        {
          "uuid": "12345678-1234-1234-1234-123456789012",
          "devices": {
            "staleDevices": [3]
          }
        },
        {
          "uuid": "22345678-1234-1234-1234-123456789012",
          "devices": {
            "staleDevices": [2]
          }
        }
      ]
    """.trimIndent()

    val parsed: Array<GroupStaleDevices> = JsonUtil.fromJson(json, Array<GroupStaleDevices>::class.java)

    assertThat(parsed).hasSize(2)
    val (first, second) = parsed

    assertThat(first.uuid).isEqualTo("12345678-1234-1234-1234-123456789012")
    assertThat(first.devices.staleDevices).containsExactly(3)

    assertThat(second.uuid).isEqualTo("22345678-1234-1234-1234-123456789012")
    assertThat(second.devices.staleDevices).containsExactly(2)
  }
}
