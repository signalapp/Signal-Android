/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.entercode

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationEnterCodeBinding
import org.thoughtcrime.securesms.registration.ReceivedSmsEvent
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.fragments.ContactSupportBottomSheetFragment
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.fragments.SignalStrengthPhoneStateListener
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.visible

/**
 * The final screen of account registration, where the user enters their verification code.
 */
class EnterCodeFragment : LoggingFragment(R.layout.fragment_registration_enter_code) {

  companion object {
    private const val BOTTOM_SHEET_TAG = "support_bottom_sheet"
  }

  private val TAG = Log.tag(EnterCodeFragment::class.java)

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
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

    sharedViewModel.uiState.observe(viewLifecycleOwner) {
      it.sessionStateError?.let { error ->
        handleSessionErrorResponse(error)
        sharedViewModel.sessionStateErrorShown()
      }

      it.registerAccountError?.let { error ->
        handleRegistrationErrorResponse(error)
        sharedViewModel.registerAccountErrorShown()
      }

      binding.resendSmsCountDown.startCountDownTo(it.nextSmsTimestamp)
      binding.callMeCountDown.startCountDownTo(it.nextCallTimestamp)
      if (it.inProgress) {
        binding.keyboard.displayProgress()
      } else {
        binding.keyboard.displayKeyboard()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    sharedViewModel.phoneNumber?.let {
      val formatted = PhoneNumberUtil.getInstance().format(it, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
      binding.verificationSubheader.text = requireContext().getString(R.string.RegistrationActivity_enter_the_code_we_sent_to_s, formatted)
    }
  }

  private fun handleSessionErrorResponse(result: VerificationCodeRequestResult) {
    when (result) {
      is VerificationCodeRequestResult.Success -> throw IllegalStateException("Session error handler called on successful response!")
      is VerificationCodeRequestResult.RateLimited -> presentRateLimitedDialog()
      is VerificationCodeRequestResult.AttemptsExhausted -> presentAccountLocked()
      is VerificationCodeRequestResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      else -> presentGenericError(result)
    }
  }

  private fun handleRegistrationErrorResponse(result: RegisterAccountResult) {
    when (result) {
      is RegisterAccountResult.Success -> throw IllegalStateException("Register account error handler called on successful response!")
      is RegisterAccountResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      is RegisterAccountResult.AuthorizationFailed -> presentIncorrectCodeDialog()
      is RegisterAccountResult.AttemptsExhausted -> presentAccountLocked()
      is RegisterAccountResult.RateLimited -> presentRateLimitedDialog()

      else -> presentGenericError(result)
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

  private fun presentIncorrectCodeDialog() {
    sharedViewModel.incrementIncorrectCodeAttempts()

    Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_code, Toast.LENGTH_LONG).show()

    binding.keyboard.displayFailure().addListener(object : AssertedSuccessListener<Boolean?>() {
      override fun onSuccess(result: Boolean?) {
        binding.callMeCountDown.visibility = View.VISIBLE
        binding.resendSmsCountDown.visibility = View.VISIBLE
        binding.wrongNumber.visibility = View.VISIBLE
        binding.code.clear()
        binding.keyboard.displayKeyboard()
      }
    })
  }

  private fun presentGenericError(requestResult: RegistrationResult) {
    binding.keyboard.displayFailure().addListener(
      object : AssertedSuccessListener<Boolean>() {
        override fun onSuccess(result: Boolean?) {
          Log.w(TAG, "Encountered unexpected error!", requestResult.getCause())
          MaterialAlertDialogBuilder(requireContext()).apply {
            null?.let<String, MaterialAlertDialogBuilder> {
              setTitle(it)
            }
            setMessage(getString(R.string.RegistrationActivity_error_connecting_to_service))
            setPositiveButton(android.R.string.ok) { _, _ -> binding.keyboard.displayKeyboard() }
            show()
          }
        }
      }
    )
  }

  private fun popBackStack() {
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PUSH_NETWORK_AUDITED)
    NavHostFragment.findNavController(this).popBackStack()
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
      bottomSheet.showSafely(childFragmentManager, BOTTOM_SHEET_TAG)
    }

    override fun onCellSignalPresent() {
      if (bottomSheet.isResumed) {
        bottomSheet.dismiss()
      }
    }
  }
}
