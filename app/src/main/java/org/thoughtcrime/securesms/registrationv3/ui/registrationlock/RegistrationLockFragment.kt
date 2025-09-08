/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.registrationlock

import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationLockBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.lock.v2.SvrConstants
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationViewModel
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.concurrent.TimeUnit

class RegistrationLockFragment : LoggingFragment(R.layout.fragment_registration_lock) {
  companion object {
    private val TAG = Log.tag(RegistrationLockFragment::class.java)
  }

  private val binding: FragmentRegistrationLockBinding by ViewBinderDelegate(FragmentRegistrationLockBinding::bind)

  private val viewModel by activityViewModels<RegistrationViewModel>()

  private var timeRemaining: Long = 0

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setDebugLogSubmitMultiTapView(view.findViewById(R.id.kbs_lock_pin_title))

    val args: RegistrationLockFragmentArgs = RegistrationLockFragmentArgs.fromBundle(requireArguments())

    timeRemaining = args.getTimeRemaining()

    binding.kbsLockForgotPin.visibility = View.GONE
    binding.kbsLockForgotPin.setOnClickListener { handleForgottenPin(timeRemaining) }

    binding.kbsLockPinInput.setImeOptions(EditorInfo.IME_ACTION_DONE)
    binding.kbsLockPinInput.setOnEditorActionListener { v: TextView?, actionId: Int, _: KeyEvent? ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        ViewUtil.hideKeyboard(requireContext(), v!!)
        handlePinEntry()
        return@setOnEditorActionListener true
      }
      false
    }

    enableAndFocusPinEntry()

    binding.kbsLockPinConfirm.setOnClickListener {
      ViewUtil.hideKeyboard(requireContext(), binding.kbsLockPinInput)
      handlePinEntry()
    }

    binding.kbsLockKeyboardToggle.setOnClickListener {
      val keyboardType: PinKeyboardType = getPinEntryKeyboardType()
      updateKeyboard(keyboardType.other)
      binding.kbsLockKeyboardToggle.setIconResource(keyboardType.iconResource)
    }

    val keyboardType: PinKeyboardType = getPinEntryKeyboardType().getOther()
    binding.kbsLockKeyboardToggle.setIconResource(keyboardType.iconResource)

    viewModel.lockedTimeRemaining.observe(viewLifecycleOwner) { t: Long -> timeRemaining = t }

    val triesRemaining: Int = viewModel.svrTriesRemaining

    if (triesRemaining <= 3) {
      val daysRemaining = getLockoutDays(timeRemaining)

      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.RegistrationLockFragment__not_many_tries_left)
        .setMessage(getTriesRemainingDialogMessage(triesRemaining, daysRemaining))
        .setPositiveButton(android.R.string.ok, null)
        .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support) { _, _ -> sendEmailToSupport() }
        .show()
    }

    if (triesRemaining < 5) {
      binding.kbsLockPinInputLabel.text = requireContext().resources.getQuantityString(R.plurals.RegistrationLockFragment__d_attempts_remaining, triesRemaining, triesRemaining)
    }

    viewModel.uiState.observe(viewLifecycleOwner) {
      if (it.inProgress) {
        binding.kbsLockPinConfirm.setSpinning()
      } else {
        binding.kbsLockPinConfirm.cancelSpinning()
      }

      it.sessionStateError?.let { error ->
        handleSessionErrorResponse(error)
        viewModel.sessionStateErrorShown()
      }

      it.registerAccountError?.let { error ->
        handleRegistrationErrorResponse(error)
        viewModel.registerAccountErrorShown()
      }
    }
  }

  private fun handlePinEntry() {
    binding.kbsLockPinInput.setEnabled(false)

    val pin: String = binding.kbsLockPinInput.getText().toString()

    val trimmedLength = pin.replace(" ", "").length
    if (trimmedLength == 0) {
      Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show()
      enableAndFocusPinEntry()
      return
    }

    if (trimmedLength < SvrConstants.MINIMUM_PIN_LENGTH) {
      Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, SvrConstants.MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show()
      enableAndFocusPinEntry()
      return
    }

    SignalStore.pin.keyboardType = getPinEntryKeyboardType()

    binding.kbsLockPinConfirm.setSpinning()

    viewModel.verifyCodeAndRegisterAccountWithRegistrationLock(requireContext(), pin)
  }

  private fun handleSessionErrorResponse(requestResult: VerificationCodeRequestResult) {
    when (requestResult) {
      is VerificationCodeRequestResult.Success -> throw IllegalStateException("Session error handler called on successful response!")
      is VerificationCodeRequestResult.RateLimited -> onRateLimited()

      is VerificationCodeRequestResult.RegistrationLocked -> {
        Log.i(TAG, "Registration locked response to verify account!")
        binding.kbsLockPinConfirm.cancelSpinning()
        enableAndFocusPinEntry()
        Toast.makeText(requireContext(), "Reg lock!", Toast.LENGTH_LONG).show()
      }

      else -> {
        Log.w(TAG, "Unable to verify code with registration lock", requestResult.getCause())
        onError()
      }
    }
  }

  private fun handleRegistrationErrorResponse(result: RegisterAccountResult) {
    when (result) {
      is RegisterAccountResult.Success -> throw IllegalStateException("Register account error handler called on successful response!")
      is RegisterAccountResult.RateLimited -> onRateLimited()
      is RegisterAccountResult.AttemptsExhausted -> {
        findNavController().safeNavigate(RegistrationLockFragmentDirections.actionAccountLocked())
      }

      is RegisterAccountResult.RegistrationLocked -> {
        Log.i(TAG, "Registration locked response to register account!")
        binding.kbsLockPinConfirm.cancelSpinning()
        enableAndFocusPinEntry()
        Toast.makeText(requireContext(), "Reg lock!", Toast.LENGTH_LONG).show()
      }

      is RegisterAccountResult.SvrWrongPin -> onIncorrectKbsRegistrationLockPin(result.triesRemaining)
      is RegisterAccountResult.SvrNoData -> {
        findNavController().safeNavigate(RegistrationLockFragmentDirections.actionAccountLocked())
      }

      else -> {
        Log.w(TAG, "Unable to register account with registration lock", result.getCause())
        onError()
      }
    }
  }

  private fun onIncorrectKbsRegistrationLockPin(svrTriesRemaining: Int) {
    binding.kbsLockPinConfirm.cancelSpinning()
    binding.kbsLockPinInput.getText().clear()
    enableAndFocusPinEntry()

    if (svrTriesRemaining == 0) {
      Log.w(TAG, "Account locked. User out of attempts on KBS.")
      findNavController().safeNavigate(RegistrationLockFragmentDirections.actionAccountLocked())
      return
    }

    if (svrTriesRemaining == 3) {
      val daysRemaining = getLockoutDays(timeRemaining)

      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.RegistrationLockFragment__incorrect_pin)
        .setMessage(getTriesRemainingDialogMessage(svrTriesRemaining, daysRemaining))
        .setPositiveButton(android.R.string.ok, null)
        .show()
    }

    if (svrTriesRemaining > 5) {
      binding.kbsLockPinInputLabel.setText(R.string.RegistrationLockFragment__incorrect_pin_try_again)
    } else {
      binding.kbsLockPinInputLabel.text = requireContext().resources.getQuantityString(R.plurals.RegistrationLockFragment__incorrect_pin_d_attempts_remaining, svrTriesRemaining, svrTriesRemaining)
      binding.kbsLockForgotPin.visibility = View.VISIBLE
    }
  }

  private fun onRateLimited() {
    binding.kbsLockPinConfirm.cancelSpinning()
    enableAndFocusPinEntry()

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.RegistrationActivity_too_many_attempts)
      .setMessage(R.string.RegistrationActivity_you_have_made_too_many_incorrect_registration_lock_pin_attempts_please_try_again_in_a_day)
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }

  fun onError() {
    binding.kbsLockPinConfirm.cancelSpinning()
    enableAndFocusPinEntry()

    Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show()
  }

  private fun handleForgottenPin(timeRemainingMs: Long) {
    val lockoutDays = getLockoutDays(timeRemainingMs)
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.RegistrationLockFragment__forgot_your_pin)
      .setMessage(requireContext().resources.getQuantityString(R.plurals.RegistrationLockFragment__for_your_privacy_and_security_there_is_no_way_to_recover, lockoutDays, lockoutDays))
      .setPositiveButton(android.R.string.ok, null)
      .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support) { _, _ -> sendEmailToSupport() }
      .show()
  }

  private fun getLockoutDays(timeRemainingMs: Long): Int {
    return TimeUnit.MILLISECONDS.toDays(timeRemainingMs).toInt() + 1
  }

  private fun getTriesRemainingDialogMessage(triesRemaining: Int, daysRemaining: Int): String {
    val resources = requireContext().resources
    val tries = resources.getQuantityString(R.plurals.RegistrationLockFragment__you_have_d_attempts_remaining, triesRemaining, triesRemaining)
    val days = resources.getQuantityString(R.plurals.RegistrationLockFragment__if_you_run_out_of_attempts_your_account_will_be_locked_for_d_days, daysRemaining, daysRemaining)

    return "$tries $days"
  }

  private fun enableAndFocusPinEntry() {
    binding.kbsLockPinInput.setEnabled(true)
    binding.kbsLockPinInput.setFocusable(true)
    binding.kbsLockPinInput.transformationMethod = PasswordTransformationMethod.getInstance()
    ViewUtil.focusAndShowKeyboard(binding.kbsLockPinInput)
  }

  private fun getPinEntryKeyboardType(): PinKeyboardType {
    val isNumeric = (binding.kbsLockPinInput.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER

    return if (isNumeric) PinKeyboardType.NUMERIC else PinKeyboardType.ALPHA_NUMERIC
  }

  private fun updateKeyboard(keyboard: PinKeyboardType) {
    val isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC

    binding.kbsLockPinInput.setInputType(
      if (isAlphaNumeric) InputType.TYPE_CLASS_TEXT
      else InputType.TYPE_CLASS_NUMBER
    )

    binding.kbsLockPinInput.getText().clear()
    binding.kbsLockPinInput.transformationMethod = PasswordTransformationMethod.getInstance()
  }

  private fun sendEmailToSupport() {
    val subject = R.string.RegistrationLockFragment__signal_registration_need_help_with_pin_for_android_v2_pin

    val body = SupportEmailUtil.generateSupportEmailBody(
      requireContext(),
      subject,
      null,
      null
    )
    CommunicationActions.openEmail(
      requireContext(),
      SupportEmailUtil.getSupportEmailAddress(requireContext()),
      getString(subject),
      body
    )
  }
}
