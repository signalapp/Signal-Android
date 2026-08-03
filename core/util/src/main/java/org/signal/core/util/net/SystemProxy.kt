/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.net

import androidx.annotation.WorkerThread
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

/**
 * Resolves the system-configured [Proxy] for the given URL using [ProxySelector].
 *
 * @return null if no proxy is configured.
 */
@WorkerThread
fun resolveSystemProxy(
  targetUrl: String,
  proxySelector: ProxySelector? = ProxySelector.getDefault()
): Proxy? {
  val proxyList = proxySelector?.select(URI.create(targetUrl))
  return proxyList?.firstOrNull()
}
