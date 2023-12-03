/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

data class RemoteConfigResult(
  val config: Map<String, Any>,
  val serverEpochTimeSeconds: Long
)
