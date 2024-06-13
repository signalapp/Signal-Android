/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.phonenumber

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
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
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationEnterPhoneNumberV2Binding
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.util.CountryPrefix
import org.thoughtcrime.securesms.registration.v2.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.v2.data.network.Challenge
import org.thoughtcrime.securesms.registration.v2.data.network.RegistrationResult
import org.thoughtcrime.securesms.registration.v2.data.network.RegistrationSessionCreationResult
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.v2.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.v2.ui.RegistrationV2State
import org.thoughtcrime.securesms.registration.v2.ui.RegistrationV2ViewModel
import org.thoughtcrime.securesms.registration.v2.ui.toE164
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.Dialogs
import org.thoughtcrime.securesms.util.PlayServicesUtil
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.livedata.LiveDataObserverCallback
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.visible
import kotlin.time.Duration.Companion.milliseconds

/**
 * Screen in registration where the user enters their phone number.
 */
class EnterPhoneNumberV2Fragment : LoggingFragment(R.layout.fragment_registration_enter_phone_number_v2) {

  private val TAG = Log.tag(EnterPhoneNumberV2Fragment::class.java)
  private val sharedViewModel by activityViewModels<RegistrationV2ViewModel>()
  private val fragmentViewModel by viewModels<EnterPhoneNumberV2ViewModel>()
  private val binding: FragmentRegistrationEnterPhoneNumberV2Binding by ViewBinderDelegate(FragmentRegistrationEnterPhoneNumberV2Binding::bind)

  private val skipToNextScreen: DialogInterface.OnClickListener = DialogInterface.OnClickListener { _: DialogInterface?, _: Int -> moveToVerificationEntryScreen() }

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

      sharedState.networkError?.let {
        presentNetworkError(it)
      }

