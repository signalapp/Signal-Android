/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

data class OutgoingPushMessage(
  val type: Int,
  val destinationDeviceId: Int,
  val destinationRegistrationId: Int,
  val content: String
)
