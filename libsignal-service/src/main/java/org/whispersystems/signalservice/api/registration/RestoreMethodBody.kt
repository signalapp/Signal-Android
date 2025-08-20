/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.registration

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.provisioning.RestoreMethod

/**
 * Request and response body used to communicate a quick restore method selection during registration.
 */
data class RestoreMethodBody @JsonCreator constructor(
  @JsonProperty val method: RestoreMethod?
)
