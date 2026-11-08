/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

class VerifyAccountResponse(
  val uuid: String? = null,
  val pni: String? = null,
  val storageCapable: Boolean = false,
  val number: String? = null,
  val reregistration: Boolean = false
)
