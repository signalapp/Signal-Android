/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.captcha

import org.signal.core.util.censor

sealed class CaptchaScreenEvents {
  data class CaptchaCompleted(val token: String) : CaptchaScreenEvents() {
    override fun toString(): String = "CaptchaCompleted(token=${token.censor()})"
  }
  data object Cancel : CaptchaScreenEvents()
}
