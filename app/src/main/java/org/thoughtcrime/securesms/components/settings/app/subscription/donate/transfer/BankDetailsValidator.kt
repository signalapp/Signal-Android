/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer

object BankDetailsValidator {

  private val EMAIL_REGEX: Regex = ".+@.+\\..+".toRegex()

  fun validName(name: String): Boolean {
    return name.length >= 2
  }

  fun validEmail(email: String): Boolean {
    return email.length >= 3 && email.matches(EMAIL_REGEX)
  }
}
