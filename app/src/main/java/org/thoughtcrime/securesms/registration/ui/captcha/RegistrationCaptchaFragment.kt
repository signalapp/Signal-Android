/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.captcha

import androidx.fragment.app.activityViewModels
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel

/**
 * Screen that displays a captcha as part of the registration flow.
 * This subclass plugs in [RegistrationViewModel] to the shared super class.
 *
 * @see CaptchaFragment
 */
class RegistrationCaptchaFragment : CaptchaFragment() {
  private val sharedViewModel by activityViewModels<RegistrationViewModel>()

  override fun handleCaptchaToken(token: String) {
    sharedViewModel.setCaptchaResponse(token)
  }
}
