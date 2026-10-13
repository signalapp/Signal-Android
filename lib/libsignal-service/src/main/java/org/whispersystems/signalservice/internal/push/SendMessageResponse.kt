/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonIgnore

class SendMessageResponse(
  val needsSync: Boolean = false
) {
  /** Not part of the response body; set by the caller based on how the request was authenticated. */
  @JsonIgnore
  var sentUnidentified: Boolean = false
}
