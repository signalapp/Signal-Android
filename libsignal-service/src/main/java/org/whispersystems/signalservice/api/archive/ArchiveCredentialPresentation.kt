/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

/**
 * Acts as credentials for various archive operations.
 */
class ArchiveCredentialPresentation(
  val presentation: ByteArray,
  val signedPresentation: ByteArray
)
