package org.thoughtcrime.securesms.registration.fragments

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.FragmentRegistrationLockBinding
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Using a recovery password or restored KBS token attempt to register in the skip flow.
 */
class ReRegisterWithPinFragment : LoggingFragment(R.layout.fragment_registration_lock) {

  companion object {
    private val TAG = Log.tag(ReRegisterWithPinFragment::class.java)
  }

  private var _binding: FragmentRegistrationLockBinding? = null
  private val binding: FragmentRegistrationLockBinding
    get() = _binding!!

  private val viewModel: RegistrationViewModel by activityViewModels()
  private val disposables = LifecycleDisposable()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    _binding = FragmentRegistrationLockBinding.bind(view)

    disposables.bindTo(viewLifecycleOwner.lifecycle)

    RegistrationViewDelegate.setDebugLogSubmitMultiTapView(binding.kbsLockPinTitle)

    binding.kbsLockForgotPin.visibility = View.GONE

    binding.kbsLockPinInput.imeOptions = EditorInfo.IME_ACTION_DONE
    binding.kbsLockPinInput.setOnEditorActionListener { v, actionId, _ ->
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

    binding.kbsLockKeyboardToggle.setOnClickListener { v: View? ->
      val keyboardType: PinKeyboardType = getPinEntryKeyboardType()
      updateKeyboard(keyboardType.other)
      binding.kbsLockKeyboardToggle.setText(resolveKeyboardToggleText(keyboardType))
    }

    val keyboardType: PinKeyboardType = getPinEntryKeyboardType().other
    binding.kbsLockKeyboardToggle.setText(resolveKeyboardToggleText(keyboardType))
  }

  private fun handlePinEntry() {
    binding.kbsLockPinInput.isEnabled = false

    val pin: String? = binding.kbsLockPinInput.text?.toString()

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

    binding.kbsLockPinConfirm.setSpinning()

    disposables += viewModel.verifyReRegisterWithPin(pin!!)
      .subscribe { p ->
        if (p.hasResult()) {
          Log.i(TAG, "Successfully re-registered via skip flow")
          findNavController().safeNavigate(R.id.action_reRegisterWithPinFragment_to_registrationCompletePlaceHolderFragment)
        } else {
          Log.w(TAG, "Unable to continue skip flow, resuming normal flow", p.error)
          // todo handle the various error conditions
          Toast.makeText(requireContext(), "retry or nav TODO ERROR See log", Toast.LENGTH_SHORT).show()
          binding.kbsLockPinInput.isEnabled = true
        }
      }
  }

  private fun enableAndFocusPinEntry() {
    binding.kbsLockPinInput.isEnabled = true
    binding.kbsLockPinInput.isFocusable = true
    if (binding.kbsLockPinInput.requestFocus()) {
      ServiceUtil.getInputMethodManager(binding.kbsLockPinInput.context).showSoftInput(binding.kbsLockPinInput, 0)
    }
  }

  private fun getPinEntryKeyboardType(): PinKeyboardType {
    val isNumeric = binding.kbsLockPinInput.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER
    return if (isNumeric) PinKeyboardType.NUMERIC else PinKeyboardType.ALPHA_NUMERIC
  }

  private fun updateKeyboard(keyboard: PinKeyboardType) {
    val isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC
    binding.kbsLockPinInput.inputType = if (isAlphaNumeric) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    binding.kbsLockPinInput.text.clear()
  }

  @StringRes
  private fun resolveKeyboardToggleText(keyboard: PinKeyboardType): Int {
    return if (keyboard == PinKeyboardType.ALPHA_NUMERIC) {
      R.string.RegistrationLockFragment__enter_alphanumeric_pin
    } else {
      R.string.RegistrationLockFragment__enter_numeric_pin
    }
  }
}
