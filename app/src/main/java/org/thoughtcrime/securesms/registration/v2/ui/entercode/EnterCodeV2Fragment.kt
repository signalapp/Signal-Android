/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.entercode

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationEnterCodeV2Binding
import org.thoughtcrime.securesms.registration.fragments.ContactSupportBottomSheetFragment
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.fragments.SignalStrengthPhoneStateListener
import org.thoughtcrime.securesms.registration.v2.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.v2.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.v2.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.v2.ui.RegistrationV2ViewModel
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * The final screen of account registration, where the user enters their verification code.
 */
class EnterCodeV2Fragment : LoggingFragment(R.layout.fragment_registration_enter_code_v2) {

  companion object {
    private const val BOTTOM_SHEET_TAG = "support_bottom_sheet"
  }

  private val TAG = Log.tag(EnterCodeV2Fragment::class.java)

  private val sharedViewModel by activityViewModels<RegistrationV2ViewModel>()
  private val binding: FragmentRegistrationEnterCodeV2Binding by ViewBinderDelegate(FragmentRegistrationEnterCodeV2Binding::bind)

  private lateinit var phoneStateListener: SignalStrengthPhoneStateListener

  private var autopilotCodeEntryActive = false

  private val bottomSheet = ContactSupportBottomSheetFragment()

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
      sharedViewModel.verifyCodeWithoutRegistrationLock(requireContext(), it, ::handleSessionErrorResponse, ::handleRegistrationErrorResponse)
    }

    binding.havingTroubleButton.setOnClickListener {
      bottomSheet.show(childFragmentManager, BOTTOM_SHEET_TAG)
    }

    binding.callMeCountDown.apply {
      setTextResources(R.string.RegistrationActivity_call, R.string.RegistrationActivity_call_me_instead_available_in)
      setOnClickListener {
        sharedViewModel.requestVerificationCall(requireContext(), ::handleSessionErrorResponse)
      }
    }

    binding.resendSmsCountDown.apply {
      setTextResources(R.string.RegistrationActivity_resend_code, R.string.RegistrationActivity_resend_sms_available_in)
      setOnClickListener {
        sharedViewModel.requestSmsCode(requireContext(), ::handleSessionErrorResponse)
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

    sharedViewModel.uiState.observe(viewLifecycleOwner) {
      binding.resendSmsCountDown.startCountDownTo(it.nextSms)
      binding.callMeCountDown.startCountDownTo(it.nextCall)
      if (it.inProgress) {
        binding.keyboard.displayProgress()
      } else {
        binding.keyboard.displayKeyboard()
      }
    }
  }

  private fun handleSessionErrorResponse(requestResult: VerificationCodeRequestResult, mode: RegistrationRepository.Mode) {
    when (requestResult) {
      is VerificationCodeRequestResult.Success -> binding.keyboard.displaySuccess()
      is VerificationCodeRequestResult.RateLimited -> {
        binding.keyboard.displayFailure().addListener(
          object : AssertedSuccessListener<Boolean>() {
            override fun onSuccess(result: Boolean?) {
              presentRemoteErrorDialog(getString(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)) { _, _ ->
                binding.code.clear()
              }
            }
          }
        )
      }

      is VerificationCodeRequestResult.RegistrationLocked -> {
        binding.keyboard.displayLocked().addListener(
          object : AssertedSuccessListener<Boolean>() {
            override fun onSuccess(result: Boolean?) {
              findNavController().safeNavigate(EnterCodeV2FragmentDirections.actionRequireKbsLockPin(requestResult.timeRemaining))
            }
          }
        )
      }

      else -> {
        binding.keyboard.displayFailure().addListener(
          object : AssertedSuccessListener<Boolean>() {
            override fun onSuccess(result: Boolean?) {
              Log.w(TAG, "Encountered unexpected error!", requestResult.getCause())
              presentRemoteErrorDialog(getString(R.string.RegistrationActivity_error_connecting_to_service))
            }
          }
        )
      }
    }
  }

  private fun handleRegistrationErrorResponse(result: RegisterAccountResult) {
    when (result) {
      is RegisterAccountResult.Success -> Log.d(TAG, "Register account was successful.")
      is RegisterAccountResult.AuthorizationFailed -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_error_connecting_to_service))
      is RegisterAccountResult.MalformedRequest -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_error_connecting_to_service))
      is RegisterAccountResult.RegistrationLocked -> {
        Log.w(TAG, "Account is registration locked, cannot register.")
        findNavController().safeNavigate(EnterCodeV2FragmentDirections.actionRequireKbsLockPin(result.timeRemaining))
      }
      is RegisterAccountResult.UnknownError -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_error_connecting_to_service))
      is RegisterAccountResult.ValidationError -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_error_connecting_to_service))
      is RegisterAccountResult.IncorrectRecoveryPassword -> {
        Log.w(TAG, "User somehow got recovery password error while entering code. This is very suspicious!")
        sharedViewModel.setUserSkippedReRegisterFlow(true)
        popBackStack()
      }

      is RegisterAccountResult.AttemptsExhausted,
      is RegisterAccountResult.RateLimited -> presentRateLimitedDialog()
    }
  }

  private fun presentRateLimitedDialog() {
    binding.keyboard.displayFailure().addListener(
      object : AssertedSuccessListener<Boolean?>() {
        override fun onSuccess(result: Boolean?) {
          MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(R.string.RegistrationActivity_too_many_attempts)
            setMessage(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
            setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
              binding.callMeCountDown.visibility = View.VISIBLE
              binding.resendSmsCountDown.visibility = View.VISIBLE
              binding.wrongNumber.visibility = View.VISIBLE
              binding.code.clear()
              binding.keyboard.displayKeyboard()
            }
            show()
          }
        }
      }
    )
  }

  private fun presentRemoteErrorDialog(message: String, title: String? = null, positiveButtonListener: DialogInterface.OnClickListener? = null) {
    MaterialAlertDialogBuilder(requireContext()).apply {
      title?.let {
        setTitle(it)
      }
      setMessage(message)
      setPositiveButton(android.R.string.ok, positiveButtonListener)
      show()
    }
  }

  private fun popBackStack() {
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PUSH_NETWORK_AUDITED)
    NavHostFragment.findNavController(this).popBackStack()
  }

  private inner class PhoneStateCallback : SignalStrengthPhoneStateListener.Callback {
    override fun onNoCellSignalPresent() {
      bottomSheet.show(childFragmentManager, BOTTOM_SHEET_TAG)
    }

    override fun onCellSignalPresent() {
      if (bottomSheet.isResumed) {
        bottomSheet.dismiss()
      }
    }
  }
}
