/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

enum class ConnectivityState {
  OFFLINE,
  ONLINE,
  ONLINE_VPN,
  BLOCKED,
  BLOCKED_VPN,

  /**
   * A network is connected but Android has not validated Internet reachability (e.g. a captive portal, or a
   * restrictive network that blocks the OS validation probe). Treated as online (see [isAssumedOnline]) so we
   * still try.
   */
  CONNECTED_UNVALIDATED,

  /**
   * We could not get connectivity information from the OS. We fail open and assume we are online until
   * we get a strong signal otherwise (e.g. [BLOCKED]), rather than sitting idle believing we are offline.
   */
  MONITORING_UNAVAILABLE;

  /** True only when the OS confirms validated Internet reachability. */
  val isOnline: Boolean
    get() = this == ONLINE || this == ONLINE_VPN

  /** [isOnline] plus the fail-open states we optimistically treat as online (connected-but-unvalidated, or unknown). */
  val isAssumedOnline: Boolean
    get() = isOnline || this == CONNECTED_UNVALIDATED || this == MONITORING_UNAVAILABLE
}

/** The tracked state of a single network. */
data class NetworkState(
  val validated: Boolean,
  val blocked: Boolean,
  val onVpn: Boolean,
  val blockedKnown: Boolean = false
) {
  val isReachable: Boolean get() = validated && !blocked

  val rank: Int
    get() = when {
      isReachable && onVpn -> 4
      isReachable -> 3
      !blocked -> 2
      blocked && onVpn -> 1
      else -> 0
    }
}

/**
 * Pure aggregation of per-network [NetworkState]s into a single [ConnectivityState]. No Android
 * dependencies so it can be unit tested directly.
 *
 * Android's default network callback only reports the single "best" network, but to handle VPNs
 * (especially VPNs with a kill switch) correctly we track all networks and reduce them here:
 * - No VPN: reachable when any non-VPN network is validated and not blocked.
 * - With a VPN: a VPN network only counts as validated if some non-VPN network is present, blocked or not.
 *   A lockdown VPN blocks the underlying network for our UID even while the VPN works, so requiring an
 *   unblocked one would report offline for every always-on VPN user. A kill switch instead surfaces as the
 *   VPN network disappearing, leaving only blocked networks.
 */
object ConnectivityStateCalculator {

  fun calculate(states: Collection<NetworkState>): ConnectivityState {
    if (states.isEmpty()) {
      return ConnectivityState.OFFLINE
    }

    val hasUnderlyingNetwork = states.any { !it.onVpn }
    val best = states
      .map { if (it.onVpn) it.copy(validated = it.validated && hasUnderlyingNetwork) else it }
      .maxBy { it.rank }

    return when {
      best.isReachable && best.onVpn -> ConnectivityState.ONLINE_VPN
      best.isReachable -> ConnectivityState.ONLINE
      best.blocked && best.onVpn -> ConnectivityState.BLOCKED_VPN
      best.blocked -> ConnectivityState.BLOCKED
      // Connected but unvalidated. A VPN-only setup with no underlying network is a kill switch, not a usable link.
      hasUnderlyingNetwork -> ConnectivityState.CONNECTED_UNVALIDATED
      else -> ConnectivityState.OFFLINE
    }
  }
}
