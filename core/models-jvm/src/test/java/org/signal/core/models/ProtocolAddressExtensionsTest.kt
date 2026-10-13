/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import org.junit.Test
import org.signal.libsignal.protocol.SignalProtocolAddress

class ProtocolAddressExtensionsTest {

  companion object {
    private const val UUID = "d81b9a54-2c3e-4f6a-9b1d-7e8f9a0b1c2d"
  }

  @Test
  fun `service ids parsed from different casings are equal`() {
    assertThat(ServiceId.parseOrThrow(UUID.uppercase())).isEqualTo(ServiceId.parseOrThrow(UUID))
    assertThat(ServiceId.parseOrThrow("PNI:${UUID.uppercase()}")).isEqualTo(ServiceId.parseOrThrow("PNI:$UUID"))
  }

  @Test
  fun `aci and pni with the same uuid are not equal`() {
    assertThat(ServiceId.parseOrThrow(UUID)).isNotEqualTo(ServiceId.parseOrThrow("PNI:$UUID"))
  }

  @Test
  fun `normalized rebuilds uppercase aci addresses`() {
    assertThat(SignalProtocolAddress(UUID.uppercase(), 2).normalized()).isEqualTo(SignalProtocolAddress(UUID, 2))
  }

  @Test
  fun `normalized rebuilds uppercase pni addresses`() {
    assertThat(SignalProtocolAddress("PNI:${UUID.uppercase()}", 1).normalized()).isEqualTo(SignalProtocolAddress("PNI:$UUID", 1))
  }

  @Test
  fun `normalized leaves canonical addresses unchanged`() {
    assertThat(SignalProtocolAddress(UUID, 1).normalized()).isEqualTo(SignalProtocolAddress(UUID, 1))
  }

  @Test
  fun `normalized leaves non service id addresses unchanged`() {
    assertThat(SignalProtocolAddress("+15555550100", 1).normalized()).isEqualTo(SignalProtocolAddress("+15555550100", 1))
  }

  @Test
  fun `normalizeAddressName canonicalizes service ids and leaves other names alone`() {
    assertThat(ServiceId.normalizeAddressName(UUID.uppercase())).isEqualTo(UUID)
    assertThat(ServiceId.normalizeAddressName("+15555550100")).isEqualTo("+15555550100")
  }
}
