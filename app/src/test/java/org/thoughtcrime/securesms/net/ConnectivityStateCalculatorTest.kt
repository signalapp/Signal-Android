/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class ConnectivityStateCalculatorTest {

  @Test
  fun `isOnline is true only for validated online states`() {
    assertThat(ConnectivityState.ONLINE.isOnline).isTrue()
    assertThat(ConnectivityState.ONLINE_VPN.isOnline).isTrue()
    assertThat(ConnectivityState.CONNECTED_UNVALIDATED.isOnline).isFalse()
    assertThat(ConnectivityState.MONITORING_UNAVAILABLE.isOnline).isFalse()
    assertThat(ConnectivityState.OFFLINE.isOnline).isFalse()
    assertThat(ConnectivityState.BLOCKED.isOnline).isFalse()
    assertThat(ConnectivityState.BLOCKED_VPN.isOnline).isFalse()
  }

  @Test
  fun `isAssumedOnline adds the fail-open states`() {
    assertThat(ConnectivityState.ONLINE.isAssumedOnline).isTrue()
    assertThat(ConnectivityState.ONLINE_VPN.isAssumedOnline).isTrue()
    assertThat(ConnectivityState.CONNECTED_UNVALIDATED.isAssumedOnline).isTrue()
    assertThat(ConnectivityState.MONITORING_UNAVAILABLE.isAssumedOnline).isTrue()
    assertThat(ConnectivityState.OFFLINE.isAssumedOnline).isFalse()
    assertThat(ConnectivityState.BLOCKED.isAssumedOnline).isFalse()
    assertThat(ConnectivityState.BLOCKED_VPN.isAssumedOnline).isFalse()
  }

  @Test
  fun `no networks is OFFLINE`() {
    assertThat(ConnectivityStateCalculator.calculate(emptyList())).isEqualTo(ConnectivityState.OFFLINE)
  }

  @Test
  fun `validated non-vpn network is ONLINE`() {
    val states = listOf(NetworkState(validated = true, blocked = false, onVpn = false))
    assertThat(ConnectivityStateCalculator.calculate(states)).isEqualTo(ConnectivityState.ONLINE)
  }

  @Test
  fun `connected but unvalidated non-vpn network is CONNECTED_UNVALIDATED`() {
    val states = listOf(NetworkState(validated = false, blocked = false, onVpn = false))
    assertThat(ConnectivityStateCalculator.calculate(states)).isEqualTo(ConnectivityState.CONNECTED_UNVALIDATED)
  }

  @Test
  fun `blocked non-vpn network is BLOCKED`() {
    val states = listOf(NetworkState(validated = true, blocked = true, onVpn = false))
    assertThat(ConnectivityStateCalculator.calculate(states)).isEqualTo(ConnectivityState.BLOCKED)
  }

  @Test
  fun `picks best of multiple networks`() {
    val states = listOf(
      NetworkState(validated = false, blocked = false, onVpn = false),
      NetworkState(validated = true, blocked = false, onVpn = false)
    )
    assertThat(ConnectivityStateCalculator.calculate(states)).isEqualTo(ConnectivityState.ONLINE)
  }

  @Test
  fun `vpn without an underlying network is OFFLINE (kill switch)`() {
    val states = listOf(NetworkState(validated = true, blocked = false, onVpn = true))
    assertThat(ConnectivityStateCalculator.calculate(states)).isEqualTo(ConnectivityState.OFFLINE)
  }

  @Test
  fun `vpn with a validated underlying network is ONLINE_VPN`() {
    val states = listOf(
      NetworkState(validated = true, blocked = false, onVpn = true),
      NetworkState(validated = true, blocked = false, onVpn = false)
    )
    assertThat(ConnectivityStateCalculator.calculate(states)).isEqualTo(ConnectivityState.ONLINE_VPN)
  }

  @Test
  fun `vpn with a blocked underlying network is ONLINE_VPN`() {
    val states = listOf(
      NetworkState(validated = true, blocked = false, onVpn = true),
      NetworkState(validated = true, blocked = true, onVpn = false)
    )
    assertThat(ConnectivityStateCalculator.calculate(states)).isEqualTo(ConnectivityState.ONLINE_VPN)
  }

  @Test
  fun `blocked underlying network after the vpn drops is BLOCKED (kill switch)`() {
    val states = listOf(NetworkState(validated = true, blocked = true, onVpn = false))
    assertThat(ConnectivityStateCalculator.calculate(states)).isEqualTo(ConnectivityState.BLOCKED)
  }

  @Test
  fun `a usable unvalidated network outranks a blocked one`() {
    val states = listOf(
      NetworkState(validated = true, blocked = true, onVpn = false),
      NetworkState(validated = false, blocked = false, onVpn = false)
    )
    assertThat(ConnectivityStateCalculator.calculate(states)).isEqualTo(ConnectivityState.CONNECTED_UNVALIDATED)
  }
}
