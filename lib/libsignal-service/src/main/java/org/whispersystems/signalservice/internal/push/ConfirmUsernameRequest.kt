/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

class ConfirmUsernameRequest(
  val usernameHash: String,
  val zkProof: String,
  val encryptedUsername: String
)
