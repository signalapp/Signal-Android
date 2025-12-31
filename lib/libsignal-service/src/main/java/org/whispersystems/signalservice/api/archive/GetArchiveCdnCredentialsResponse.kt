/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Get response with headers to use to read from archive cdn.
 */
class GetArchiveCdnCredentialsResponse(
  @JsonProperty val headers: Map<String, String>
)
