/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID
import org.signal.libsignal.protocol.ServiceId.Aci as LibSignalAci
import org.signal.libsignal.protocol.ServiceId.Pni as LibSignalPni

class ServiceIdTests {

  @Test
  fun `ServiceId parseOrNull String`() {
    val uuidString = UUID.randomUUID().toString()

    assertNull(ServiceId.parseOrNull(null as String?))
    assertNull(ServiceId.parseOrNull(""))
    assertNull(ServiceId.parseOrNull("asdf"))

    assertEquals(ACI.from(UUID.fromString(uuidString)), ServiceId.parseOrNull(uuidString))
    assertEquals(PNI.from(UUID.fromString(uuidString)), ServiceId.parseOrNull("PNI:$uuidString"))
  }

  @Test
  fun `ServiceId parseOrNull ByteArray`() {
    val uuid = UUID.randomUUID()
    val uuidString = uuid.toString()
    val uuidBytes = UuidUtil.toByteArray(uuid)

    assertNull(ServiceId.parseOrNull(null as ByteArray?))
    assertNull(ServiceId.parseOrNull(ByteArray(0)))
    assertNull(ServiceId.parseOrNull(byteArrayOf(1, 2, 3)))

    assertEquals(ACI.from(UUID.fromString(uuidString)), ServiceId.parseOrNull(uuidBytes))
    assertEquals(PNI.from(UUID.fromString(uuidString)), ServiceId.parseOrNull(LibSignalPni(uuid).toServiceIdBinary()))
    assertEquals(ACI.from(UUID.fromString(uuidString)), ServiceId.parseOrNull(LibSignalAci(uuid).toServiceIdFixedWidthBinary()))
    assertEquals(PNI.from(UUID.fromString(uuidString)), ServiceId.parseOrNull(LibSignalPni(uuid).toServiceIdFixedWidthBinary()))
  }

  @Test
  fun `ACI parseOrNull String`() {
    val uuid = UUID.randomUUID()
    val uuidString = uuid.toString()

    assertNull(ACI.parseOrNull(null as String?))
    assertNull(ACI.parseOrNull(""))
    assertNull(ACI.parseOrNull("asdf"))
    assertNull(ACI.parseOrNull(LibSignalPni(uuid).toServiceIdString()))

    assertEquals(ACI.from(UUID.fromString(uuidString)), ACI.parseOrNull(uuidString))
  }

  @Test
  fun `ACI parseOrNull ByteArray`() {
    val uuid = UUID.randomUUID()
    val uuidString = uuid.toString()
    val uuidBytes = UuidUtil.toByteArray(uuid)

    assertNull(ACI.parseOrNull(null as ByteArray?))
    assertNull(ACI.parseOrNull(ByteArray(0)))
    assertNull(ACI.parseOrNull(byteArrayOf(1, 2, 3)))
    assertNull(ACI.parseOrNull(LibSignalPni(uuid).toServiceIdBinary()))

    assertEquals(ACI.from(UUID.fromString(uuidString)), ACI.parseOrNull(uuidBytes))
    assertEquals(ACI.from(UUID.fromString(uuidString)), ACI.parseOrNull(LibSignalAci(uuid).toServiceIdBinary()))
    assertEquals(ACI.from(UUID.fromString(uuidString)), ACI.parseOrNull(LibSignalAci(uuid).toServiceIdFixedWidthBinary()))
  }

  @Test
  fun `PNI parseOrNull String`() {
    val uuidString = UUID.randomUUID().toString()

    assertNull(PNI.parseOrNull(null as String?))
    assertNull(PNI.parseOrNull(""))
    assertNull(PNI.parseOrNull("asdf"))

    assertEquals(PNI.from(UUID.fromString(uuidString)), PNI.parseOrNull(uuidString))
    assertEquals(PNI.from(UUID.fromString(uuidString)), PNI.parseOrNull("PNI:$uuidString"))
  }

  @Test
  fun `PNI parseOrNull ByteArray`() {
    val uuid = UUID.randomUUID()
    val uuidString = uuid.toString()
    val uuidBytes = UuidUtil.toByteArray(uuid)

    assertNull(PNI.parseOrNull(null as ByteArray?))
    assertNull(PNI.parseOrNull(ByteArray(0)))
    assertNull(PNI.parseOrNull(byteArrayOf(1, 2, 3)))
    assertNull(PNI.parseOrNull(LibSignalAci(uuid).toServiceIdFixedWidthBinary()))

    assertEquals(PNI.from(UUID.fromString(uuidString)), PNI.parseOrNull(uuidBytes))
    assertEquals(PNI.from(UUID.fromString(uuidString)), PNI.parseOrNull(LibSignalPni(uuid).toServiceIdBinary()))
  }

  @Test
  fun `PNI parsePrefixedOrNull`() {
    val uuidString = UUID.randomUUID().toString()

    assertNull(PNI.parsePrefixedOrNull(null))
    assertNull(PNI.parsePrefixedOrNull(""))
    assertNull(PNI.parsePrefixedOrNull("asdf"))
    assertNull(PNI.parsePrefixedOrNull(uuidString))

    assertEquals(PNI.from(UUID.fromString(uuidString)), PNI.parsePrefixedOrNull("PNI:$uuidString"))
  }
}
