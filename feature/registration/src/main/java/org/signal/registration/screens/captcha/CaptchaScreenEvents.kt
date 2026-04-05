/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.captcha

import org.signal.core.util.censor
import org.signal.registration.util.DebugLoggableModel

sealed class CaptchaScreenEvents : DebugLoggableModel() {
  data class CaptchaCompleted(val token: String) : CaptchaScreenEvents() {
    override fun toSafeString(): String {
      return "CaptchaCompleted(token=${token.censor()})"
    }
  }
  data object Cancel : CaptchaScreenEvents()
}
