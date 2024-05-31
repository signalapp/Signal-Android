/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber.v2

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import org.thoughtcrime.securesms.registration.v2.data.network.Challenge
import org.thoughtcrime.securesms.registration.v2.ui.captcha.CaptchaV2Fragment

/**
 * Screen visible to the user when they are to solve a captcha. @see [CaptchaV2Fragment]
 */
class ChangeNumberCaptchaV2Fragment : CaptchaV2Fragment() {
  private val viewModel by activityViewModels<ChangeNumberV2ViewModel>()
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    viewModel.addPresentedChallenge(Challenge.CAPTCHA)
  }

  override fun handleCaptchaToken(token: String) {
    viewModel.setCaptchaResponse(token)
  }

  override fun handleUserExit() {
    viewModel.removePresentedChallenge(Challenge.CAPTCHA)
  }
}
