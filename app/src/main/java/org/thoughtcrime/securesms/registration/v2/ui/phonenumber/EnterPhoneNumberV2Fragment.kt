/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.phonenumber

import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationEnterPhoneNumberV2Binding
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.util.CountryPrefix
import org.thoughtcrime.securesms.registration.v2.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.v2.ui.RegistrationV2State
import org.thoughtcrime.securesms.registration.v2.ui.RegistrationV2ViewModel
import org.thoughtcrime.securesms.registration.v2.ui.toE164
import org.thoughtcrime.securesms.util.PlayServicesUtil
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.livedata.LiveDataObserverCallback
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.visible

/**
 * Screen in registration where the user enters their phone number.
 */
class EnterPhoneNumberV2Fragment : LoggingFragment(R.layout.fragment_registration_enter_phone_number_v2) {

  private val TAG = Log.tag(EnterPhoneNumberV2Fragment::class.java)
  private val sharedViewModel by activityViewModels<RegistrationV2ViewModel>()
  private val fragmentViewModel by viewModels<EnterPhoneNumberV2ViewModel>()
  private val binding: FragmentRegistrationEnterPhoneNumberV2Binding by ViewBinderDelegate(FragmentRegistrationEnterPhoneNumberV2Binding::bind)

  private lateinit var spinnerAdapter: ArrayAdapter<CountryPrefix>
  private lateinit var phoneNumberInputLayout: TextInputEditText
  private lateinit var spinnerView: MaterialAutoCompleteTextView

