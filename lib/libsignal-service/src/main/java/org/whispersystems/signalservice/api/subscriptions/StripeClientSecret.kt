/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.subscriptions

data class StripeClientSecret(
  val clientSecret: String
) {
  val id: String
    get() = clientSecret.replaceFirst("_secret.*".toRegex(), "")
}
