/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.entercode

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.ThreadUtil
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.databinding.FragmentRegistrationEnterCodeBinding
import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCheckResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCreationResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.fragments.ContactSupportBottomSheetFragment
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.fragments.SignalStrengthPhoneStateListener
import org.thoughtcrime.securesms.registration.sms.ReceivedSmsEvent
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationViewModel
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.visible
import kotlin.time.Duration.Companion.milliseconds

/**
 * The final screen of account registration, where the user enters their verification code.
 */
class EnterCodeFragment : LoggingFragment(R.layout.fragment_registration_enter_code) {

  companion object {
    private val TAG = Log.tag(EnterCodeFragment::class.java)

    private const val BOTTOM_SHEET_TAG = "support_bottom_sheet"
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val fragmentViewModel by viewModels<EnterCodeViewModel>()
  private val bottomSheet = ContactSupportBottomSheetFragment()
  private val binding: FragmentRegistrationEnterCodeBinding by ViewBinderDelegate(FragmentRegistrationEnterCodeBinding::bind)

  private lateinit var phoneStateListener: SignalStrengthPhoneStateListener

  private var autopilotCodeEntryActive = false

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setDebugLogSubmitMultiTapView(binding.verifyHeader)

    phoneStateListener = SignalStrengthPhoneStateListener(this, PhoneStateCallback())

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          popBackStack()
        }
      }
    )

    binding.wrongNumber.setOnClickListener {
      popBackStack()
    }

    binding.code.setOnCompleteListener {
      sharedViewModel.verifyCodeWithoutRegistrationLock(requireContext(), it)
    }

    binding.havingTroubleButton.setOnClickListener {
      bottomSheet.showSafely(childFragmentManager, BOTTOM_SHEET_TAG)
    }

    binding.callMeCountDown.apply {
      setTextResources(R.string.RegistrationActivity_call, R.string.RegistrationActivity_call_me_instead_available_in)
      setOnClickListener {
        sharedViewModel.requestVerificationCall(requireContext())
      }
    }

    binding.resendSmsCountDown.apply {
      setTextResources(R.string.RegistrationActivity_resend_code, R.string.RegistrationActivity_resend_sms_available_in)
      setOnClickListener {
        sharedViewModel.requestSmsCode(requireContext())
      }
    }

    binding.keyboard.setOnKeyPressListener { key ->
      if (!autopilotCodeEntryActive) {
        if (key >= 0) {
          binding.code.append(key)
        } else {
          binding.code.delete()
        }
      }
    }

    sharedViewModel.incorrectCodeAttempts.observe(viewLifecycleOwner) { attempts: Int ->
      if (attempts >= 3) {
        binding.havingTroubleButton.visible = true
      }
    }

    sharedViewModel.uiState.observe(viewLifecycleOwner) { sharedState ->
      sharedState.sessionCreationError?.let { error ->
        handleSessionCreationError(error)
        sharedViewModel.sessionCreationErrorShown()
      }

      sharedState.sessionStateError?.let { error ->
        handleSessionErrorResponse(error)
        sharedViewModel.sessionStateErrorShown()
      }

      sharedState.registerAccountError?.let { error ->
        handleRegistrationErrorResponse(error)
        sharedViewModel.registerAccountErrorShown()
      }

      if (sharedState.challengesRequested.contains(Challenge.CAPTCHA) && sharedState.captchaToken.isNotNullOrBlank()) {
        sharedViewModel.submitCaptchaToken(requireContext())
      } else if (sharedState.challengesRemaining.isNotEmpty()) {
        handleChallenges(sharedState.challengesRemaining)
      }

      binding.resendSmsCountDown.startCountDownTo(sharedState.nextSmsTimestamp)
      binding.callMeCountDown.startCountDownTo(sharedState.nextCallTimestamp)
      if (sharedState.inProgress) {
        binding.keyboard.displayProgress()
      } else {
        binding.keyboard.displayKeyboard()
      }
    }

    fragmentViewModel.uiState.observe(viewLifecycleOwner) {
      if (it.resetRequiredAfterFailure) {
        binding.callMeCountDown.visibility = View.VISIBLE
        binding.resendSmsCountDown.visibility = View.VISIBLE
        binding.wrongNumber.visibility = View.VISIBLE
        binding.code.clear()
        binding.keyboard.displayKeyboard()
        fragmentViewModel.allViewsResetCompleted()
      } else if (it.showKeyboard) {
        binding.keyboard.displayKeyboard()
        fragmentViewModel.keyboardShown()
      }
    }

    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = viewLifecycleOwner)
  }

  override fun onResume() {
    super.onResume()
    sharedViewModel.phoneNumber?.let {
      val formatted = PhoneNumberUtil.getInstance().format(it, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
      binding.verificationSubheader.text = requireContext().getString(R.string.RegistrationActivity_enter_the_code_we_sent_to_s, formatted)
    }
  }

  private fun handleSessionCreationError(result: RegistrationSessionResult) {
    if (!result.isSuccess()) {
      Log.i(TAG, "[sessionCreateError] Handling error response of ${result.javaClass.name}", result.getCause())
    }
    when (result) {
      is RegistrationSessionCheckResult.Success,
      is RegistrationSessionCreationResult.Success -> throw IllegalStateException("Session error handler called on successful response!")
      is RegistrationSessionCreationResult.AttemptsExhausted -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_service))
      is RegistrationSessionCreationResult.MalformedRequest -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service))

      is RegistrationSessionCreationResult.RateLimited -> {
        val timeRemaining = result.timeRemaining?.milliseconds
        Log.i(TAG, "Session creation rate limited! Next attempt: $timeRemaining")
        if (timeRemaining != null) {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_try_again, timeRemaining.toString()))
        } else {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later))
        }
      }

      is RegistrationSessionCreationResult.ServerUnableToParse -> presentGenericError(result)
      is RegistrationSessionCheckResult.SessionNotFound -> presentGenericError(result)
      is RegistrationSessionCheckResult.UnknownError,
      is RegistrationSessionCreationResult.UnknownError -> presentGenericError(result)
    }
  }

  private fun handleSessionErrorResponse(result: VerificationCodeRequestResult) {
    if (!result.isSuccess()) {
      Log.i(TAG, "[sessionError] Handling error response of ${result.javaClass.name}", result.getCause())
    }

    when (result) {
      is VerificationCodeRequestResult.Success -> throw IllegalStateException("Session error handler called on successful response!")
      is VerificationCodeRequestResult.RateLimited -> {
        val timeRemaining = result.timeRemaining?.milliseconds
        Log.i(TAG, "Session patch rate limited! Next attempt: $timeRemaining")
        if (timeRemaining != null) {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_try_again, timeRemaining.toString()))
        } else {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later))
        }
      }
      is VerificationCodeRequestResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      is VerificationCodeRequestResult.ExternalServiceFailure -> presentSmsGenericError(result)
      is VerificationCodeRequestResult.RequestVerificationCodeRateLimited -> {
        Log.i(TAG, result.log())
        handleRequestVerificationCodeRateLimited(result)
      }
      is VerificationCodeRequestResult.SubmitVerificationCodeRateLimited -> presentSubmitVerificationCodeRateLimited()
      is VerificationCodeRequestResult.TokenNotAccepted -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_we_need_to_verify_that_youre_human)) { _, _ -> moveToCaptcha() }
      else -> presentGenericError(result)
    }
  }

  private fun handleRegistrationErrorResponse(result: RegisterAccountResult) {
    if (!result.isSuccess()) {
      Log.i(TAG, "[registrationError] Handling error response of ${result.javaClass.name}", result.getCause())
    }

    when (result) {
      is RegisterAccountResult.Success -> throw IllegalStateException("Register account error handler called on successful response!")
      is RegisterAccountResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      is RegisterAccountResult.AuthorizationFailed -> presentIncorrectCodeDialog()
      is RegisterAccountResult.AttemptsExhausted -> presentAccountLocked()
      is RegisterAccountResult.RateLimited -> presentRateLimitedDialog()

      else -> presentGenericError(result)
    }
  }

  private fun handleChallenges(remainingChallenges: List<Challenge>) {
    when (remainingChallenges.first()) {
      Challenge.CAPTCHA -> moveToCaptcha()
      Challenge.PUSH -> sharedViewModel.requestAndSubmitPushToken(requireContext())
    }
  }

  private fun presentAccountLocked() {
    binding.keyboard.displayLocked().addListener(
      object : AssertedSuccessListener<Boolean>() {
        override fun onSuccess(result: Boolean?) {
          findNavController().safeNavigate(EnterCodeFragmentDirections.actionAccountLocked())
        }
      }
    )
  }

  private fun presentRegistrationLocked(timeRemaining: Long) {
    binding.keyboard.displayLocked().addListener(
      object : AssertedSuccessListener<Boolean>() {
        override fun onSuccess(result: Boolean?) {
          findNavController().safeNavigate(EnterCodeFragmentDirections.actionRequireKbsLockPin(timeRemaining))
          sharedViewModel.setInProgress(false)
        }
      }
    )
  }

  private fun presentRateLimitedDialog() {
    binding.keyboard.displayFailure().addListener(
      object : AssertedSuccessListener<Boolean?>() {
        override fun onSuccess(result: Boolean?) {
          MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(R.string.RegistrationActivity_too_many_attempts)
            setMessage(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
            setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
              fragmentViewModel.resetAllViews()
            }
            show()
          }
        }
      }
    )
  }

  private fun presentIncorrectCodeDialog() {
    sharedViewModel.incrementIncorrectCodeAttempts()

    Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_code, Toast.LENGTH_LONG).show()

    binding.keyboard.displayFailure().addListener(object : AssertedSuccessListener<Boolean?>() {
      override fun onSuccess(result: Boolean?) {
        fragmentViewModel.resetAllViews()
      }
    })
  }

  private fun presentSmsGenericError(requestResult: RegistrationResult) {
    binding.keyboard.displayFailure().addListener(
      object : AssertedSuccessListener<Boolean>() {
        override fun onSuccess(result: Boolean?) {
          Log.w(TAG, "Encountered sms provider error!", requestResult.getCause())
          MaterialAlertDialogBuilder(requireContext()).apply {
            setMessage(R.string.RegistrationActivity_sms_provider_error)
            setPositiveButton(android.R.string.ok) { _, _ -> fragmentViewModel.showKeyboard() }
            show()
          }
        }
      }
    )
  }

  private fun presentRemoteErrorDialog(message: String, positiveButtonListener: DialogInterface.OnClickListener? = null) {
    MaterialAlertDialogBuilder(requireContext()).apply {
      setMessage(message)
      setPositiveButton(android.R.string.ok, positiveButtonListener)
      show()
    }
  }

  private fun presentGenericError(requestResult: RegistrationResult) {
    binding.keyboard.displayFailure().addListener(
      object : AssertedSuccessListener<Boolean>() {
        override fun onSuccess(result: Boolean?) {
          Log.w(TAG, "Encountered unexpected error!", requestResult.getCause())
          MaterialAlertDialogBuilder(requireContext()).apply {
            setMessage(R.string.RegistrationActivity_error_connecting_to_service)
            setPositiveButton(android.R.string.ok) { _, _ -> fragmentViewModel.showKeyboard() }
            show()
          }
        }
      }
    )
  }

  private fun handleRequestVerificationCodeRateLimited(result: VerificationCodeRequestResult.RequestVerificationCodeRateLimited) {
    if (result.willBeAbleToRequestAgain) {
      Log.i(TAG, "Attempted to request new code too soon, timers should be updated")
    } else {
      Log.w(TAG, "Request for new verification code impossible, need to restart registration")
      MaterialAlertDialogBuilder(requireContext()).apply {
        setMessage(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
        setPositiveButton(android.R.string.ok) { _, _ -> popBackStack() }
        setCancelable(false)
        show()
      }
    }
  }

  private fun presentSubmitVerificationCodeRateLimited() {
    binding.keyboard.displayFailure().addListener(
      object : AssertedSuccessListener<Boolean>() {
        override fun onSuccess(result: Boolean?) {
          Log.w(TAG, "Submit verification code impossible, need to request a new code and restart registration")
          MaterialAlertDialogBuilder(requireContext()).apply {
            setMessage(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
            setPositiveButton(android.R.string.ok) { _, _ -> popBackStack() }
            setCancelable(false)
            show()
          }
        }
      }
    )
  }

  private fun popBackStack() {
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PUSH_NETWORK_AUDITED)
    NavHostFragment.findNavController(this).popBackStack()
    sharedViewModel.setInProgress(false)
  }

  private fun moveToCaptcha() {
    findNavController().safeNavigate(EnterCodeFragmentDirections.actionRequestCaptcha())
    ThreadUtil.postToMain { sharedViewModel.setInProgress(false) }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onVerificationCodeReceived(event: ReceivedSmsEvent) {
    Log.i(TAG, "Received verification code via EventBus.")
    binding.code.clear()

    if (event.code.isBlank() || event.code.length != ReceivedSmsEvent.CODE_LENGTH) {
      Log.i(TAG, "Received invalid code of length ${event.code.length}. Ignoring.")
      return
    }

    val finalIndex = ReceivedSmsEvent.CODE_LENGTH - 1
    autopilotCodeEntryActive = true
    try {
      event.code
        .map { it.digitToInt() }
        .forEachIndexed { i, digit ->
          binding.code.postDelayed({
            binding.code.append(digit)
            if (i == finalIndex) {
              autopilotCodeEntryActive = false
            }
          }, i * 200L)
        }
      Log.i(TAG, "Finished auto-filling code.")
    } catch (notADigit: IllegalArgumentException) {
      Log.w(TAG, "Failed to convert code into digits.", notADigit)
      autopilotCodeEntryActive = false
    }
  }

  private inner class PhoneStateCallback : SignalStrengthPhoneStateListener.Callback {
    override fun onNoCellSignalPresent() {
      if (isAdded) {
        bottomSheet.showSafely(childFragmentManager, BOTTOM_SHEET_TAG)
      }
    }

    override fun onCellSignalPresent() {
      if (bottomSheet.isResumed) {
        bottomSheet.dismiss()
      }
    }
  }
}
