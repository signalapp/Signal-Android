/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents the result of fetching archive credentials.
 * See [ArchiveServiceCredential].
 */
class ArchiveServiceCredentialsResponse(
  @JsonProperty
  val credentials: Array<ArchiveServiceCredential>
)
