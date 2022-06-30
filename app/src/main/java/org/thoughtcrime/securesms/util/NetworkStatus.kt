package org.thoughtcrime.securesms.util

/**
 * Information about the current network status by our best guess. This information
 * isn't guaranteed to be 100% accurate.
 */
data class NetworkStatus(val isOnVpn: Boolean, val isMetered: Boolean) {
  override fun toString(): String {
    return "[isOnVpn: $isOnVpn, isMetered: $isMetered]"
  }
}
