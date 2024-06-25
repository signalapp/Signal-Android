/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.captcha

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel

/**
 * Screen that displays a captcha as part of the registration flow.
 * This subclass plugs in [RegistrationViewModel] to the shared super class.
 *
 * @see CaptchaFragment
 */
class RegistrationCaptchaFragment : CaptchaFragment() {
  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    sharedViewModel.addPresentedChallenge(Challenge.CAPTCHA)
  }

  override fun handleCaptchaToken(token: String) {
    sharedViewModel.setCaptchaResponse(token)
  }

  override fun handleUserExit() {
    sharedViewModel.removePresentedChallenge(Challenge.CAPTCHA)
  }
}
