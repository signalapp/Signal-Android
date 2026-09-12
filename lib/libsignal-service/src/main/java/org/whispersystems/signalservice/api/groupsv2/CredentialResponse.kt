/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.groupsv2

class CredentialResponse(
  val credentials: Array<TemporalCredential> = emptyArray(),
  val callLinkAuthCredentials: Array<TemporalCredential> = emptyArray()
)
