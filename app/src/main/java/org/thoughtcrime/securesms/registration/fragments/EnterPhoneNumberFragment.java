package org.thoughtcrime.securesms.registration.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.dd.CircularProgressButton;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.Task;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.LabeledEditText;
import org.thoughtcrime.securesms.registration.service.RegistrationCodeRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationService;
import org.thoughtcrime.securesms.registration.viewmodel.NumberViewState;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.PlayServicesUtil;

public final class EnterPhoneNumberFragment extends BaseRegistrationFragment {

  private static final String TAG = Log.tag(EnterPhoneNumberFragment.class);

  private LabeledEditText        countryCode;
  private LabeledEditText        number;
  private ArrayAdapter<String>   countrySpinnerAdapter;
  private AsYouTypeFormatter     countryFormatter;
  private CircularProgressButton register;
  private Spinner                countrySpinner;
  private View                   cancel;
  private ScrollView             scrollView;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_enter_phone_number, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

    countryCode    = view.findViewById(R.id.country_code);
    number         = view.findViewById(R.id.number);
    countrySpinner = view.findViewById(R.id.country_spinner);
    cancel         = view.findViewById(R.id.cancel_button);
    scrollView     = view.findViewById(R.id.scroll_view);
    register       = view.findViewById(R.id.registerButton);

    initializeSpinner(countrySpinner);

    setUpNumberInput();

    register.setOnClickListener(v -> handleRegister(requireContext()));

    if (isReregister()) {
      cancel.setVisibility(View.VISIBLE);
      cancel.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    } else {
      cancel.setVisibility(View.GONE);
    }

    RegistrationViewModel model  = getModel();
    NumberViewState       number = model.getNumber();

    initNumber(number);

    countryCode.getInput().addTextChangedListener(new CountryCodeChangedListener());

    if (model.hasCaptchaToken()) {
      handleRegister(requireContext());
    }

