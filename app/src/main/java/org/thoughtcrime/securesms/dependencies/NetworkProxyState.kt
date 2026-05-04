/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import org.thoughtcrime.securesms.net.ProxyConfig
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks the proxy configuration that has been applied to the [org.signal.libsignal.net.Network]
 * instance so callers can detect changes and restart connections when needed.
 */
class NetworkProxyState {

  private val current = AtomicReference<ProxyConfig>(ProxyConfig.Direct)

  val currentConfig: ProxyConfig
    get() = current.get()

  /** Returns true if the proxy changed and connections should be restarted. */
  fun update(proxyConfig: ProxyConfig): Boolean {
    val prev = current.getAndUpdate { proxyConfig }
    return prev != proxyConfig
  }
}
