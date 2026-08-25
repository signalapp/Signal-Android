/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.authenticatorcodeentry

data class AuthenticatorCodeEntryState(
  val code: String = "",
  val submitting: Boolean = false
) {

  val canSubmit: Boolean
    get() = code.length == CODE_LENGTH && !submitting

  override fun toString(): String = "AuthenticatorCodeEntryState(codeLength=${code.length}, submitting=$submitting)"

  companion object {
    const val CODE_LENGTH = 6
  }
}
