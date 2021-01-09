package org.thoughtcrime.securesms.registration.fragments;

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
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.Navigation;

import com.dd.CircularProgressButton;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.StorageAccountRestoreJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.pin.PinRestoreRepository.TokenData;
import org.thoughtcrime.securesms.registration.service.CodeVerificationRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationService;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.util.concurrent.TimeUnit;

public final class RegistrationLockFragment extends BaseRegistrationFragment {

  private static final String TAG = Log.tag(RegistrationLockFragment.class);

  /** Applies to both V1 and V2 pins, because some V2 pins may have been migrated from V1. */
  private static final int MINIMUM_PIN_LENGTH = 4;

  private EditText               pinEntry;
  private View                   forgotPin;
  private CircularProgressButton pinButton;
  private TextView               errorLabel;
  private TextView               keyboardToggle;
  private long                   timeRemaining;

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

    timeRemaining = args.getTimeRemaining();

    if (args.getIsV1RegistrationLock()) {
      keyboardToggle.setVisibility(View.GONE);
    }

    forgotPin.setVisibility(View.GONE);
    forgotPin.setOnClickListener(v -> handleForgottenPin(timeRemaining));

    pinEntry.setImeOptions(EditorInfo.IME_ACTION_DONE);
    pinEntry.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        hideKeyboard(requireContext(), v);
        handlePinEntry();
        return true;
      }
      return false;
    });

    enableAndFocusPinEntry();

    pinButton.setOnClickListener((v) -> {
      hideKeyboard(requireContext(), pinEntry);
      handlePinEntry();
    });

    keyboardToggle.setOnClickListener((v) -> {
      PinKeyboardType keyboardType = getPinEntryKeyboardType();

      updateKeyboard(keyboardType.getOther());
      keyboardToggle.setText(resolveKeyboardToggleText(keyboardType));
    });

    PinKeyboardType keyboardType = getPinEntryKeyboardType().getOther();
    keyboardToggle.setText(resolveKeyboardToggleText(keyboardType));

    getModel().getLockedTimeRemaining()
              .observe(getViewLifecycleOwner(), t -> timeRemaining = t);

    TokenData keyBackupCurrentToken = getModel().getKeyBackupCurrentToken();

    if (keyBackupCurrentToken != null) {
      int triesRemaining = keyBackupCurrentToken.getTriesRemaining();
      if (triesRemaining <= 3) {
        int daysRemaining = getLockoutDays(timeRemaining);

        new AlertDialog.Builder(requireContext())
                       .setTitle(R.string.RegistrationLockFragment__not_many_tries_left)
                       .setMessage(getTriesRemainingDialogMessage(triesRemaining, daysRemaining))
                       .setPositiveButton(android.R.string.ok, null)
                       .show();
      }

      if (triesRemaining < 5) {
        errorLabel.setText(requireContext().getResources().getQuantityString(R.plurals.RegistrationLockFragment__d_attempts_remaining, triesRemaining, triesRemaining));
      }
    }
  }

  private String getTriesRemainingDialogMessage(int triesRemaining, int daysRemaining) {
    Resources resources = requireContext().getResources();
    String tries        = resources.getQuantityString(R.plurals.RegistrationLockFragment__you_have_d_attempts_remaining, triesRemaining, triesRemaining);
    String days         = resources.getQuantityString(R.plurals.RegistrationLockFragment__if_you_run_out_of_attempts_your_account_will_be_locked_for_d_days, daysRemaining, daysRemaining);

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
      return;
    }

    if (trimmedLength < MINIMUM_PIN_LENGTH) {
      Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show();
      return;
    }

    RegistrationViewModel model                   = getModel();
    RegistrationService   registrationService     = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());
    TokenData             tokenData               = model.getKeyBackupCurrentToken();

    setSpinning(pinButton);

    registrationService.verifyAccount(requireActivity(),
                                      model.getFcmToken(),
                                      model.getTextCodeEntered(),
                                      pin,
                                      tokenData,

      new CodeVerificationRequest.VerifyCallback() {

        @Override
        public void onSuccessfulRegistration() {
          handleSuccessfulPinEntry();
        }

        @Override
        public void onV1RegistrationLockPinRequiredOrIncorrect(long timeRemaining) {
          getModel().setLockedTimeRemaining(timeRemaining);

          cancelSpinning(pinButton);
          pinEntry.getText().clear();
          enableAndFocusPinEntry();

          errorLabel.setText(R.string.RegistrationLockFragment__incorrect_pin);
        }

        @Override
        public void onKbsRegistrationLockPinRequired(long timeRemaining, @NonNull TokenData kbsTokenData, @NonNull String kbsStorageCredentials) {
          throw new AssertionError("Not expected after a pin guess");
        }

        @Override
        public void onIncorrectKbsRegistrationLockPin(@NonNull TokenData tokenData) {
          cancelSpinning(pinButton);
          pinEntry.getText().clear();
          enableAndFocusPinEntry();

          model.setKeyBackupTokenData(tokenData);

          int triesRemaining = tokenData.getTriesRemaining();

          if (triesRemaining == 0) {
            Log.w(TAG, "Account locked. User out of attempts on KBS.");
            onAccountLocked();
            return;
          }

          if (triesRemaining == 3) {
            int daysRemaining = getLockoutDays(timeRemaining);

            new AlertDialog.Builder(requireContext())
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

        @Override
        public void onRateLimited() {
          cancelSpinning(pinButton);
          enableAndFocusPinEntry();

          new AlertDialog.Builder(requireContext())
                         .setTitle(R.string.RegistrationActivity_too_many_attempts)
                         .setMessage(R.string.RegistrationActivity_you_have_made_too_many_incorrect_registration_lock_pin_attempts_please_try_again_in_a_day)
                         .setPositiveButton(android.R.string.ok, null)
                         .show();
        }

        @Override
        public void onKbsAccountLocked(@Nullable Long timeRemaining) {
          if (timeRemaining != null) {
            model.setLockedTimeRemaining(timeRemaining);
          }

          onAccountLocked();
        }

        @Override
        public void onError() {
          cancelSpinning(pinButton);
          enableAndFocusPinEntry();

          Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
        }
      });
  }

  private void handleForgottenPin(long timeRemainingMs) {
    int lockoutDays = getLockoutDays(timeRemainingMs);
    new AlertDialog.Builder(requireContext())
                   .setTitle(R.string.RegistrationLockFragment__forgot_your_pin)
                   .setMessage(requireContext().getResources().getQuantityString(R.plurals.RegistrationLockFragment__for_your_privacy_and_security_there_is_no_way_to_recover, lockoutDays, lockoutDays))
                   .setPositiveButton(android.R.string.ok, null)
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

    pinEntry.setInputType(isAlphaNumeric ? InputType.TYPE_CLASS_TEXT   | InputType.TYPE_TEXT_VARIATION_PASSWORD
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

    long startTime = System.currentTimeMillis();
    SimpleTask.run(() -> {
      SignalStore.onboarding().clearAll();
      return ApplicationDependencies.getJobManager().runSynchronously(new StorageAccountRestoreJob(), StorageAccountRestoreJob.LIFESPAN);
    }, result -> {
      long elapsedTime = System.currentTimeMillis() - startTime;

      if (result.isPresent()) {
        Log.i(TAG, "Storage Service account restore completed: " + result.get().name() + ". (Took " + elapsedTime + " ms)");
      } else {
        Log.i(TAG, "Storage Service account restore failed to complete in the allotted time. (" + elapsedTime + " ms elapsed)");
      }
      cancelSpinning(pinButton);
      Navigation.findNavController(requireView()).navigate(RegistrationLockFragmentDirections.actionSuccessfulRegistration());
    });
  }
}
