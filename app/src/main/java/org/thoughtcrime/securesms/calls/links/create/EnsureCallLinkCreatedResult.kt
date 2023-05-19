/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links.create

import org.thoughtcrime.securesms.service.webrtc.links.CreateCallLinkResult

sealed interface EnsureCallLinkCreatedResult {
  object Success : EnsureCallLinkCreatedResult
  data class Failure(val failure: CreateCallLinkResult.Failure) : EnsureCallLinkCreatedResult
}
