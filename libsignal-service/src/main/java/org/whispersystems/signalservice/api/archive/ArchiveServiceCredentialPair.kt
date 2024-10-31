/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

/**
 * A convenient container for passing around both a message and media archive service credential.
 */
data class ArchiveServiceCredentialPair(
  val messageCredential: ArchiveServiceCredential,
  val mediaCredential: ArchiveServiceCredential
)
