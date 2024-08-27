/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.reregisterwithpin

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationPinRestoreEntryV2Binding
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.lock.v2.SvrConstants
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.ui.RegistrationState
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class ReRegisterWithPinFragment : LoggingFragment(R.layout.fragment_registration_pin_restore_entry_v2) {
  companion object {
    private val TAG = Log.tag(ReRegisterWithPinFragment::class.java)
  }

  private val registrationViewModel by activityViewModels<RegistrationViewModel>()
  private val reRegisterViewModel by viewModels<ReRegisterWithPinViewModel>()

  private val binding: FragmentRegistrationPinRestoreEntryV2Binding by ViewBinderDelegate(FragmentRegistrationPinRestoreEntryV2Binding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    RegistrationViewDelegate.setDebugLogSubmitMultiTapView(binding.pinRestorePinTitle)
    binding.pinRestorePinDescription.setText(R.string.RegistrationLockFragment__enter_the_pin_you_created_for_your_account)

    binding.pinRestoreForgotPin.visibility = View.GONE
    binding.pinRestoreForgotPin.setOnClickListener { onNeedHelpClicked() }

    binding.pinRestoreSkipButton.setOnClickListener { onSkipClicked() }

    binding.pinRestorePinInput.imeOptions = EditorInfo.IME_ACTION_DONE
    binding.pinRestorePinInput.setOnEditorActionListener { v, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        ViewUtil.hideKeyboard(requireContext(), v!!)
        handlePinEntry()
        return@setOnEditorActionListener true
      }
      false
    }

    enableAndFocusPinEntry()

    binding.pinRestorePinContinue.setOnClickListener {
      handlePinEntry()
    }

    binding.pinRestoreKeyboardToggle.setOnClickListener {
      val currentKeyboardType: PinKeyboardType = getPinEntryKeyboardType()
      updateKeyboard(currentKeyboardType.other)
      binding.pinRestoreKeyboardToggle.setIconResource(currentKeyboardType.iconResource)
    }

    binding.pinRestoreKeyboardToggle.setIconResource(getPinEntryKeyboardType().other.iconResource)

    registrationViewModel.uiState.observe(viewLifecycleOwner, ::updateViewState)
  }

  private fun updateViewState(state: RegistrationState) {
    if (state.networkError != null) {
      genericErrorDialog()
      registrationViewModel.networkErrorShown()
    } else if (!state.canSkipSms) {
      findNavController().safeNavigate(ReRegisterWithPinFragmentDirections.actionReRegisterWithPinFragmentToEnterPhoneNumberFragment())
    } else if (state.isRegistrationLockEnabled && state.svrTriesRemaining == 0) {
      Log.w(TAG, "Unable to continue skip flow, KBS is locked")
      onAccountLocked()
    } else {
      presentProgress(state.inProgress)
      presentTriesRemaining(state.svrTriesRemaining)
    }

    state.registerAccountError?.let { error ->
      registrationErrorHandler(error)
      registrationViewModel.registerAccountErrorShown()
    }
  }

  private fun presentProgress(inProgress: Boolean) {
    if (inProgress) {
      ViewUtil.hideKeyboard(requireContext(), binding.pinRestorePinInput)
      binding.pinRestorePinInput.isEnabled = false
      binding.pinRestorePinContinue.setSpinning()
    } else {
      binding.pinRestorePinInput.isEnabled = true
      binding.pinRestorePinContinue.cancelSpinning()
    }
  }

  private fun handlePinEntry() {
    val pin: String? = binding.pinRestorePinInput.text?.toString()

    if (pin.isNullOrBlank()) {
      Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show()
      enableAndFocusPinEntry()
      return
    }

    if (pin.trim().length < SvrConstants.MINIMUM_PIN_LENGTH) {
      Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, SvrConstants.MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show()
      enableAndFocusPinEntry()
      return
    }

    registrationViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PIN_CONFIRMED)

    registrationViewModel.verifyReRegisterWithPin(
      context = requireContext(),
      pin = pin,
      wrongPinHandler = {
        reRegisterViewModel.markIncorrectGuess()
      }
    )
  }

  private fun presentTriesRemaining(triesRemaining: Int) {
    if (reRegisterViewModel.hasIncorrectGuess) {
      if (triesRemaining == 1 && !reRegisterViewModel.isLocalVerification) {
        MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.PinRestoreEntryFragment_incorrect_pin)
          .setMessage(resources.getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, triesRemaining, triesRemaining))
          .setPositiveButton(android.R.string.ok, null)
          .show()
      }

      if (triesRemaining > 5) {
        binding.pinRestorePinInputLabel.setText(R.string.PinRestoreEntryFragment_incorrect_pin)
      } else {
        binding.pinRestorePinInputLabel.text = resources.getQuantityString(R.plurals.RegistrationLockFragment__incorrect_pin_d_attempts_remaining, triesRemaining, triesRemaining)
      }
      binding.pinRestoreForgotPin.visibility = View.VISIBLE
    } else {
      if (triesRemaining == 1) {
        binding.pinRestoreForgotPin.visibility = View.VISIBLE
        if (!reRegisterViewModel.isLocalVerification) {
          MaterialAlertDialogBuilder(requireContext())
            .setMessage(resources.getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, triesRemaining, triesRemaining))
            .setPositiveButton(android.R.string.ok, null)
            .show()
        }
      }
    }

    if (triesRemaining == 0) {
      Log.w(TAG, "Account locked. User out of attempts on KBS.")
      onAccountLocked()
    }
  }

  private fun onAccountLocked() {
    Log.d(TAG, "Showing Incorrect PIN dialog. Is local verification: ${reRegisterViewModel.isLocalVerification}")
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_out_of_guesses_local else R.string.PinRestoreLockedFragment_youve_run_out_of_pin_guesses

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PinRestoreEntryFragment_incorrect_pin)
      .setMessage(message)
      .setCancelable(false)
      .setPositiveButton(R.string.ReRegisterWithPinFragment_send_sms_code) { _, _ -> onSkipPinEntry() }
      .setNegativeButton(R.string.AccountLockedFragment__learn_more) { _, _ -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.PinRestoreLockedFragment_learn_more_url)) }
      .show()
  }

  private fun enableAndFocusPinEntry() {
    binding.pinRestorePinInput.isEnabled = true
    binding.pinRestorePinInput.isFocusable = true
    ViewUtil.focusAndShowKeyboard(binding.pinRestorePinInput)
  }

  private fun getPinEntryKeyboardType(): PinKeyboardType {
    val isNumeric = binding.pinRestorePinInput.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER
    return if (isNumeric) PinKeyboardType.NUMERIC else PinKeyboardType.ALPHA_NUMERIC
  }

  private fun updateKeyboard(keyboard: PinKeyboardType) {
    val isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC
    binding.pinRestorePinInput.inputType = if (isAlphaNumeric) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    binding.pinRestorePinInput.text?.clear()
  }

  private fun onNeedHelpClicked() {
    Log.i(TAG, "User clicked need help dialog.")
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_need_help_local else R.string.PinRestoreEntryFragment_your_pin_is_a_d_digit_code

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PinRestoreEntryFragment_need_help)
      .setMessage(getString(message, SvrConstants.MINIMUM_PIN_LENGTH))
      .setPositiveButton(R.string.PinRestoreEntryFragment_skip) { _, _ -> onSkipPinEntry() }
      .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support) { _, _ ->
        val body = SupportEmailUtil.generateSupportEmailBody(requireContext(), R.string.ReRegisterWithPinFragment_support_email_subject, null, null)

        CommunicationActions.openEmail(
          requireContext(),
          SupportEmailUtil.getSupportEmailAddress(requireContext()),
          getString(R.string.ReRegisterWithPinFragment_support_email_subject),
          body
        )
      }
      .setNegativeButton(R.string.PinRestoreEntryFragment_cancel, null)
      .show()
  }

  private fun onSkipClicked() {
    Log.i(TAG, "User clicked the skip PIN button.")
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_skip_local else R.string.PinRestoreEntryFragment_if_you_cant_remember_your_pin

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PinRestoreEntryFragment_skip_pin_entry)
      .setMessage(message)
      .setPositiveButton(R.string.PinRestoreEntryFragment_skip) { _, _ -> onSkipPinEntry() }
      .setNegativeButton(R.string.PinRestoreEntryFragment_cancel, null)
      .show()
  }

  private fun onSkipPinEntry() {
    Log.d(TAG, "User skipping PIN entry.")
    registrationViewModel.setUserSkippedReRegisterFlow(true)
  }

  private fun presentRateLimitedDialog() {
    MaterialAlertDialogBuilder(requireContext()).apply {
      setTitle(R.string.RegistrationActivity_too_many_attempts)
      setMessage(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
      setPositiveButton(android.R.string.ok, null)
      show()
    }
  }

  private fun genericErrorDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setMessage(R.string.RegistrationActivity_error_connecting_to_service)
      .setPositiveButton(android.R.string.ok, null)
      .create()
      .show()
  }

  private fun registrationErrorHandler(result: RegisterAccountResult) {
    when (result) {
      is RegisterAccountResult.Success -> throw IllegalStateException("Register account error handler called on successful response!")
      is RegisterAccountResult.AuthorizationFailed,
      is RegisterAccountResult.MalformedRequest,
      is RegisterAccountResult.UnknownError,
      is RegisterAccountResult.ValidationError,
      is RegisterAccountResult.RegistrationLocked -> {
        Log.i(TAG, "Registration failed.", result.getCause())
        genericErrorDialog()
      }

      is RegisterAccountResult.IncorrectRecoveryPassword -> {
        registrationViewModel.setUserSkippedReRegisterFlow(true)
        findNavController().safeNavigate(ReRegisterWithPinFragmentDirections.actionReRegisterWithPinFragmentToEnterPhoneNumberFragment())
      }

      is RegisterAccountResult.AttemptsExhausted,
      is RegisterAccountResult.RateLimited -> presentRateLimitedDialog()

      is RegisterAccountResult.SvrNoData -> onAccountLocked()
      is RegisterAccountResult.SvrWrongPin -> {
        reRegisterViewModel.markIncorrectGuess()
        reRegisterViewModel.markAsRemoteVerification()
      }
    }
  }
}
