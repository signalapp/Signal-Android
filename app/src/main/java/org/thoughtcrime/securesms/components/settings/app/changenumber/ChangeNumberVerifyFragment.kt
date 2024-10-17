/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.changeNumberSuccess
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Screen to show while the change number is in-progress.
 */
class ChangeNumberVerifyFragment : LoggingFragment(R.layout.fragment_change_phone_number_verify) {

  companion object {
    private val TAG: String = Log.tag(ChangeNumberVerifyFragment::class.java)
  }

  private val viewModel by activityViewModels<ChangeNumberViewModel>()
  private var dialogVisible: Boolean = false

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.ChangeNumberVerifyFragment__change_number)
    toolbar.setNavigationOnClickListener {
      findNavController().navigateUp()
      viewModel.resetLocalSessionState()
    }

    val status: TextView = view.findViewById(R.id.change_phone_number_verify_status)
    status.text = getString(R.string.ChangeNumberVerifyFragment__verifying_s, viewModel.number.fullFormattedNumber)

    viewModel.uiState.observe(viewLifecycleOwner, ::onStateUpdate)

    requestCode()
  }

  private fun onStateUpdate(state: ChangeNumberState) {
    if (state.challengesRequested.contains(Challenge.CAPTCHA) && state.captchaToken.isNotNullOrBlank()) {
      viewModel.submitCaptchaToken(requireContext())
    } else if (state.challengesRemaining.isNotEmpty()) {
      handleChallenges(state.challengesRemaining)
    } else if (state.changeNumberOutcome != null) {
      handleRequestCodeResult(state.changeNumberOutcome)
    } else if (!state.inProgress) {
      Log.d(TAG, "Not in progress, navigating up.")
      if (state.allowedToRequestCode) {
        requestCode()
      } else if (!dialogVisible) {
        showErrorDialog(R.string.RegistrationActivity_unable_to_request_verification_code)
      }
    }
  }

  private fun requestCode() {
    val mode = if (ChangeNumberVerifyFragmentArgs.fromBundle(requireArguments()).smsListenerEnabled) RegistrationRepository.E164VerificationMode.SMS_WITH_LISTENER else RegistrationRepository.E164VerificationMode.SMS_WITHOUT_LISTENER
    viewModel.initiateChangeNumberSession(requireContext(), mode)
  }

  private fun handleRequestCodeResult(changeNumberOutcome: ChangeNumberOutcome) {
    Log.d(TAG, "Handling request code result: ${changeNumberOutcome.javaClass.name}")
    when (changeNumberOutcome) {
      is ChangeNumberOutcome.RecoveryPasswordWorked -> {
        Log.i(TAG, "Successfully changed number with recovery password.")
        changeNumberSuccess()
      }

      is ChangeNumberOutcome.ChangeNumberRequestOutcome -> {
        when (val castResult = changeNumberOutcome.result) {
          is VerificationCodeRequestResult.Success -> {
            Log.i(TAG, "Successfully requested SMS code.")
            findNavController().safeNavigate(ChangeNumberVerifyFragmentDirections.actionChangePhoneNumberVerifyFragmentToChangeNumberEnterCodeFragment())
          }

          is VerificationCodeRequestResult.ChallengeRequired -> {
            Log.i(TAG, "Unable to request sms code due to challenges required: ${castResult.challenges.joinToString { it.key }}")
          }

          is VerificationCodeRequestResult.RateLimited -> {
            Log.i(TAG, "Unable to request sms code due to rate limit")
            showErrorDialog(R.string.RegistrationActivity_rate_limited_to_service)
          }

          is VerificationCodeRequestResult.TokenNotAccepted -> {
            Log.i(TAG, "Token was not accepted.")
            showErrorDialog(R.string.RegistrationActivity_additional_verification_required)
          }

          else -> {
            Log.w(TAG, "Unable to request sms code", castResult.getCause())
            showErrorDialog(R.string.RegistrationActivity_unable_to_request_verification_code)
          }
        }
      }

      is ChangeNumberOutcome.VerificationCodeWorked -> {
        Log.i(TAG, "Successfully changed number with verification code.")
        changeNumberSuccess()
      }
    }
  }

  private fun handleChallenges(remainingChallenges: List<Challenge>) {
    Log.i(TAG, "Handling challenge(s): ${remainingChallenges.joinToString { it.key }}")
    when (remainingChallenges.first()) {
      Challenge.CAPTCHA -> {
        findNavController().safeNavigate(ChangeNumberVerifyFragmentDirections.actionChangePhoneNumberVerifyFragmentToCaptchaFragment())
      }

      Challenge.PUSH -> {
        viewModel.requestAndSubmitPushToken(requireContext())
      }
    }
  }

  private fun showErrorDialog(@StringRes message: Int) {
    if (dialogVisible) {
      Log.i(TAG, "Dialog already being shown, failed to display dialog with message ${getString(message)}")
      return
    }

    MaterialAlertDialogBuilder(requireContext()).apply {
      setMessage(message)
      setPositiveButton(android.R.string.ok) { _, _ ->
        findNavController().navigateUp()
        viewModel.resetLocalSessionState()
      }
      show()
      dialogVisible = true
    }
  }
}