    countryCode.getInput().setImeOptions(EditorInfo.IME_ACTION_NEXT);
  }

  private void setUpNumberInput() {
    EditText numberInput = number.getInput();

    numberInput.addTextChangedListener(new NumberChangedListener());

    number.setOnFocusChangeListener((v, hasFocus) -> {
      if (hasFocus) {
        scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, register.getBottom()), 250);
      }
    });

    numberInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
    numberInput.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        hideKeyboard(requireContext(), v);
        handleRegister(requireContext());
        return true;
      }
      return false;
    });
  }

  private void handleRegister(@NonNull Context context) {
    if (TextUtils.isEmpty(countryCode.getText())) {
      Toast.makeText(context, getString(R.string.RegistrationActivity_you_must_specify_your_country_code), Toast.LENGTH_LONG).show();
      return;
    }

    if (TextUtils.isEmpty(this.number.getText())) {
      Toast.makeText(context, getString(R.string.RegistrationActivity_you_must_specify_your_phone_number), Toast.LENGTH_LONG).show();
      return;
    }

    final NumberViewState number     = getModel().getNumber();
    final String          e164number = number.getE164Number();

    if (!number.isValid()) {
      Dialogs.showAlertDialog(context,
        getString(R.string.RegistrationActivity_invalid_number),
        String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid), e164number));
      return;
    }

    PlayServicesUtil.PlayServicesStatus fcmStatus = PlayServicesUtil.getPlayServicesStatus(context);

    if (fcmStatus == PlayServicesUtil.PlayServicesStatus.SUCCESS) {
      confirmNumberPrompt(context, e164number, () -> handleRequestVerification(context, e164number, true));
    } else if (fcmStatus == PlayServicesUtil.PlayServicesStatus.MISSING) {
      confirmNumberPrompt(context, e164number, () -> handlePromptForNoPlayServices(context, e164number));
    } else if (fcmStatus == PlayServicesUtil.PlayServicesStatus.NEEDS_UPDATE) {
      GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, 0).show();
    } else {
      Dialogs.showAlertDialog(context, getString(R.string.RegistrationActivity_play_services_error),
        getString(R.string.RegistrationActivity_google_play_services_is_updating_or_unavailable));
    }
  }

  private void handleRequestVerification(@NonNull Context context, @NonNull String e164number, boolean fcmSupported) {
    setSpinning(register);
    disableAllEntries();

    if (fcmSupported) {
      SmsRetrieverClient client = SmsRetriever.getClient(context);
      Task<Void>         task   = client.startSmsRetriever();

      task.addOnSuccessListener(none -> {
        Log.i(TAG, "Successfully registered SMS listener.");
        requestVerificationCode(e164number, RegistrationCodeRequest.Mode.SMS_WITH_LISTENER);
      });

      task.addOnFailureListener(e -> {
        Log.w(TAG, "Failed to register SMS listener.", e);
        requestVerificationCode(e164number, RegistrationCodeRequest.Mode.SMS_WITHOUT_LISTENER);
      });
    } else {
      Log.i(TAG, "FCM is not supported, using no SMS listener");
      requestVerificationCode(e164number, RegistrationCodeRequest.Mode.SMS_WITHOUT_LISTENER);
    }
  }

  private void disableAllEntries() {
    countryCode.setEnabled(false);
    number.setEnabled(false);
    countrySpinner.setEnabled(false);
    cancel.setVisibility(View.GONE);
  }

  private void enableAllEntries() {
    countryCode.setEnabled(true);
    number.setEnabled(true);
    countrySpinner.setEnabled(true);
    if (isReregister()) {
      cancel.setVisibility(View.VISIBLE);
    }
  }

  private void requestVerificationCode(String e164number, @NonNull RegistrationCodeRequest.Mode mode) {
    RegistrationViewModel model   = getModel();
    String                captcha = model.getCaptchaToken();
    model.clearCaptchaResponse();

    NavController navController = Navigation.findNavController(register);

    if (!model.getRequestLimiter().canRequest(mode, e164number, System.currentTimeMillis())) {
      Log.i(TAG, "Local rate limited");
      navController.navigate(EnterPhoneNumberFragmentDirections.actionEnterVerificationCode());
      cancelSpinning(register);
      enableAllEntries();
      return;
    }

    RegistrationService registrationService = RegistrationService.getInstance(e164number, model.getRegistrationSecret());

    registrationService.requestVerificationCode(requireActivity(), mode, captcha,
      new RegistrationCodeRequest.SmsVerificationCodeCallback() {

        @Override
        public void onNeedCaptcha() {
          if (getContext() == null) {
            Log.i(TAG, "Got onNeedCaptcha response, but fragment is no longer attached.");
            return;
          }
          navController.navigate(EnterPhoneNumberFragmentDirections.actionRequestCaptcha());
          cancelSpinning(register);
          enableAllEntries();
          model.getRequestLimiter().onUnsuccessfulRequest();
          model.updateLimiter();
        }

        @Override
        public void requestSent(@Nullable String fcmToken) {
          if (getContext() == null) {
            Log.i(TAG, "Got requestSent response, but fragment is no longer attached.");
            return;
          }
          model.setFcmToken(fcmToken);
          model.markASuccessfulAttempt();
          navController.navigate(EnterPhoneNumberFragmentDirections.actionEnterVerificationCode());
          cancelSpinning(register);
          enableAllEntries();
          model.getRequestLimiter().onSuccessfulRequest(mode, e164number, System.currentTimeMillis());
          model.updateLimiter();
        }

        @Override
        public void onRateLimited() {
          Toast.makeText(register.getContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show();
          cancelSpinning(register);
          enableAllEntries();
          model.getRequestLimiter().onUnsuccessfulRequest();
          model.updateLimiter();
        }

        @Override
        public void onError() {
          Toast.makeText(register.getContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
          cancelSpinning(register);
          enableAllEntries();
          model.getRequestLimiter().onUnsuccessfulRequest();
          model.updateLimiter();
        }
      });
  }

  private void initializeSpinner(Spinner countrySpinner) {
    countrySpinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
    countrySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    setCountryDisplay(getString(R.string.RegistrationActivity_select_your_country));

    countrySpinner.setAdapter(countrySpinnerAdapter);
    countrySpinner.setOnTouchListener((view, event) -> {
      if (event.getAction() == MotionEvent.ACTION_UP) {
        pickCountry(view);
      }
      return true;
    });
    countrySpinner.setOnKeyListener((view, keyCode, event) -> {
      if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_UP) {
        pickCountry(view);
        return true;
      }
      return false;
    });
  }

  private void pickCountry(@NonNull View view) {
    Navigation.findNavController(view).navigate(R.id.action_pickCountry);
  }

  private void initNumber(@NonNull NumberViewState numberViewState) {
    int    countryCode       = numberViewState.getCountryCode();
    long   number            = numberViewState.getNationalNumber();
    String regionDisplayName = numberViewState.getCountryDisplayName();

    this.countryCode.setText(String.valueOf(countryCode));

    setCountryDisplay(regionDisplayName);

    String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);
    setCountryFormatter(regionCode);

    if (number != 0) {
      this.number.setText(String.valueOf(number));
    }
  }

  private void setCountryDisplay(String regionDisplayName) {
    countrySpinnerAdapter.clear();
    if (regionDisplayName == null) {
      countrySpinnerAdapter.add(getString(R.string.RegistrationActivity_select_your_country));
    } else {
      countrySpinnerAdapter.add(regionDisplayName);
    }
  }

  private class CountryCodeChangedListener implements TextWatcher {
    @Override
    public void afterTextChanged(Editable s) {
      if (TextUtils.isEmpty(s) || !TextUtils.isDigitsOnly(s)) {
        setCountryDisplay(null);
        countryFormatter = null;
        return;
      }

      int    countryCode = Integer.parseInt(s.toString());
      String regionCode  = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);

      setCountryFormatter(regionCode);

      if (!TextUtils.isEmpty(regionCode) && !regionCode.equals("ZZ")) {
        number.requestFocus();

        int numberLength = number.getText().length();
        number.getInput().setSelection(numberLength, numberLength);
      }

      RegistrationViewModel model = getModel();

      model.onCountrySelected(null, countryCode);
      setCountryDisplay(model.getNumber().getCountryDisplayName());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  }

  private class NumberChangedListener implements TextWatcher {

    @Override
    public void afterTextChanged(Editable s) {
      Long number = reformatText(s);

      if (number == null) return;

      RegistrationViewModel model = getModel();

      model.setNationalNumber(number);

      setCountryDisplay(model.getNumber().getCountryDisplayName());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }
  }

  private Long reformatText(Editable s) {
    if (countryFormatter == null) {
      return null;
    }

    if (TextUtils.isEmpty(s)) {
      return null;
    }

    countryFormatter.clear();

    String        formattedNumber = null;
    StringBuilder justDigits      = new StringBuilder();

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isDigit(c)) {
        formattedNumber = countryFormatter.inputDigit(c);
        justDigits.append(c);
      }
    }

    if (formattedNumber != null && !s.toString().equals(formattedNumber)) {
      s.replace(0, s.length(), formattedNumber);
    }

    if (justDigits.length() == 0) {
      return null;
    }

    return Long.parseLong(justDigits.toString());
  }

  private void setCountryFormatter(@Nullable String regionCode) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();

    countryFormatter = regionCode != null ? util.getAsYouTypeFormatter(regionCode) : null;

    reformatText(number.getText());
  }

  private void handlePromptForNoPlayServices(@NonNull Context context, @NonNull String e164number) {
    new AlertDialog.Builder(context)
                   .setTitle(R.string.RegistrationActivity_missing_google_play_services)
                   .setMessage(R.string.RegistrationActivity_this_device_is_missing_google_play_services)
                   .setPositiveButton(R.string.RegistrationActivity_i_understand, (dialog1, which) -> handleRequestVerification(context, e164number, false))
                   .setNegativeButton(android.R.string.cancel, null)
                   .show();
  }

  protected final void confirmNumberPrompt(@NonNull Context context,
                                           @NonNull String e164number,
                                           @NonNull Runnable onConfirmed)
  {
    showConfirmNumberDialogIfTranslated(context,
                                        R.string.RegistrationActivity_a_verification_code_will_be_sent_to,
                                        e164number,
                                        () -> {
                                          hideKeyboard(context, number.getInput());
                                          onConfirmed.run();
                                        },
                                        () -> number.focusAndMoveCursorToEndAndOpenKeyboard());
  }
}
