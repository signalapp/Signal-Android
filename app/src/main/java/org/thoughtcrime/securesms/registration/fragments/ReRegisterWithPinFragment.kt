package org.thoughtcrime.securesms.registration.fragments

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.PinRestoreEntryFragmentBinding
import org.thoughtcrime.securesms.lock.v2.KbsConstants
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.registration.VerifyResponseWithRegistrationLockProcessor
import org.thoughtcrime.securesms.registration.viewmodel.ReRegisterWithPinViewModel
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Using a recovery password or restored KBS token attempt to register in the skip flow.
 */
class ReRegisterWithPinFragment : LoggingFragment(R.layout.pin_restore_entry_fragment) {

  companion object {
    private val TAG = Log.tag(ReRegisterWithPinFragment::class.java)
  }

  private var _binding: PinRestoreEntryFragmentBinding? = null
  private val binding: PinRestoreEntryFragmentBinding
    get() = _binding!!

  private val registrationViewModel: RegistrationViewModel by activityViewModels()
  private val reRegisterViewModel: ReRegisterWithPinViewModel by viewModels()

  private val disposables = LifecycleDisposable()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    _binding = PinRestoreEntryFragmentBinding.bind(view)

    disposables.bindTo(viewLifecycleOwner.lifecycle)

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

    binding.pinRestorePinConfirm.setOnClickListener {
      handlePinEntry()
    }

    binding.pinRestoreKeyboardToggle.setOnClickListener {
      val keyboardType: PinKeyboardType = getPinEntryKeyboardType()
      updateKeyboard(keyboardType.other)
      binding.pinRestoreKeyboardToggle.setText(resolveKeyboardToggleText(keyboardType))
    }

    val keyboardType: PinKeyboardType = getPinEntryKeyboardType().other
    binding.pinRestoreKeyboardToggle.setText(resolveKeyboardToggleText(keyboardType))

    reRegisterViewModel.updateTokenData(registrationViewModel.keyBackupCurrentToken)

    disposables += reRegisterViewModel.triesRemaining.subscribe(this::updateTriesRemaining)
  }

  override fun onDestroyView() {
    _binding = null
    super.onDestroyView()
  }

  private fun handlePinEntry() {
    val pin: String? = binding.pinRestorePinInput.text?.toString()

    val trimmedLength = pin?.replace(" ", "")?.length ?: 0
    if (trimmedLength == 0) {
      Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show()
      enableAndFocusPinEntry()
      return
    }

    if (trimmedLength < BaseRegistrationLockFragment.MINIMUM_PIN_LENGTH) {
      Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, BaseRegistrationLockFragment.MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show()
      enableAndFocusPinEntry()
      return
    }

    disposables += registrationViewModel.verifyReRegisterWithPin(pin!!)
      .doOnSubscribe {
        ViewUtil.hideKeyboard(requireContext(), binding.pinRestorePinInput)
        binding.pinRestorePinInput.isEnabled = false
        binding.pinRestorePinConfirm.setSpinning()
      }
      .doAfterTerminate {
        binding.pinRestorePinInput.isEnabled = true
        binding.pinRestorePinConfirm.cancelSpinning()
      }
      .subscribe { processor ->
        if (processor.hasResult()) {
          Log.i(TAG, "Successfully re-registered via skip flow")
          findNavController().safeNavigate(R.id.action_reRegisterWithPinFragment_to_registrationCompletePlaceHolderFragment)
          return@subscribe
        }

        reRegisterViewModel.hasIncorrectGuess = true

        if (processor is VerifyResponseWithRegistrationLockProcessor && processor.wrongPin()) {
          reRegisterViewModel.updateTokenData(processor.tokenData)
          if (processor.tokenData != null) {
            registrationViewModel.setKeyBackupTokenData(processor.tokenData)
          }
          return@subscribe
        } else if (processor.isKbsLocked()) {
          Log.w(TAG, "Unable to continue skip flow, KBS is locked")
          onAccountLocked()
        } else if (processor.isIncorrectRegistrationRecoveryPassword()) {
          Log.w(TAG, "Registration recovery password was incorrect. Moving to SMS verification.")
          onSkipPinEntry()
        } else if (processor.isServerSentError()) {
          Log.i(TAG, "Error from server, not likely recoverable", processor.error)
          Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show()
        } else {
          Log.i(TAG, "Unexpected error occurred", processor.error)
          Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show()
        }
      }
  }

  private fun updateTriesRemaining(triesRemaining: Int) {
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

  @StringRes
  private fun resolveKeyboardToggleText(keyboard: PinKeyboardType): Int {
    return if (keyboard == PinKeyboardType.ALPHA_NUMERIC) {
      R.string.RegistrationLockFragment__enter_alphanumeric_pin
    } else {
      R.string.RegistrationLockFragment__enter_numeric_pin
    }
  }

  private fun onNeedHelpClicked() {
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_need_help_local else R.string.PinRestoreEntryFragment_your_pin_is_a_d_digit_code

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PinRestoreEntryFragment_need_help)
      .setMessage(getString(message, KbsConstants.MINIMUM_PIN_LENGTH))
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
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_skip_local else R.string.PinRestoreEntryFragment_if_you_cant_remember_your_pin

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PinRestoreEntryFragment_skip_pin_entry)
      .setMessage(message)
      .setPositiveButton(R.string.PinRestoreEntryFragment_skip) { _, _ -> onSkipPinEntry() }
      .setNegativeButton(R.string.PinRestoreEntryFragment_cancel, null)
      .show()
  }

  private fun onSkipPinEntry() {
    registrationViewModel.setUserSkippedReRegisterFlow(true)
    findNavController().safeNavigate(R.id.action_reRegisterWithPinFragment_to_enterPhoneNumberFragment)
  }
}
