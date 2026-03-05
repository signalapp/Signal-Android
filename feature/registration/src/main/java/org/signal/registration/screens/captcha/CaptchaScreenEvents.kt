/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.captcha

sealed class CaptchaScreenEvents {
  data class CaptchaCompleted(val token: String) : CaptchaScreenEvents()
  data object Cancel : CaptchaScreenEvents()
}
