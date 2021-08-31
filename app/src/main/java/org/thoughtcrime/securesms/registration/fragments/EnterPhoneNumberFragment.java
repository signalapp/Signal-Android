package org.thoughtcrime.securesms.registration.fragments;

import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;
import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.showConfirmNumberDialogIfTranslated;
import static org.thoughtcrime.securesms.util.CircularProgressButtonUtil.cancelSpinning;
import static org.thoughtcrime.securesms.util.CircularProgressButtonUtil.setSpinning;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.dd.CircularProgressButton;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.LabeledEditText;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository.Mode;
import org.thoughtcrime.securesms.registration.viewmodel.NumberViewState;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.PlayServicesUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public final class EnterPhoneNumberFragment extends LoggingFragment {

  private static final String TAG = Log.tag(EnterPhoneNumberFragment.class);

  private LabeledEditText        countryCode;
  private LabeledEditText        number;
  private ArrayAdapter<String>   countrySpinnerAdapter;
  private AsYouTypeFormatter     countryFormatter;
  private CircularProgressButton register;
  private Spinner                countrySpinner;
  private View                   cancel;
  private ScrollView             scrollView;
  private RegistrationViewModel  viewModel;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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

    disposables.bindTo(getViewLifecycleOwner().getLifecycle());
    viewModel = ViewModelProviders.of(requireActivity()).get(RegistrationViewModel.class);

    if (viewModel.isReregister()) {
      cancel.setVisibility(View.VISIBLE);
      cancel.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    } else {
      cancel.setVisibility(View.GONE);
    }

    NumberViewState number = viewModel.getNumber();

    initNumber(number);

    countryCode.getInput().addTextChangedListener(new CountryCodeChangedListener());

    if (viewModel.hasCaptchaToken()) {
      handleRegister(requireContext());
    }

    countryCode.getInput().setImeOptions(EditorInfo.IME_ACTION_NEXT);

    Toolbar toolbar = view.findViewById(R.id.toolbar);
    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(null);
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    inflater.inflate(R.menu.enter_phone_number, menu);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.phone_menu_use_proxy) {
      Navigation.findNavController(requireView()).navigate(EnterPhoneNumberFragmentDirections.actionEditProxy());
      return true;
    } else {
      return false;
    }
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
        ViewUtil.hideKeyboard(requireContext(), v);
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

    final NumberViewState number     = viewModel.getNumber();
    final String          e164number = number.getE164Number();

    if (!number.isValid()) {
      Dialogs.showAlertDialog(context,
                              getString(R.string.RegistrationActivity_invalid_number),
                              String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid), e164number));
      return;
    }

    PlayServicesUtil.PlayServicesStatus fcmStatus = PlayServicesUtil.getPlayServicesStatus(context);

    if (fcmStatus == PlayServicesUtil.PlayServicesStatus.SUCCESS) {
      confirmNumberPrompt(context, e164number, () -> handleRequestVerification(context, true));
    } else if (fcmStatus == PlayServicesUtil.PlayServicesStatus.MISSING) {
      confirmNumberPrompt(context, e164number, () -> handlePromptForNoPlayServices(context));
    } else if (fcmStatus == PlayServicesUtil.PlayServicesStatus.NEEDS_UPDATE) {
      GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, 0).show();
    } else {
      Dialogs.showAlertDialog(context,
                              getString(R.string.RegistrationActivity_play_services_error),
                              getString(R.string.RegistrationActivity_google_play_services_is_updating_or_unavailable));
    }
  }

  private void handleRequestVerification(@NonNull Context context, boolean fcmSupported) {
    setSpinning(register);
    disableAllEntries();

    if (fcmSupported) {
      SmsRetrieverClient client = SmsRetriever.getClient(context);
      Task<Void>         task   = client.startSmsRetriever();

      task.addOnSuccessListener(none -> {
        Log.i(TAG, "Successfully registered SMS listener.");
        requestVerificationCode(Mode.SMS_WITH_LISTENER);
      });

      task.addOnFailureListener(e -> {
        Log.w(TAG, "Failed to register SMS listener.", e);
        requestVerificationCode(Mode.SMS_WITHOUT_LISTENER);
      });
    } else {
      Log.i(TAG, "FCM is not supported, using no SMS listener");
      requestVerificationCode(Mode.SMS_WITHOUT_LISTENER);
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
    if (viewModel.isReregister()) {
      cancel.setVisibility(View.VISIBLE);
    }
  }

  private void requestVerificationCode(@NonNull Mode mode) {
    NavController navController = NavHostFragment.findNavController(this);

    Disposable request = viewModel.requestVerificationCode(mode)
                                  .doOnSubscribe(unused -> TextSecurePreferences.setPushRegistered(ApplicationDependencies.getApplication(), false))
                                  .observeOn(AndroidSchedulers.mainThread())
                                  .subscribe(processor -> {
                                    if (processor.hasResult()) {
                                      navController.navigate(EnterPhoneNumberFragmentDirections.actionEnterVerificationCode());
                                    } else if (processor.localRateLimit()) {
                                      Log.i(TAG, "Unable to request sms code due to local rate limit");
                                      navController.navigate(EnterPhoneNumberFragmentDirections.actionEnterVerificationCode());
                                    } else if (processor.captchaRequired()) {
                                      Log.i(TAG, "Unable to request sms code due to captcha required");
                                      navController.navigate(EnterPhoneNumberFragmentDirections.actionRequestCaptcha());
                                    } else if (processor.rateLimit()) {
                                      Log.i(TAG, "Unable to request sms code due to rate limit");
                                      Toast.makeText(register.getContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show();
                                    } else {
                                      Log.w(TAG, "Unable to request sms code", processor.getError());
                                      Toast.makeText(register.getContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
                                    }

                                    cancelSpinning(register);
                                    enableAllEntries();
                                  });

    disposables.add(request);
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
    String number            = numberViewState.getNationalNumber();
    String regionDisplayName = numberViewState.getCountryDisplayName();

    this.countryCode.setText(String.valueOf(countryCode));

    setCountryDisplay(regionDisplayName);

    String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);
    setCountryFormatter(regionCode);

    if (!TextUtils.isEmpty(number)) {
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

      viewModel.onCountrySelected(null, countryCode);
      setCountryDisplay(viewModel.getNumber().getCountryDisplayName());
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
      String number = reformatText(s);

      if (number == null) return;

      viewModel.setNationalNumber(number);

      setCountryDisplay(viewModel.getNumber().getCountryDisplayName());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }
  }

  private String reformatText(Editable s) {
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

    return justDigits.toString();
  }

  private void setCountryFormatter(@Nullable String regionCode) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();

    countryFormatter = regionCode != null ? util.getAsYouTypeFormatter(regionCode) : null;

    reformatText(number.getText());
  }

  private void handlePromptForNoPlayServices(@NonNull Context context) {
    new MaterialAlertDialogBuilder(context)
        .setTitle(R.string.RegistrationActivity_missing_google_play_services)
        .setMessage(R.string.RegistrationActivity_this_device_is_missing_google_play_services)
        .setPositiveButton(R.string.RegistrationActivity_i_understand, (dialog1, which) -> handleRequestVerification(context, false))
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
                                          ViewUtil.hideKeyboard(context, number.getInput());
                                          onConfirmed.run();
                                        },
                                        () -> number.focusAndMoveCursorToEndAndOpenKeyboard());
  }
}
