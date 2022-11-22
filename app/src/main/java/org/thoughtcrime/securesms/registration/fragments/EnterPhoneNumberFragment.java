package org.thoughtcrime.securesms.registration.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.LabeledEditText;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.registration.VerifyAccountRepository.Mode;
import org.thoughtcrime.securesms.registration.util.RegistrationNumberInputController;
import org.thoughtcrime.securesms.registration.viewmodel.NumberViewState;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.PlayServicesUtil;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;
import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.showConfirmNumberDialogIfTranslated;

public final class EnterPhoneNumberFragment extends LoggingFragment implements RegistrationNumberInputController.Callbacks {

  private static final String TAG = Log.tag(EnterPhoneNumberFragment.class);

  private LabeledEditText                countryCode;
  private LabeledEditText                number;
  private CircularProgressMaterialButton register;
  private Spinner                        countrySpinner;
  private View                           cancel;
  private ScrollView                     scrollView;
  private RegistrationViewModel          viewModel;

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

    RegistrationNumberInputController controller = new RegistrationNumberInputController(requireContext(),
                                                                                         countryCode,
                                                                                         number,
                                                                                         countrySpinner,
                                                                                         true,
                                                                                         this);

    register.setOnClickListener(v -> handleRegister(requireContext()));

    disposables.bindTo(getViewLifecycleOwner().getLifecycle());
    viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

    if (viewModel.isReregister()) {
      cancel.setVisibility(View.VISIBLE);
      cancel.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    } else {
      cancel.setVisibility(View.GONE);
    }

    viewModel.getLiveNumber().observe(getViewLifecycleOwner(), controller::updateNumber);

