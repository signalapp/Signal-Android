package org.whispersystems.signalservice.internal.push

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.Test
import org.whispersystems.signalservice.internal.util.JsonUtil

class GroupMismatchedDevicesTest {
  @Test
  fun testSimpleParse() {
    val json = """
      [
        {
          "uuid": "12345678-1234-1234-1234-123456789012",
          "devices": {
            "missingDevices": [1, 2],
            "extraDevices": [3]
          }
        },
        {
          "uuid": "22345678-1234-1234-1234-123456789012",
          "devices": {
            "missingDevices": [],
            "extraDevices": [2]
          }
        }
      ]
    """.trimIndent()
    val parsed = JsonUtil.fromJson(json, Array<GroupMismatchedDevices>::class.java)

    assertThat(parsed).hasSize(2)
    val (first, second) = parsed

    assertThat(first.uuid).isEqualTo("12345678-1234-1234-1234-123456789012")
    assertThat(first.devices.missingDevices).containsExactly(1, 2)
    assertThat(first.devices.extraDevices).containsExactly(3)

    assertThat(second.uuid).isEqualTo("22345678-1234-1234-1234-123456789012")
    assertThat(second.devices.extraDevices).containsExactly(2)
  }
}
