/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.push.exceptions

import org.signal.network.exceptions.MalformedResponseException
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.signal.network.util.JsonUtil

/**
 * Response indicating we gave the server a non-normalized phone number. The expected normalized version of the number is provided.
 */
class NonNormalizedPhoneNumberException(
  val originalNumber: String,
  val normalizedNumber: String
) : NonSuccessfulResponseCodeException(400) {

  /** The 400 response body. Separate from the exception itself so that it can be deserialized. */
  class Body(
    val originalNumber: String? = null,
    val normalizedNumber: String? = null
  )

  companion object {
    @JvmStatic
    @Throws(MalformedResponseException::class)
    fun forResponse(responseBody: String): NonNormalizedPhoneNumberException {
      val body = JsonUtil.fromJsonResponse(responseBody, Body::class.java)

      if (body.originalNumber == null || body.normalizedNumber == null) {
        throw MalformedResponseException("Response is missing a number")
      }

      return NonNormalizedPhoneNumberException(body.originalNumber, body.normalizedNumber)
    }
  }
}