    if (viewModel.hasCaptchaToken()) {
      ThreadUtil.runOnMainDelayed(() -> handleRegister(requireContext()), 250);
    }

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
      SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), EnterPhoneNumberFragmentDirections.actionEditProxy());
      return true;
    } else {
      return false;
    }
  }

  private void handleRegister(@NonNull Context context) {
    if (TextUtils.isEmpty(countryCode.getText())) {
      showErrorDialog(context, getString(R.string.RegistrationActivity_you_must_specify_your_country_code));
      return;
    }

    if (TextUtils.isEmpty(this.number.getText())) {
      showErrorDialog(context, getString(R.string.RegistrationActivity_you_must_specify_your_phone_number));
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
    register.setSpinning();
    disableAllEntries();

    if (fcmSupported) {
      SmsRetrieverClient client = SmsRetriever.getClient(context);
      Task<Void>         task   = client.startSmsRetriever();
      AtomicBoolean      handled = new AtomicBoolean(false);

      Debouncer debouncer = new Debouncer(TimeUnit.SECONDS.toMillis(5));
      debouncer.publish(() -> {
        if (!handled.getAndSet(true)) {
          Log.w(TAG, "Timed out waiting for SMS listener!");
          requestVerificationCode(Mode.SMS_WITHOUT_LISTENER);
        }
      });

      task.addOnSuccessListener(none -> {
        if (!handled.getAndSet(true)) {
          Log.i(TAG, "Successfully registered SMS listener.");
          requestVerificationCode(Mode.SMS_WITH_LISTENER);
        } else {
          Log.w(TAG, "Successfully registered listener after timeout.");
        }
        debouncer.clear();
      });

      task.addOnFailureListener(e -> {
        if (!handled.getAndSet(true)) {
          Log.w(TAG, "Failed to register SMS listener.", e);
          requestVerificationCode(Mode.SMS_WITHOUT_LISTENER);
        } else {
          Log.w(TAG, "Failed to register listener after timeout.");
        }
        debouncer.clear();
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
                                  .doOnSubscribe(unused -> SignalStore.account().setRegistered(false))
                                  .observeOn(AndroidSchedulers.mainThread())
                                  .subscribe(processor -> {
                                    if (processor.hasResult()) {
                                      SafeNavigation.safeNavigate(navController, EnterPhoneNumberFragmentDirections.actionEnterVerificationCode());
                                    } else if (processor.localRateLimit()) {
                                      Log.i(TAG, "Unable to request sms code due to local rate limit");
                                      SafeNavigation.safeNavigate(navController, EnterPhoneNumberFragmentDirections.actionEnterVerificationCode());
                                    } else if (processor.captchaRequired()) {
                                      Log.i(TAG, "Unable to request sms code due to captcha required");
                                      SafeNavigation.safeNavigate(navController, EnterPhoneNumberFragmentDirections.actionRequestCaptcha());
                                    } else if (processor.rateLimit()) {
                                      Log.i(TAG, "Unable to request sms code due to rate limit");
                                      showErrorDialog(register.getContext(), getString(R.string.RegistrationActivity_rate_limited_to_service));
                                    } else if (processor.isImpossibleNumber()) {
                                      Log.w(TAG, "Impossible number", processor.getError());
                                      Dialogs.showAlertDialog(requireContext(),
                                                              getString(R.string.RegistrationActivity_invalid_number),
                                                              String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid), viewModel.getNumber().getFullFormattedNumber()));
                                    } else if (processor.isNonNormalizedNumber()) {
                                      handleNonNormalizedNumberError(processor.getOriginalNumber(), processor.getNormalizedNumber(), mode);
                                    } else {
                                      Log.i(TAG, "Unknown error during verification code request", processor.getError());
                                      showErrorDialog(register.getContext(), getString(R.string.RegistrationActivity_unable_to_connect_to_service));
                                    }

                                    register.cancelSpinning();
                                    enableAllEntries();
                                  });

    disposables.add(request);
  }

  public void showErrorDialog(Context context, String msg) {
    new MaterialAlertDialogBuilder(context).setMessage(msg).setPositiveButton(R.string.ok, null).show();
  }

  @Override
  public void onNumberFocused() {
    scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, register.getBottom()), 250);
  }

  @Override
  public void onNumberInputNext(@NonNull View view) {
    // Intentionally left blank
  }

  @Override
  public void onNumberInputDone(@NonNull View view) {
    ViewUtil.hideKeyboard(requireContext(), view);
    handleRegister(requireContext());
  }

  @Override
  public void onPickCountry(@NonNull View view) {
    SafeNavigation.safeNavigate(Navigation.findNavController(view), R.id.action_pickCountry);
  }

  @Override
  public void setNationalNumber(@NonNull String number) {
    viewModel.setNationalNumber(number);
  }

  @Override
  public void setCountry(int countryCode) {
    viewModel.onCountrySelected(null, countryCode);
  }

  private void handleNonNormalizedNumberError(@NonNull String originalNumber, @NonNull String normalizedNumber, @NonNull Mode mode) {
    try {
      Phonenumber.PhoneNumber phoneNumber = PhoneNumberUtil.getInstance().parse(normalizedNumber, null);

      new MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.RegistrationActivity_non_standard_number_format)
          .setMessage(getString(R.string.RegistrationActivity_the_number_you_entered_appears_to_be_a_non_standard, originalNumber, normalizedNumber))
          .setNegativeButton(android.R.string.no, (d, i) -> d.dismiss())
          .setNeutralButton(R.string.RegistrationActivity_contact_signal_support, (d, i) -> {
            String subject = getString(R.string.RegistrationActivity_signal_android_phone_number_format);
            String body    = SupportEmailUtil.generateSupportEmailBody(requireContext(), R.string.RegistrationActivity_signal_android_phone_number_format, null, null);

            CommunicationActions.openEmail(requireContext(), SupportEmailUtil.getSupportEmailAddress(requireContext()), subject, body);
            d.dismiss();
          })
          .setPositiveButton(R.string.yes, (d, i) -> {
            countryCode.setText(String.valueOf(phoneNumber.getCountryCode()));
            number.setText(String.valueOf(phoneNumber.getNationalNumber()));
            requestVerificationCode(mode);
            d.dismiss();
          })
          .show();
    } catch (NumberParseException e) {
      Log.w(TAG, "Failed to parse number!", e);

      Dialogs.showAlertDialog(requireContext(),
                              getString(R.string.RegistrationActivity_invalid_number),
                              String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid), viewModel.getNumber().getFullFormattedNumber()));
    }
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
