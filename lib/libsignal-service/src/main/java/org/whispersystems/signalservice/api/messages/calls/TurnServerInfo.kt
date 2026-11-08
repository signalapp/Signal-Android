/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages.calls

data class TurnServerInfo(
  val username: String? = null,
  val password: String? = null,
  /** Hostname for the ips in [urlsWithIps]. */
  val hostname: String? = null,
  val urls: List<String>? = null,
  val urlsWithIps: List<String>? = null,
  val ttl: Long? = null
)
