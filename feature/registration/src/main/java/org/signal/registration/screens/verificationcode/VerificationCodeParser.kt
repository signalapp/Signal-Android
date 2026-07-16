/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import java.util.regex.Pattern

/**
 * Extracts a verification code from the body of an SMS delivered by the Play Services SMS retriever.
 */
object VerificationCodeParser {

  private val CHALLENGE_PATTERN = Pattern.compile("(.*\\D|^)([0-9]{3,4})-?([0-9]{3,4}).*", Pattern.DOTALL)

  fun parse(messageBody: String?): String? {
    if (messageBody == null) {
      return null
    }

    val matcher = CHALLENGE_PATTERN.matcher(messageBody)
    if (!matcher.matches()) {
      return null
    }

    return matcher.group(matcher.groupCount() - 1) + matcher.group(matcher.groupCount())
  }
}