  private var currentPhoneNumberFormatter: TextWatcher? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setDebugLogSubmitMultiTapView(binding.verifyHeader)
    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          popBackStack()
        }
      }
    )
    phoneNumberInputLayout = binding.number.editText as TextInputEditText
    spinnerView = binding.countryCode.editText as MaterialAutoCompleteTextView
    spinnerAdapter = ArrayAdapter<CountryPrefix>(
      requireContext(),
      R.layout.registration_country_code_dropdown_item,
      fragmentViewModel.supportedCountryPrefixes
    )
    binding.registerButton.setOnClickListener { onRegistrationButtonClicked() }

    binding.toolbar.title = ""
    val activity = requireActivity() as AppCompatActivity
    activity.setSupportActionBar(binding.toolbar)

    requireActivity().addMenuProvider(UseProxyMenuProvider(), viewLifecycleOwner)

    sharedViewModel.uiState.observe(viewLifecycleOwner) { sharedState ->
      presentRegisterButton(sharedState)
      presentProgressBar(sharedState.inProgress, sharedState.isReRegister)
      if (sharedState.registrationCheckpoint >= RegistrationCheckpoint.PHONE_NUMBER_CONFIRMED && sharedState.canSkipSms) {
        moveToEnterPinScreen()
      } else if (sharedState.registrationCheckpoint >= RegistrationCheckpoint.VERIFICATION_CODE_REQUESTED) {
        moveToVerificationEntryScreen()
      }
    }

    fragmentViewModel.uiState.observe(viewLifecycleOwner) { fragmentState ->

      fragmentState.phoneNumberFormatter?.let {
        if (it != currentPhoneNumberFormatter) {
          currentPhoneNumberFormatter?.let { oldWatcher ->
            Log.d(TAG, "Removing current phone number formatter in fragment")
            phoneNumberInputLayout.removeTextChangedListener(oldWatcher)
          }
          phoneNumberInputLayout.addTextChangedListener(it)
          currentPhoneNumberFormatter = it
          Log.d(TAG, "Updating phone number formatter in fragment")
        }
      }

      if (fragmentViewModel.isEnteredNumberValid(fragmentState)) {
        sharedViewModel.setPhoneNumber(fragmentViewModel.parsePhoneNumber(fragmentState))
      } else {
        sharedViewModel.setPhoneNumber(null)
      }

      if (fragmentState.error != EnterPhoneNumberV2State.Error.NONE) {
        presentError(fragmentState)
      }
    }

    initializeInputFields()

    val existingPhoneNumber = sharedViewModel.uiState.value?.phoneNumber
    if (existingPhoneNumber != null) {
      fragmentViewModel.restoreState(existingPhoneNumber)
      fragmentViewModel.phoneNumber()?.let {
        phoneNumberInputLayout.setText(it.nationalNumber.toString())
      }
    } else if (spinnerView.editableText.isBlank()) {
      spinnerView.setText(fragmentViewModel.countryPrefix().toString())
    }

    ViewUtil.focusAndShowKeyboard(phoneNumberInputLayout)
  }

  private fun initializeInputFields() {
    binding.countryCode.editText?.addTextChangedListener { s ->
      val countryCode: Int = s.toString().toInt()
      fragmentViewModel.setCountry(countryCode)
    }

    phoneNumberInputLayout.addTextChangedListener {
      fragmentViewModel.setPhoneNumber(it?.toString())
    }
    phoneNumberInputLayout.onFocusChangeListener = View.OnFocusChangeListener { _: View?, hasFocus: Boolean ->
      if (hasFocus) {
        binding.scrollView.postDelayed({ binding.scrollView.smoothScrollTo(0, binding.registerButton.bottom) }, 250)
      }
    }
    phoneNumberInputLayout.imeOptions = EditorInfo.IME_ACTION_DONE
    phoneNumberInputLayout.setOnEditorActionListener { v: TextView?, actionId: Int, _: KeyEvent? ->
      if (actionId == EditorInfo.IME_ACTION_DONE && v != null) {
        onRegistrationButtonClicked()
        return@setOnEditorActionListener true
      }
      false
    }

    spinnerView.threshold = 100
    spinnerView.setAdapter(spinnerAdapter)
    spinnerView.addTextChangedListener { s ->
      if (s.isNullOrEmpty()) {
        return@addTextChangedListener
      }

      if (s[0] != '+') {
        s.insert(0, "+")
      }

      fragmentViewModel.supportedCountryPrefixes.firstOrNull { it.toString() == s.toString() }?.let {
        fragmentViewModel.setCountry(it.digits)
        val numberLength: Int = phoneNumberInputLayout.text?.length ?: 0
        phoneNumberInputLayout.setSelection(numberLength, numberLength)
      }
    }
  }

  private fun presentRegisterButton(sharedState: RegistrationV2State) {
    binding.registerButton.isEnabled = sharedState.phoneNumber != null && PhoneNumberUtil.getInstance().isValidNumber(sharedState.phoneNumber) && !sharedState.inProgress
    // TODO [regv2]: always enable the button but display error dialogs if the entered phone number is invalid
  }

  private fun presentError(state: EnterPhoneNumberV2State) {
    when (state.error) {
      EnterPhoneNumberV2State.Error.NONE -> {
        Unit
      }

      EnterPhoneNumberV2State.Error.INVALID_PHONE_NUMBER -> {
        MaterialAlertDialogBuilder(requireContext()).apply {
          setTitle(getString(R.string.RegistrationActivity_invalid_number))
          setMessage(
            String.format(
              getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid),
              state.phoneNumber
            )
          )
          setPositiveButton(android.R.string.ok) { _, _ -> fragmentViewModel.clearError() }
          setOnCancelListener { fragmentViewModel.clearError() }
          setOnDismissListener { fragmentViewModel.clearError() }
          show()
        }
      }

      EnterPhoneNumberV2State.Error.PLAY_SERVICES_MISSING -> {
        Log.w(TAG, "Not yet implemented!", NotImplementedError()) // TODO [regv2]
      }

      EnterPhoneNumberV2State.Error.PLAY_SERVICES_NEEDS_UPDATE -> {
        GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, 0)?.show()
      }

      EnterPhoneNumberV2State.Error.PLAY_SERVICES_TRANSIENT -> {
        Log.w(TAG, "Not yet implemented!", NotImplementedError()) // TODO [regv2]
      }
    }
  }

  private fun onRegistrationButtonClicked() {
    ViewUtil.hideKeyboard(requireContext(), phoneNumberInputLayout)
    sharedViewModel.setInProgress(true)
    val hasFcm = validateFcmStatus(requireContext())
    if (hasFcm) {
      sharedViewModel.uiState.observe(viewLifecycleOwner, FcmTokenRetrievedObserver())
      sharedViewModel.fetchFcmToken(requireContext())
    } else {
      sharedViewModel.setInProgress(false)
      // TODO [regv2]: handle if FCM isn't available
    }
  }

  private fun onFcmTokenRetrieved(value: RegistrationV2State) {
    if (value.phoneNumber == null) {
      fragmentViewModel.setError(EnterPhoneNumberV2State.Error.INVALID_PHONE_NUMBER)
      sharedViewModel.setInProgress(false)
    } else {
      presentConfirmNumberDialog(value.phoneNumber, value.isReRegister, value.canSkipSms)
    }
  }

  private fun presentProgressBar(showProgress: Boolean, isReRegister: Boolean) {
    if (showProgress) {
      binding.registerButton.setSpinning()
    } else {
      binding.registerButton.cancelSpinning()
    }
    binding.countryCode.isEnabled = !showProgress
    binding.number.isEnabled = !showProgress
    binding.cancelButton.visible = !showProgress && isReRegister
  }

  private fun validateFcmStatus(context: Context): Boolean {
    val fcmStatus = PlayServicesUtil.getPlayServicesStatus(context)
    Log.d(TAG, "Got $fcmStatus for Play Services status.")
    when (fcmStatus) {
      PlayServicesUtil.PlayServicesStatus.SUCCESS -> {
        return true
      }

      PlayServicesUtil.PlayServicesStatus.MISSING -> {
        fragmentViewModel.setError(EnterPhoneNumberV2State.Error.PLAY_SERVICES_MISSING)
        return false
      }

      PlayServicesUtil.PlayServicesStatus.NEEDS_UPDATE -> {
        fragmentViewModel.setError(EnterPhoneNumberV2State.Error.PLAY_SERVICES_NEEDS_UPDATE)
        return false
      }

      PlayServicesUtil.PlayServicesStatus.TRANSIENT_ERROR -> {
        fragmentViewModel.setError(EnterPhoneNumberV2State.Error.PLAY_SERVICES_TRANSIENT)
        return false
      }

      null -> {
        Log.w(TAG, "Null result received from PlayServicesUtil, marking Play Services as missing.")
        fragmentViewModel.setError(EnterPhoneNumberV2State.Error.PLAY_SERVICES_MISSING)
        return false
      }
    }
  }

  private fun onConfirmNumberDialogCanceled() {
    Log.d(TAG, "User canceled confirm number, returning to edit number.")
    sharedViewModel.setInProgress(false)
    ViewUtil.focusAndMoveCursorToEndAndOpenKeyboard(phoneNumberInputLayout)
  }

  private fun presentConfirmNumberDialog(phoneNumber: PhoneNumber, isReRegister: Boolean, canSkipSms: Boolean) {
    val title = if (isReRegister) {
      R.string.RegistrationActivity_additional_verification_required
    } else {
      R.string.RegistrationActivity_phone_number_verification_dialog_title
    }

    val message: CharSequence = SpannableStringBuilder().apply {
      append(SpanUtil.bold(PhoneNumberFormatter.prettyPrint(phoneNumber.toE164())))
      if (!canSkipSms) {
        append("\n\n")
        append(getString(R.string.RegistrationActivity_a_verification_code_will_be_sent_to_this_number))
      }
    }

    MaterialAlertDialogBuilder(requireContext()).apply {
      setTitle(title)
      setMessage(message)
      setPositiveButton(android.R.string.ok) { _, _ ->
        Log.d(TAG, "User confirmed number.")
        sharedViewModel.onUserConfirmedPhoneNumber(requireContext())
      }
      setNegativeButton(R.string.RegistrationActivity_edit_number) { _, _ -> onConfirmNumberDialogCanceled() }
      setOnCancelListener { _ -> onConfirmNumberDialogCanceled() }
    }.show()
  }

  private fun moveToEnterPinScreen() {
    sharedViewModel.setInProgress(false)
    findNavController().safeNavigate(EnterPhoneNumberV2FragmentDirections.actionReRegisterWithPinV2Fragment())
  }

  private fun moveToVerificationEntryScreen() {
    NavHostFragment.findNavController(this).safeNavigate(EnterPhoneNumberV2FragmentDirections.actionEnterVerificationCode())
    sharedViewModel.setInProgress(false)
  }

  private fun popBackStack() {
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.INITIALIZATION)
    NavHostFragment.findNavController(this).popBackStack()
  }

  private inner class FcmTokenRetrievedObserver : LiveDataObserverCallback<RegistrationV2State>(sharedViewModel.uiState) {
    override fun onValue(value: RegistrationV2State): Boolean {
      val fcmRetrieved = value.isFcmSupported
      if (fcmRetrieved) {
        onFcmTokenRetrieved(value)
      }
      return fcmRetrieved
    }
  }

  private inner class UseProxyMenuProvider : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
      menuInflater.inflate(R.menu.enter_phone_number, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
      return if (menuItem.itemId == R.id.phone_menu_use_proxy) {
        NavHostFragment.findNavController(this@EnterPhoneNumberV2Fragment).safeNavigate(EnterPhoneNumberV2FragmentDirections.actionEditProxy())
        true
      } else {
        false
      }
    }
  }
}
