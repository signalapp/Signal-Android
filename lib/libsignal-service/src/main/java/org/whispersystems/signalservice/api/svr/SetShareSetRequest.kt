/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.whispersystems.signalservice.internal.push.ByteArraySerializerBase64NoPadding

/**
 * Request body for setting a share-set on the service.
 */
class SetShareSetRequest(
  @JsonProperty
  @JsonSerialize(using = ByteArraySerializerBase64NoPadding::class)
  val shareSet: ByteArray
)
