/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.push.exceptions

import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse

/**
 * Rate limit exception specific to requesting a verification code for registration.
 */
class RequestVerificationCodeRateLimitException(
  val sessionMetadata: RegistrationSessionMetadataResponse
) : NonSuccessfulResponseCodeException(429, "User request verification code rate limited")
