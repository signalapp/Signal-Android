/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.groupsv2

class TemporalCredential(
  val credential: ByteArray,
  val redemptionTime: Long
)
