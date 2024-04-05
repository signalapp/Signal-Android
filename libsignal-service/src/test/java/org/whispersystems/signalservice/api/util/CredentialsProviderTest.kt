/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.signalservice.api.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.util.UUID

class CredentialsProviderTest {
  private fun makeProvider(aci: UUID?, deviceId: Int = SignalServiceAddress.DEFAULT_DEVICE_ID): CredentialsProvider {
    return object : CredentialsProvider {
      override fun getAci(): ServiceId.ACI? {
        if (aci == null) {
          return null
        }
        return ServiceId.ACI.from(aci)
      }

      override fun getPni(): ServiceId.PNI {
        TODO("Not used")
      }

      override fun getE164(): String {
        TODO("Not used")
      }

      override fun getDeviceId(): Int {
        return deviceId
      }

      override fun getPassword(): String {
        TODO("Not used")
      }
    }
  }

  @Test
  fun usernameWithDefaultDeviceId() {
    val uuid = UUID.randomUUID()
    assertEquals(uuid.toString(), makeProvider(uuid).username)
  }

  @Test
  fun usernameWithDeviceId() {
    val uuid = UUID.randomUUID()
    assertEquals("$uuid.42", makeProvider(uuid, 42).username)
  }

  @Test
  fun usernameWithNullAci() {
    assertThrows(NullPointerException::class.java) { makeProvider(aci = null).username }
  }
}
