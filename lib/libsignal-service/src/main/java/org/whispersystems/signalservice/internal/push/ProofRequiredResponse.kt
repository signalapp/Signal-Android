/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

data class ProofRequiredResponse(
  val token: String = "",
  val options: List<String> = emptyList()
)
