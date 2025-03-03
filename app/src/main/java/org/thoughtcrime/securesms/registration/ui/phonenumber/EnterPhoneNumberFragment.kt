/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
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
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.i18n.phonenumbers.AsYouTypeFormatter
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import org.signal.core.util.ThreadUtil
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationEnterPhoneNumberBinding
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCheckResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCreationResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.ui.RegistrationState
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryCodeFragment
import org.thoughtcrime.securesms.registration.ui.toE164
import org.thoughtcrime.securesms.registration.util.CountryPrefix
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.Dialogs
import org.thoughtcrime.securesms.util.PlayServicesUtil
import org.thoughtcrime.securesms.util.SignalE164Util
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
class EnterPhoneNumberFragment : LoggingFragment(R.layout.fragment_registration_enter_phone_number) {

  private val TAG = Log.tag(EnterPhoneNumberFragment::class.java)
  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val fragmentViewModel by viewModels<EnterPhoneNumberViewModel>()
  private val binding: FragmentRegistrationEnterPhoneNumberBinding by ViewBinderDelegate(FragmentRegistrationEnterPhoneNumberBinding::bind)

  private val skipToNextScreen: DialogInterface.OnClickListener = DialogInterface.OnClickListener { _: DialogInterface?, _: Int -> moveToVerificationEntryScreen() }

  private lateinit var spinnerAdapter: ArrayAdapter<CountryPrefix>
  private lateinit var phoneNumberInputLayout: TextInputEditText
  private lateinit var spinnerView: TextInputEditText
  private lateinit var countryPickerView: View

  private var currentPhoneNumberFormatter: AsYouTypeFormatter? = null

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
    spinnerView = binding.countryCode.editText as TextInputEditText
    countryPickerView = binding.countryPicker

    countryPickerView.setOnClickListener {
      moveToCountryPickerScreen()
    }

    parentFragmentManager.setFragmentResultListener(
      CountryCodeFragment.REQUEST_KEY_COUNTRY,
      this
    ) { _, bundle ->
      val country: Country = bundle.getParcelableCompat(CountryCodeFragment.RESULT_COUNTRY, Country::class.java)!!
      fragmentViewModel.setCountry(country.countryCode, country)
    }

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
      updateEnabledControls(sharedState.inProgress, sharedState.isReRegister)

      sharedState.networkError?.let {
        presentNetworkError(it)
        sharedViewModel.networkErrorShown()
      }

      sharedState.sessionCreationError?.let {
        handleSessionCreationError(it)
        sharedViewModel.sessionCreationErrorShown()
      }

      sharedState.sessionStateError?.let {
        handleSessionStateError(it)
        sharedViewModel.sessionStateErrorShown()
      }

      sharedState.registerAccountError?.let {
        handleRegistrationErrorResponse(it)
        sharedViewModel.registerAccountErrorShown()
      }

