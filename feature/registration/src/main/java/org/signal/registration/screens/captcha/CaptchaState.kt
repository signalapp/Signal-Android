/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.captcha

import org.signal.core.util.censor

sealed class CaptchaLoadState {
  data object Loading : CaptchaLoadState()
  data object Loaded : CaptchaLoadState()
  data object Error : CaptchaLoadState()
}

data class CaptchaState(
  val captchaUrl: String,
  val captchaScheme: String = "signalcaptcha://",
  val loadState: CaptchaLoadState = CaptchaLoadState.Loading
) {
  override fun toString(): String = "CaptchaState(captchaUrl=${captchaUrl.censor()}, captchaScheme=$captchaScheme, loadState=$loadState)"
}
