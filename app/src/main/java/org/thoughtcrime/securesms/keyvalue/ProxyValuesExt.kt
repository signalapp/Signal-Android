/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import org.signal.network.config.SignalProxy
import org.signal.storage.keyvalue.ProxyValues

/** The last-chosen proxy, whether or not it's currently enabled. */
val ProxyValues.proxy: SignalProxy?
  get() = proxyHost?.let { SignalProxy(it, proxyPort) }

fun ProxyValues.enableProxy(proxy: SignalProxy) {
  enableProxy(proxy.host, proxy.port)
}