      if (sharedState.challengesRequested.contains(Challenge.CAPTCHA) && sharedState.captchaToken.isNotNullOrBlank()) {
        sharedViewModel.submitCaptchaToken(requireContext())
      } else if (sharedState.challengesRemaining.isNotEmpty()) {
        handleChallenges(sharedState.challengesRemaining)
      } else if (sharedState.registrationCheckpoint >= RegistrationCheckpoint.PHONE_NUMBER_CONFIRMED && sharedState.canSkipSms) {
        moveToEnterPinScreen()
      } else if (sharedState.registrationCheckpoint >= RegistrationCheckpoint.VERIFICATION_CODE_REQUESTED) {
        moveToVerificationEntryScreen()
      }
    }

    fragmentViewModel
      .uiState
      .map { it.phoneNumberRegionCode }
      .distinctUntilChanged()
      .observe(viewLifecycleOwner) { regionCode ->
        if (regionCode.isNotNullOrBlank()) {
          currentPhoneNumberFormatter = PhoneNumberUtil.getInstance().getAsYouTypeFormatter(regionCode)
          reformatText(phoneNumberInputLayout.text)
          phoneNumberInputLayout.requestFocus()
        }
      }

    fragmentViewModel.uiState.observe(viewLifecycleOwner) { fragmentState ->
      if (fragmentViewModel.isEnteredNumberPossible(fragmentState)) {
        sharedViewModel.setPhoneNumber(fragmentViewModel.parsePhoneNumber(fragmentState))
        sharedViewModel.nationalNumber = ""
      } else {
        sharedViewModel.setPhoneNumber(null)
      }

      updateCountrySelection(fragmentState.country)

      if (fragmentState.error != EnterPhoneNumberState.Error.NONE) {
        presentLocalError(fragmentState)
      }
    }

    initializeInputFields()

    val existingPhoneNumber = sharedViewModel.phoneNumber
    val existingNationalNumber = sharedViewModel.nationalNumber
    if (existingPhoneNumber != null) {
      fragmentViewModel.restoreState(existingPhoneNumber)
      spinnerView.setText(existingPhoneNumber.countryCode.toString())
      phoneNumberInputLayout.setText(existingPhoneNumber.nationalNumber.toString())
    } else if (spinnerView.text?.isEmpty() == true) {
      spinnerView.setText(fragmentViewModel.getDefaultCountryCode(requireContext()).toString())
      phoneNumberInputLayout.setText(existingNationalNumber)
    } else {
      phoneNumberInputLayout.setText(existingNationalNumber)
    }

    ViewUtil.focusAndShowKeyboard(phoneNumberInputLayout)
  }

  private fun updateCountrySelection(country: Country?) {
    if (country != null) {
      binding.countryEmoji.visible = true
      binding.countryEmoji.text = country.emoji
      binding.country.text = country.name
      if (spinnerView.text.toString() != country.countryCode.toString()) {
        spinnerView.setText(country.countryCode.toString())
      }
    } else {
      binding.countryEmoji.visible = false
      binding.country.text = getString(R.string.RegistrationActivity_select_a_country)
    }
  }

  private fun reformatText(text: Editable?) {
    if (text.isNullOrEmpty()) {
      return
    }

    currentPhoneNumberFormatter?.let { formatter ->
      formatter.clear()

      var formattedNumber: String? = null
      text.forEach {
        if (it.isDigit()) {
          formattedNumber = formatter.inputDigit(it)
        }
      }

      if (formattedNumber != null && text.toString() != formattedNumber) {
        text.replace(0, text.length, formattedNumber)
      }
    }
  }

  private fun handleChallenges(remainingChallenges: List<Challenge>) {
    when (remainingChallenges.first()) {
      Challenge.CAPTCHA -> moveToCaptcha()
      Challenge.PUSH -> performPushChallenge()
    }
  }

  private fun performPushChallenge() {
    sharedViewModel.requestAndSubmitPushToken(requireContext())
  }

  private fun initializeInputFields() {
    binding.countryCode.editText?.addTextChangedListener { s ->
      val sanitized = s.toString().filter { c -> c.isDigit() }
      if (sanitized.isNotNullOrBlank()) {
        val countryCode: Int = sanitized.toInt()
        fragmentViewModel.setCountry(countryCode)
      } else {
        binding.countryCode.editText?.setHint(R.string.RegistrationActivity_default_country_code)
        fragmentViewModel.clearCountry()
      }
    }

    phoneNumberInputLayout.addTextChangedListener(
      afterTextChanged = {
        reformatText(it)
        fragmentViewModel.setPhoneNumber(it?.toString())
        sharedViewModel.nationalNumber = it?.toString() ?: ""
      }
    )

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
  }

  private fun presentRegisterButton(sharedState: RegistrationState) {
    binding.registerButton.isEnabled = sharedState.phoneNumber != null && PhoneNumberUtil.getInstance().isPossibleNumber(sharedState.phoneNumber)
    if (sharedState.inProgress) {
      binding.registerButton.setSpinning()
    } else {
      binding.registerButton.cancelSpinning()
    }
  }

  private fun presentLocalError(state: EnterPhoneNumberState) {
    when (state.error) {
      EnterPhoneNumberState.Error.NONE -> Unit

      EnterPhoneNumberState.Error.INVALID_PHONE_NUMBER -> {
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

      EnterPhoneNumberState.Error.PLAY_SERVICES_MISSING -> {
        handlePromptForNoPlayServices()
      }

      EnterPhoneNumberState.Error.PLAY_SERVICES_NEEDS_UPDATE -> {
        GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, 0)?.show()
      }

      EnterPhoneNumberState.Error.PLAY_SERVICES_TRANSIENT -> {
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
      setPositiveButton(android.R.string.ok, null)
      show()
    }
  }

  private fun handleSessionCreationError(result: RegistrationSessionResult) {
    if (!result.isSuccess()) {
      Log.i(TAG, "Handling error response of ${result.javaClass.name}", result.getCause())
    }
    when (result) {
      is RegistrationSessionCheckResult.Success,
      is RegistrationSessionCreationResult.Success -> throw IllegalStateException("Session error handler called on successful response!")

      is RegistrationSessionCreationResult.AttemptsExhausted -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_service))
      is RegistrationSessionCreationResult.MalformedRequest -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service), skipToNextScreen)

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

  private fun handleSessionStateError(result: VerificationCodeRequestResult) {
    if (!result.isSuccess()) {
      Log.i(TAG, "Handling error response.", result.getCause())
    }
    when (result) {
      is VerificationCodeRequestResult.Success -> throw IllegalStateException("Session error handler called on successful response!")
      is VerificationCodeRequestResult.ChallengeRequired -> handleChallenges(result.challenges)
      is VerificationCodeRequestResult.ExternalServiceFailure -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_sms_provider_error))
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
            sharedViewModel.requestVerificationCall(requireContext())
          }
          setNegativeButton(R.string.RegistrationActivity_cancel, null)
          show()
        }
      }

      is VerificationCodeRequestResult.MalformedRequest -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service), skipToNextScreen)
      is VerificationCodeRequestResult.RequestVerificationCodeRateLimited -> {
        Log.i(TAG, result.log())
        handleRequestVerificationCodeRateLimited(result)
      }

      is VerificationCodeRequestResult.SubmitVerificationCodeRateLimited -> presentGenericError(result)
      is VerificationCodeRequestResult.NonNormalizedNumber -> handleNonNormalizedNumberError(result.originalNumber, result.normalizedNumber, fragmentViewModel.mode)
      is VerificationCodeRequestResult.RateLimited -> {
        val timeRemaining = result.timeRemaining?.milliseconds
        Log.i(TAG, "Session patch rate limited! Next attempt: $timeRemaining")
        if (timeRemaining != null) {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_try_again, timeRemaining.toString()))
        } else {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later))
        }
      }

      is VerificationCodeRequestResult.TokenNotAccepted -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_we_need_to_verify_that_youre_human)) { _, _ -> moveToCaptcha() }
      is VerificationCodeRequestResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      is VerificationCodeRequestResult.AlreadyVerified -> presentGenericError(result)
      is VerificationCodeRequestResult.NoSuchSession -> presentGenericError(result)
      is VerificationCodeRequestResult.UnknownError -> presentGenericError(result)
    }
  }

  private fun presentGenericError(result: RegistrationResult) {
    Log.i(TAG, "Received unhandled response: ${result.javaClass.name}", result.getCause())
    presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service))
  }

  private fun handleRegistrationErrorResponse(result: RegisterAccountResult) {
    when (result) {
      is RegisterAccountResult.Success -> throw IllegalStateException("Register account error handler called on successful response!")
      is RegisterAccountResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      is RegisterAccountResult.AttemptsExhausted -> presentAccountLocked()
      is RegisterAccountResult.RateLimited -> presentRateLimitedDialog()
      is RegisterAccountResult.SvrNoData -> presentAccountLocked()
      else -> presentGenericError(result)
    }
  }

  private fun presentRegistrationLocked(timeRemaining: Long) {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionPhoneNumberRegistrationLock(timeRemaining))
    sharedViewModel.setInProgress(false)
  }

  private fun presentRateLimitedDialog() {
    presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_service))
  }

  private fun presentAccountLocked() {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionPhoneNumberAccountLocked())
    ThreadUtil.postToMain { sharedViewModel.setInProgress(false) }
  }

  private fun moveToCaptcha() {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionRequestCaptcha())
    ThreadUtil.postToMain { sharedViewModel.setInProgress(false) }
  }

  private fun presentRemoteErrorDialog(message: String, positiveButtonListener: DialogInterface.OnClickListener? = null) {
    MaterialAlertDialogBuilder(requireContext()).apply {
      setMessage(message)
      setPositiveButton(android.R.string.ok, positiveButtonListener)
      show()
    }
  }

  private fun handleRequestVerificationCodeRateLimited(result: VerificationCodeRequestResult.RequestVerificationCodeRateLimited) {
    if (result.willBeAbleToRequestAgain) {
      Log.i(TAG, "New verification code cannot be requested yet but can soon, moving to enter code to show timers")
      moveToVerificationEntryScreen()
    } else {
      Log.w(TAG, "Unable to request new verification code, prompting to start new session")
      MaterialAlertDialogBuilder(requireContext()).apply {
        setMessage(R.string.RegistrationActivity_unable_to_connect_to_service)
        setPositiveButton(R.string.NetworkFailure__retry) { _, _ ->
          onRegistrationButtonClicked()
        }
        setNegativeButton(android.R.string.cancel, null)
        show()
      }
    }
  }

  private fun handleNonNormalizedNumberError(originalNumber: String, normalizedNumber: String, mode: RegistrationRepository.E164VerificationMode) {
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
            RegistrationRepository.E164VerificationMode.SMS_WITH_LISTENER,
            RegistrationRepository.E164VerificationMode.SMS_WITHOUT_LISTENER -> sharedViewModel.requestSmsCode(requireContext())

            RegistrationRepository.E164VerificationMode.PHONE_CALL -> sharedViewModel.requestVerificationCall(requireContext())
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
        val now = System.currentTimeMillis().milliseconds
        if (value.phoneNumber == null) {
          fragmentViewModel.setError(EnterPhoneNumberState.Error.INVALID_PHONE_NUMBER)
          sharedViewModel.setInProgress(false)
        } else if (now < value.nextSmsTimestamp) {
          moveToVerificationEntryScreen()
        } else {
          presentConfirmNumberDialog(value.phoneNumber, value.isReRegister, value.canSkipSms, missingFcmConsentRequired = true)
        }
      }
    }
  }

  private fun onFcmTokenRetrieved(value: RegistrationState) {
    if (value.phoneNumber == null) {
      fragmentViewModel.setError(EnterPhoneNumberState.Error.INVALID_PHONE_NUMBER)
      sharedViewModel.setInProgress(false)
    } else {
      presentConfirmNumberDialog(value.phoneNumber, value.isReRegister, value.canSkipSms, missingFcmConsentRequired = false)
    }
  }

  private fun updateEnabledControls(showProgress: Boolean, isReRegister: Boolean) {
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
        fragmentViewModel.setError(EnterPhoneNumberState.Error.PLAY_SERVICES_MISSING)
        return false
      }

      PlayServicesUtil.PlayServicesStatus.NEEDS_UPDATE -> {
        fragmentViewModel.setError(EnterPhoneNumberState.Error.PLAY_SERVICES_NEEDS_UPDATE)
        return false
      }

      PlayServicesUtil.PlayServicesStatus.TRANSIENT_ERROR -> {
        fragmentViewModel.setError(EnterPhoneNumberState.Error.PLAY_SERVICES_TRANSIENT)
        return false
      }

      null -> {
        Log.w(TAG, "Null result received from PlayServicesUtil, marking Play Services as missing.")
        fragmentViewModel.setError(EnterPhoneNumberState.Error.PLAY_SERVICES_MISSING)
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
      append(SpanUtil.bold(SignalE164Util.prettyPrint(phoneNumber.toE164())))
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
          sharedViewModel.onUserConfirmedPhoneNumber(requireContext())
        }
      }
      setNegativeButton(R.string.RegistrationActivity_edit_number) { _, _ -> handleConfirmNumberDialogCanceled() }
      setOnCancelListener { _ -> handleConfirmNumberDialogCanceled() }
    }.show()
  }

  private fun handlePromptForNoPlayServices() {
    val context = activity

    if (context != null) {
      Log.d(TAG, "Device does not have Play Services, showing consent dialog.")
      MaterialAlertDialogBuilder(context).apply {
        setTitle(R.string.RegistrationActivity_missing_google_play_services)
        setMessage(R.string.RegistrationActivity_this_device_is_missing_google_play_services)
        setPositiveButton(R.string.RegistrationActivity_i_understand) { _, _ ->
          Log.d(TAG, "User confirmed number.")
          sharedViewModel.onUserConfirmedPhoneNumber(AppDependencies.application)
        }
        setNegativeButton(android.R.string.cancel, null)
        setOnCancelListener { fragmentViewModel.clearError() }
        setOnDismissListener { fragmentViewModel.clearError() }
        show()
      }
    }
  }

  private fun moveToEnterPinScreen() {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionReRegisterWithPinFragment())
    sharedViewModel.setInProgress(false)
  }

  private fun moveToVerificationEntryScreen() {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionEnterVerificationCode())
    sharedViewModel.setInProgress(false)
  }

  private fun moveToCountryPickerScreen() {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionCountryPicker(fragmentViewModel.country))
  }

  private fun popBackStack() {
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.INITIALIZATION)
    findNavController().popBackStack()
  }

  private inner class FcmTokenRetrievedObserver : LiveDataObserverCallback<RegistrationState>(sharedViewModel.uiState) {
    override fun onValue(value: RegistrationState): Boolean {
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
        NavHostFragment.findNavController(this@EnterPhoneNumberFragment).safeNavigate(EnterPhoneNumberFragmentDirections.actionEditProxy())
        true
      } else {
        false
      }
    }
  }
}
