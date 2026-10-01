/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

/**
 * Represents the body of a 409 response from the service during a sender key send.
 */
data class GroupMismatchedDevices(
  val uuid: String,
  val devices: MismatchedDevices
)
