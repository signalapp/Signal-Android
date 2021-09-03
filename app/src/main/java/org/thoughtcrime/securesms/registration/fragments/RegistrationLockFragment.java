package org.thoughtcrime.securesms.registration.fragments;

import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;
import static org.thoughtcrime.securesms.util.CircularProgressButtonUtil.cancelSpinning;
import static org.thoughtcrime.securesms.util.CircularProgressButtonUtil.setSpinning;

import android.content.res.Resources;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import com.dd.CircularProgressButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.StorageAccountRestoreJob;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.pin.TokenData;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public final class RegistrationLockFragment extends LoggingFragment {

  private static final String TAG = Log.tag(RegistrationLockFragment.class);

  /**
   * Applies to both V1 and V2 pins, because some V2 pins may have been migrated from V1.
   */
  private static final int MINIMUM_PIN_LENGTH = 4;

  private EditText               pinEntry;
  private View                   forgotPin;
  private CircularProgressButton pinButton;
  private TextView               errorLabel;
  private TextView               keyboardToggle;
  private long                   timeRemaining;
  private boolean                isV1RegistrationLock;
  private RegistrationViewModel  viewModel;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_lock, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.kbs_lock_pin_title));

    pinEntry       = view.findViewById(R.id.kbs_lock_pin_input);
    pinButton      = view.findViewById(R.id.kbs_lock_pin_confirm);
    errorLabel     = view.findViewById(R.id.kbs_lock_pin_input_label);
    keyboardToggle = view.findViewById(R.id.kbs_lock_keyboard_toggle);
    forgotPin      = view.findViewById(R.id.kbs_lock_forgot_pin);

    RegistrationLockFragmentArgs args = RegistrationLockFragmentArgs.fromBundle(requireArguments());

    timeRemaining        = args.getTimeRemaining();
    isV1RegistrationLock = args.getIsV1RegistrationLock();

    if (isV1RegistrationLock) {
      keyboardToggle.setVisibility(View.GONE);
    }

    forgotPin.setVisibility(View.GONE);
    forgotPin.setOnClickListener(v -> handleForgottenPin(timeRemaining));

    pinEntry.setImeOptions(EditorInfo.IME_ACTION_DONE);
    pinEntry.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        ViewUtil.hideKeyboard(requireContext(), v);
        handlePinEntry();
        return true;
      }
      return false;
    });

    enableAndFocusPinEntry();

    pinButton.setOnClickListener((v) -> {
      ViewUtil.hideKeyboard(requireContext(), pinEntry);
      handlePinEntry();
    });

    keyboardToggle.setOnClickListener((v) -> {
      PinKeyboardType keyboardType = getPinEntryKeyboardType();

      updateKeyboard(keyboardType.getOther());
      keyboardToggle.setText(resolveKeyboardToggleText(keyboardType));
    });

    PinKeyboardType keyboardType = getPinEntryKeyboardType().getOther();
    keyboardToggle.setText(resolveKeyboardToggleText(keyboardType));

    disposables.bindTo(getViewLifecycleOwner().getLifecycle());
    viewModel = ViewModelProviders.of(requireActivity()).get(RegistrationViewModel.class);

    viewModel.getLockedTimeRemaining()
             .observe(getViewLifecycleOwner(), t -> timeRemaining = t);

    TokenData keyBackupCurrentToken = viewModel.getKeyBackupCurrentToken();

    if (keyBackupCurrentToken != null) {
      int triesRemaining = keyBackupCurrentToken.getTriesRemaining();
      if (triesRemaining <= 3) {
        int daysRemaining = getLockoutDays(timeRemaining);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.RegistrationLockFragment__not_many_tries_left)
            .setMessage(getTriesRemainingDialogMessage(triesRemaining, daysRemaining))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support, (dialog, which) -> sendEmailToSupport())
            .show();
      }

      if (triesRemaining < 5) {
        errorLabel.setText(requireContext().getResources().getQuantityString(R.plurals.RegistrationLockFragment__d_attempts_remaining, triesRemaining, triesRemaining));
      }
    }
  }

  private String getTriesRemainingDialogMessage(int triesRemaining, int daysRemaining) {
    Resources resources = requireContext().getResources();
    String    tries     = resources.getQuantityString(R.plurals.RegistrationLockFragment__you_have_d_attempts_remaining, triesRemaining, triesRemaining);
    String    days      = resources.getQuantityString(R.plurals.RegistrationLockFragment__if_you_run_out_of_attempts_your_account_will_be_locked_for_d_days, daysRemaining, daysRemaining);

    return tries + " " + days;
  }

  private PinKeyboardType getPinEntryKeyboardType() {
    boolean isNumeric = (pinEntry.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER;

    return isNumeric ? PinKeyboardType.NUMERIC : PinKeyboardType.ALPHA_NUMERIC;
  }

  private void handlePinEntry() {
    pinEntry.setEnabled(false);

    final String pin = pinEntry.getText().toString();

    int trimmedLength = pin.replace(" ", "").length();
    if (trimmedLength == 0) {
      Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show();
      enableAndFocusPinEntry();
      return;
    }

    if (trimmedLength < MINIMUM_PIN_LENGTH) {
      Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show();
      enableAndFocusPinEntry();
      return;
    }

    setSpinning(pinButton);

    Disposable verify = viewModel.verifyCodeAndRegisterAccountWithRegistrationLock(pin)
                                 .observeOn(AndroidSchedulers.mainThread())
                                 .subscribe(processor -> {
                                   if (processor.hasResult()) {
                                     handleSuccessfulPinEntry();
                                   } else if (processor.wrongPin()) {
                                     onIncorrectKbsRegistrationLockPin(processor.getToken());
                                   } else if (processor.isKbsLocked()) {
                                     onKbsAccountLocked();
                                   } else if (processor.rateLimit()) {
                                     onRateLimited();
                                   } else {
                                     Log.w(TAG, "Unable to verify code with registration lock", processor.getError());
                                     onError();
                                   }
                                 });

    disposables.add(verify);
  }

  public void onIncorrectKbsRegistrationLockPin(@NonNull TokenData tokenData) {
    cancelSpinning(pinButton);
    pinEntry.getText().clear();
    enableAndFocusPinEntry();

    viewModel.setKeyBackupTokenData(tokenData);

    int triesRemaining = tokenData.getTriesRemaining();

    if (triesRemaining == 0) {
      Log.w(TAG, "Account locked. User out of attempts on KBS.");
      onAccountLocked();
      return;
    }

    if (triesRemaining == 3) {
      int daysRemaining = getLockoutDays(timeRemaining);

      new MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.RegistrationLockFragment__incorrect_pin)
          .setMessage(getTriesRemainingDialogMessage(triesRemaining, daysRemaining))
          .setPositiveButton(android.R.string.ok, null)
          .show();
    }

    if (triesRemaining > 5) {
      errorLabel.setText(R.string.RegistrationLockFragment__incorrect_pin_try_again);
    } else {
      errorLabel.setText(requireContext().getResources().getQuantityString(R.plurals.RegistrationLockFragment__incorrect_pin_d_attempts_remaining, triesRemaining, triesRemaining));
      forgotPin.setVisibility(View.VISIBLE);
    }
  }


  public void onRateLimited() {
    cancelSpinning(pinButton);
    enableAndFocusPinEntry();

    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.RegistrationActivity_too_many_attempts)
        .setMessage(R.string.RegistrationActivity_you_have_made_too_many_incorrect_registration_lock_pin_attempts_please_try_again_in_a_day)
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }


  public void onKbsAccountLocked() {
    onAccountLocked();
  }


  public void onError() {
    cancelSpinning(pinButton);
    enableAndFocusPinEntry();

    Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
  }

  private void handleForgottenPin(long timeRemainingMs) {
    int lockoutDays = getLockoutDays(timeRemainingMs);
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.RegistrationLockFragment__forgot_your_pin)
        .setMessage(requireContext().getResources().getQuantityString(R.plurals.RegistrationLockFragment__for_your_privacy_and_security_there_is_no_way_to_recover, lockoutDays, lockoutDays))
        .setPositiveButton(android.R.string.ok, null)
        .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support, (dialog, which) -> sendEmailToSupport())
        .show();
  }

  private static int getLockoutDays(long timeRemainingMs) {
    return (int) TimeUnit.MILLISECONDS.toDays(timeRemainingMs) + 1;
  }

  private void onAccountLocked() {
    Navigation.findNavController(requireView()).navigate(RegistrationLockFragmentDirections.actionAccountLocked());
  }

  private void updateKeyboard(@NonNull PinKeyboardType keyboard) {
    boolean isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC;

    pinEntry.setInputType(isAlphaNumeric ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                                         : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

    pinEntry.getText().clear();
  }

  private @StringRes static int resolveKeyboardToggleText(@NonNull PinKeyboardType keyboard) {
    if (keyboard == PinKeyboardType.ALPHA_NUMERIC) {
      return R.string.RegistrationLockFragment__enter_alphanumeric_pin;
    } else {
      return R.string.RegistrationLockFragment__enter_numeric_pin;
    }
  }

  private void enableAndFocusPinEntry() {
    pinEntry.setEnabled(true);
    pinEntry.setFocusable(true);

    if (pinEntry.requestFocus()) {
      ServiceUtil.getInputMethodManager(pinEntry.getContext()).showSoftInput(pinEntry, 0);
    }
  }

  private void handleSuccessfulPinEntry() {
    SignalStore.pinValues().setKeyboardType(getPinEntryKeyboardType());

    SimpleTask.run(() -> {
      SignalStore.onboarding().clearAll();

      Stopwatch stopwatch = new Stopwatch("RegistrationLockRestore");

      ApplicationDependencies.getJobManager().runSynchronously(new StorageAccountRestoreJob(), StorageAccountRestoreJob.LIFESPAN);
      stopwatch.split("AccountRestore");

      ApplicationDependencies.getJobManager().runSynchronously(new StorageSyncJob(), TimeUnit.SECONDS.toMillis(10));
      stopwatch.split("ContactRestore");

      try {
        FeatureFlags.refreshSync();
      } catch (IOException e) {
        Log.w(TAG, "Failed to refresh flags.", e);
      }
      stopwatch.split("FeatureFlags");

      stopwatch.stop(TAG);

      return null;
    }, none -> {
      cancelSpinning(pinButton);
      Navigation.findNavController(requireView()).navigate(RegistrationLockFragmentDirections.actionSuccessfulRegistration());
    });
  }

  private void sendEmailToSupport() {
    int subject = isV1RegistrationLock ? R.string.RegistrationLockFragment__signal_registration_need_help_with_pin_for_android_v1_pin
                                       : R.string.RegistrationLockFragment__signal_registration_need_help_with_pin_for_android_v2_pin;

    String body = SupportEmailUtil.generateSupportEmailBody(requireContext(),
                                                            subject,
                                                            null,
                                                            null);
    CommunicationActions.openEmail(requireContext(),
                                   SupportEmailUtil.getSupportEmailAddress(requireContext()),
                                   getString(subject),
                                   body);
  }
}
