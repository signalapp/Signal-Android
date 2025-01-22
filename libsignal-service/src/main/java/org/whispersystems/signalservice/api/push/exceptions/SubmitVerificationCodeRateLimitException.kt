/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.push.exceptions

import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse

/**
 * Rate limit exception specific to submitting verification codes during registration.
 */
class SubmitVerificationCodeRateLimitException(
  val sessionMetadata: RegistrationSessionMetadataResponse
) : NonSuccessfulResponseCodeException(429, "User submit verification code rate limited")
