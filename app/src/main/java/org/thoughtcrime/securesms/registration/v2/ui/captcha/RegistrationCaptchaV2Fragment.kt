/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.captcha

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import org.thoughtcrime.securesms.registration.v2.data.network.Challenge
import org.thoughtcrime.securesms.registration.v2.ui.RegistrationV2ViewModel

/**
 * Screen that displays a captcha as part of the registration flow.
 * This subclass plugs in [RegistrationV2ViewModel] to the shared super class.
 *
 * @see CaptchaV2Fragment
 */
class RegistrationCaptchaV2Fragment : CaptchaV2Fragment() {
  private val sharedViewModel by activityViewModels<RegistrationV2ViewModel>()
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