      if (sharedState.challengesRequested.contains(Challenge.CAPTCHA) && sharedState.captchaToken.isNotNullOrBlank()) {
        sharedViewModel.submitCaptchaToken(requireContext(), ::handleErrorResponse)
      } else if (sharedState.challengesRemaining.isNotEmpty()) {
        handleChallenges(sharedState.challengesRemaining)
      } else if (sharedState.registrationCheckpoint >= RegistrationCheckpoint.PHONE_NUMBER_CONFIRMED && sharedState.canSkipSms) {
        moveToEnterPinScreen()
      } else if (sharedState.registrationCheckpoint >= RegistrationCheckpoint.VERIFICATION_CODE_REQUESTED) {
        moveToVerificationEntryScreen()
      }
    }

    fragmentViewModel.uiState.observe(viewLifecycleOwner) { fragmentState ->

      fragmentState.phoneNumberFormatter?.let {
        bindPhoneNumberFormatter(it)
      }

      if (fragmentViewModel.isEnteredNumberValid(fragmentState)) {
        sharedViewModel.setPhoneNumber(fragmentViewModel.parsePhoneNumber(fragmentState))
      } else {
        sharedViewModel.setPhoneNumber(null)
      }

      if (fragmentState.error != EnterPhoneNumberV2State.Error.NONE) {
        presentLocalError(fragmentState)
      }
    }

    initializeInputFields()

    val existingPhoneNumber = sharedViewModel.phoneNumber
    if (existingPhoneNumber != null) {
      fragmentViewModel.restoreState(existingPhoneNumber)
      spinnerView.setText(existingPhoneNumber.countryCode.toString())
      fragmentViewModel.formatter?.let {
        bindPhoneNumberFormatter(it)
      }
      phoneNumberInputLayout.setText(existingPhoneNumber.nationalNumber.toString())
    } else {
      spinnerView.setText(fragmentViewModel.countryPrefix().toString())
    }

    ViewUtil.focusAndShowKeyboard(phoneNumberInputLayout)
  }

  private fun bindPhoneNumberFormatter(formatter: TextWatcher) {
    if (formatter != currentPhoneNumberFormatter) {
      currentPhoneNumberFormatter?.let { oldWatcher ->
        Log.d(TAG, "Removing current phone number formatter in fragment")
        phoneNumberInputLayout.removeTextChangedListener(oldWatcher)
      }
      phoneNumberInputLayout.addTextChangedListener(formatter)
      currentPhoneNumberFormatter = formatter
      Log.d(TAG, "Updating phone number formatter in fragment")
    }
  }

  private fun handleChallenges(remainingChallenges: List<Challenge>) {
    when (remainingChallenges.first()) {
      Challenge.CAPTCHA -> moveToCaptcha()
      Challenge.PUSH -> performPushChallenge()
    }
  }

  private fun performPushChallenge() {
    sharedViewModel.requestAndSubmitPushToken(requireContext(), ::handleErrorResponse)
  }

  private fun initializeInputFields() {
    binding.countryCode.editText?.addTextChangedListener { s ->
      val sanitized = s.toString().filter { c -> c.isDigit() }
      if (sanitized.isNotNullOrBlank()) {
        val countryCode: Int = sanitized.toInt()
        fragmentViewModel.setCountry(countryCode)
      }
    }

    phoneNumberInputLayout.addTextChangedListener {
      fragmentViewModel.setPhoneNumber(it?.toString())
    }

    val scrollView = binding.scrollView
    val registerButton = binding.registerButton
    phoneNumberInputLayout.onFocusChangeListener = View.OnFocusChangeListener { _: View?, hasFocus: Boolean ->
      if (hasFocus) {
        scrollView.postDelayed({
          scrollView.smoothScrollTo(0, registerButton.bottom)
        }, 250)
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
    spinnerView.addTextChangedListener(afterTextChanged = ::onCountryDropDownChanged)
  }

  private fun onCountryDropDownChanged(s: Editable?) {
    if (s.isNullOrEmpty()) {
      return
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

  private fun presentRegisterButton(sharedState: RegistrationV2State) {
    binding.registerButton.isEnabled = sharedState.phoneNumber != null && PhoneNumberUtil.getInstance().isValidNumber(sharedState.phoneNumber)
    if (sharedState.inProgress) {
      binding.registerButton.setSpinning()
    } else {
      binding.registerButton.cancelSpinning()
    }
  }

  private fun presentLocalError(state: EnterPhoneNumberV2State) {
    when (state.error) {
      EnterPhoneNumberV2State.Error.NONE -> Unit

      EnterPhoneNumberV2State.Error.INVALID_PHONE_NUMBER -> {
        MaterialAlertDialogBuilder(requireContext()).apply {
          setTitle(R.string.RegistrationActivity_invalid_number)
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
        handlePromptForNoPlayServices()
      }

      EnterPhoneNumberV2State.Error.PLAY_SERVICES_NEEDS_UPDATE -> {
        GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, 0)?.show()
      }

      EnterPhoneNumberV2State.Error.PLAY_SERVICES_TRANSIENT -> {
        MaterialAlertDialogBuilder(requireContext()).apply {
          setTitle(R.string.RegistrationActivity_play_services_error)
          setMessage(R.string.RegistrationActivity_google_play_services_is_updating_or_unavailable)
          setPositiveButton(android.R.string.ok) { _, _ -> fragmentViewModel.clearError() }
          setOnCancelListener { fragmentViewModel.clearError() }
          setOnDismissListener { fragmentViewModel.clearError() }
          show()
        }
      }
    }
  }

  private fun presentNetworkError(networkError: Throwable) {
    Log.i(TAG, "Unknown error during verification code request", networkError)
    MaterialAlertDialogBuilder(requireContext()).apply {
      setMessage(R.string.RegistrationActivity_unable_to_connect_to_service)
      setPositiveButton(android.R.string.ok) { _, _ -> sharedViewModel.clearNetworkError() }
      setOnCancelListener { sharedViewModel.clearNetworkError() }
      setOnDismissListener { sharedViewModel.clearNetworkError() }
      show()
    }
  }

  private fun handleErrorResponse(result: RegistrationResult) {
    if (!result.isSuccess()) {
      Log.i(TAG, "Handling error response.", result.getCause())
    }
    when (result) {
      is RegistrationSessionCreationResult.Success,
      is VerificationCodeRequestResult.Success -> Unit
      is RegistrationSessionCreationResult.AttemptsExhausted,
      is VerificationCodeRequestResult.AttemptsExhausted -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_service))
      is VerificationCodeRequestResult.ChallengeRequired -> {
        handleChallenges(result.challenges)
      }
      is VerificationCodeRequestResult.ExternalServiceFailure -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service), skipToNextScreen)
      is VerificationCodeRequestResult.ImpossibleNumber -> {
        MaterialAlertDialogBuilder(requireContext()).apply {
          setMessage(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid, fragmentViewModel.phoneNumber?.toE164()))
          setPositiveButton(android.R.string.ok, null)
          show()
        }
      }
      is VerificationCodeRequestResult.InvalidTransportModeFailure -> {
        MaterialAlertDialogBuilder(requireContext()).apply {
          setMessage(R.string.RegistrationActivity_we_couldnt_send_you_a_verification_code)
          setPositiveButton(R.string.RegistrationActivity_voice_call) { _, _ ->
            sharedViewModel.requestVerificationCall(requireContext(), ::handleErrorResponse)
          }
          setNegativeButton(R.string.RegistrationActivity_cancel, null)
          show()
        }
      }
      is RegistrationSessionCreationResult.MalformedRequest,
      is VerificationCodeRequestResult.MalformedRequest -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service), skipToNextScreen)
      is VerificationCodeRequestResult.MustRetry -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service), skipToNextScreen)
      is VerificationCodeRequestResult.NonNormalizedNumber -> handleNonNormalizedNumberError(result.originalNumber, result.normalizedNumber, fragmentViewModel.mode)
      is RegistrationSessionCreationResult.RateLimited -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_try_again, result.timeRemaining.milliseconds.toString()))
      is VerificationCodeRequestResult.RateLimited -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_try_again, result.timeRemaining.milliseconds.toString()))
      is VerificationCodeRequestResult.TokenNotAccepted -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_we_need_to_verify_that_youre_human)) { _, _ -> moveToCaptcha() }
      else -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service))
    }
  }

  private fun moveToCaptcha() {
    findNavController().safeNavigate(EnterPhoneNumberV2FragmentDirections.actionRequestCaptcha())
  }

  private fun presentRemoteErrorDialog(message: String, positiveButtonListener: DialogInterface.OnClickListener? = null) {
    MaterialAlertDialogBuilder(requireContext()).apply {
      setMessage(message)
      setPositiveButton(android.R.string.ok, positiveButtonListener)
      show()
    }
  }

  private fun handleNonNormalizedNumberError(originalNumber: String, normalizedNumber: String, mode: RegistrationRepository.Mode) {
    try {
      val phoneNumber = PhoneNumberUtil.getInstance().parse(normalizedNumber, null)

      MaterialAlertDialogBuilder(requireContext()).apply {
        setTitle(R.string.RegistrationActivity_non_standard_number_format)
        setMessage(getString(R.string.RegistrationActivity_the_number_you_entered_appears_to_be_a_non_standard, originalNumber, normalizedNumber))
        setNegativeButton(android.R.string.no) { d: DialogInterface, i: Int -> d.dismiss() }
        setNeutralButton(R.string.RegistrationActivity_contact_signal_support) { dialogInterface, _ ->
          val subject = getString(R.string.RegistrationActivity_signal_android_phone_number_format)
          val body = SupportEmailUtil.generateSupportEmailBody(requireContext(), R.string.RegistrationActivity_signal_android_phone_number_format, null, null)

          CommunicationActions.openEmail(requireContext(), SupportEmailUtil.getSupportEmailAddress(requireContext()), subject, body)
          dialogInterface.dismiss()
        }
        setPositiveButton(R.string.yes) { dialogInterface, _ ->
          spinnerView.setText(phoneNumber.countryCode.toString())
          phoneNumberInputLayout.setText(phoneNumber.nationalNumber.toString())
          when (mode) {
            RegistrationRepository.Mode.SMS_WITH_LISTENER,
            RegistrationRepository.Mode.SMS_WITHOUT_LISTENER -> sharedViewModel.requestSmsCode(requireContext(), ::handleErrorResponse)
            RegistrationRepository.Mode.PHONE_CALL -> sharedViewModel.requestVerificationCall(requireContext(), ::handleErrorResponse)
          }
          dialogInterface.dismiss()
        }
        show()
      }
    } catch (e: NumberParseException) {
      Log.w(TAG, "Failed to parse number!", e)

      Dialogs.showAlertDialog(
        requireContext(),
        getString(R.string.RegistrationActivity_invalid_number),
        getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid, fragmentViewModel.phoneNumber?.toE164())
      )
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
      sharedViewModel.uiState.value?.let { value ->
        val now = System.currentTimeMillis()
        if (value.phoneNumber == null) {
          fragmentViewModel.setError(EnterPhoneNumberV2State.Error.INVALID_PHONE_NUMBER)
          sharedViewModel.setInProgress(false)
        } else if (now < value.nextSmsTimestamp) {
          moveToVerificationEntryScreen()
        } else {
          presentConfirmNumberDialog(value.phoneNumber, value.isReRegister, value.canSkipSms, missingFcmConsentRequired = true)
        }
      }
    }
  }

  private fun onFcmTokenRetrieved(value: RegistrationV2State) {
    if (value.phoneNumber == null) {
      fragmentViewModel.setError(EnterPhoneNumberV2State.Error.INVALID_PHONE_NUMBER)
      sharedViewModel.setInProgress(false)
    } else {
      presentConfirmNumberDialog(value.phoneNumber, value.isReRegister, value.canSkipSms, missingFcmConsentRequired = false)
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

  private fun handleConfirmNumberDialogCanceled() {
    Log.d(TAG, "User canceled confirm number, returning to edit number.")
    sharedViewModel.setInProgress(false)
    ViewUtil.focusAndMoveCursorToEndAndOpenKeyboard(phoneNumberInputLayout)
  }

  private fun presentConfirmNumberDialog(phoneNumber: PhoneNumber, isReRegister: Boolean, canSkipSms: Boolean, missingFcmConsentRequired: Boolean) {
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
        if (missingFcmConsentRequired) {
          handlePromptForNoPlayServices()
        } else {
          sharedViewModel.onUserConfirmedPhoneNumber(requireContext(), ::handleErrorResponse)
        }
      }
      setNegativeButton(R.string.RegistrationActivity_edit_number) { _, _ -> handleConfirmNumberDialogCanceled() }
      setOnCancelListener { _ -> handleConfirmNumberDialogCanceled() }
    }.show()
  }

  private fun handlePromptForNoPlayServices() {
    Log.d(TAG, "Device does not have Play Services, showing consent dialog.")
    MaterialAlertDialogBuilder(requireContext()).apply {
      setTitle(R.string.RegistrationActivity_missing_google_play_services)
      setMessage(R.string.RegistrationActivity_this_device_is_missing_google_play_services)
      setPositiveButton(R.string.RegistrationActivity_i_understand) { _, _ ->
        Log.d(TAG, "User confirmed number.")
        sharedViewModel.onUserConfirmedPhoneNumber(requireContext(), ::handleErrorResponse)
      }
      setNegativeButton(android.R.string.cancel, null)
      setOnCancelListener { fragmentViewModel.clearError() }
      setOnDismissListener { fragmentViewModel.clearError() }
      show()
    }
  }

  private fun moveToEnterPinScreen() {
    findNavController().safeNavigate(EnterPhoneNumberV2FragmentDirections.actionReRegisterWithPinV2Fragment())
    sharedViewModel.setInProgress(false)
  }

  private fun moveToVerificationEntryScreen() {
    findNavController().safeNavigate(EnterPhoneNumberV2FragmentDirections.actionEnterVerificationCode())
    sharedViewModel.setInProgress(false)
  }

  private fun popBackStack() {
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.INITIALIZATION)
    findNavController().popBackStack()
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
