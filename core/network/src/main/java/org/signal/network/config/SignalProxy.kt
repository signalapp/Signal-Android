/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.config

class SignalProxy(val host: String, val port: Int) {
  fun toProxyConfig(): ProxyConfig = ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.TLS, host, port)
}
