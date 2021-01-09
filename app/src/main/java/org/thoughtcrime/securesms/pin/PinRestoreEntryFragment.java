package org.thoughtcrime.securesms.pin;

import android.app.Activity;
import android.content.Intent;
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
import androidx.autofill.HintConstants;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import com.dd.CircularProgressButton;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.KbsConstants;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.registration.RegistrationUtil;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

public class PinRestoreEntryFragment extends LoggingFragment {
  private static final String TAG = Log.tag(PinRestoreActivity.class);

  private static final int MINIMUM_PIN_LENGTH = 4;

  private EditText               pinEntry;
  private View                   helpButton;
  private View                   skipButton;
  private CircularProgressButton pinButton;
  private TextView               errorLabel;
  private TextView               keyboardToggle;
  private PinRestoreViewModel    viewModel;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.pin_restore_entry_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initViews(view);
    initViewModel();
  }

  private void initViews(@NonNull View root) {
    pinEntry       = root.findViewById(R.id.pin_restore_pin_input);
    pinButton      = root.findViewById(R.id.pin_restore_pin_confirm);
    errorLabel     = root.findViewById(R.id.pin_restore_pin_input_label);
    keyboardToggle = root.findViewById(R.id.pin_restore_keyboard_toggle);
    helpButton     = root.findViewById(R.id.pin_restore_forgot_pin);
    skipButton     = root.findViewById(R.id.pin_restore_skip_button);

    helpButton.setVisibility(View.GONE);
    helpButton.setOnClickListener(v -> onNeedHelpClicked());

    skipButton.setOnClickListener(v -> onSkipClicked());

    pinEntry.setImeOptions(EditorInfo.IME_ACTION_DONE);
    pinEntry.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        ViewUtil.hideKeyboard(requireContext(), v);
        onPinSubmitted();
        return true;
      }
      return false;
    });
    ViewCompat.setAutofillHints(pinEntry, HintConstants.AUTOFILL_HINT_PASSWORD);

    enableAndFocusPinEntry();

    pinButton.setOnClickListener((v) -> {
      ViewUtil.hideKeyboard(requireContext(), pinEntry);
      onPinSubmitted();
    });

    keyboardToggle.setOnClickListener((v) -> {
      PinKeyboardType keyboardType = getPinEntryKeyboardType();

      updateKeyboard(keyboardType.getOther());
      keyboardToggle.setText(resolveKeyboardToggleText(keyboardType));
    });

    PinKeyboardType keyboardType = getPinEntryKeyboardType().getOther();
    keyboardToggle.setText(resolveKeyboardToggleText(keyboardType));
  }

  private void initViewModel() {
    viewModel = ViewModelProviders.of(this).get(PinRestoreViewModel.class);

    viewModel.getTriesRemaining().observe(this, this::presentTriesRemaining);
    viewModel.getEvent().observe(this, this::presentEvent);
  }

  private void presentTriesRemaining(PinRestoreViewModel.TriesRemaining triesRemaining) {
    if (triesRemaining.hasIncorrectGuess()) {
      if (triesRemaining.getCount() == 1) {
        new AlertDialog.Builder(requireContext())
                       .setTitle(R.string.PinRestoreEntryFragment_incorrect_pin)
                       .setMessage(getResources().getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, triesRemaining.getCount(), triesRemaining.getCount()))
                       .setPositiveButton(android.R.string.ok, null)
                       .show();
      }

      errorLabel.setText(R.string.PinRestoreEntryFragment_incorrect_pin);
      helpButton.setVisibility(View.VISIBLE);
    } else {
      if (triesRemaining.getCount() == 1) {
        helpButton.setVisibility(View.VISIBLE);
        new AlertDialog.Builder(requireContext())
                       .setMessage(getResources().getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, triesRemaining.getCount(), triesRemaining.getCount()))
                       .setPositiveButton(android.R.string.ok, null)
                       .show();
      }
    }

    if (triesRemaining.getCount() == 0) {
      Log.w(TAG, "Account locked. User out of attempts on KBS.");
      onAccountLocked();
    }
  }

  private void presentEvent(@NonNull PinRestoreViewModel.Event event) {
    switch (event) {
      case SUCCESS:
        handleSuccess();
        break;
      case EMPTY_PIN:
        Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show();
        cancelSpinning(pinButton);
        pinEntry.getText().clear();
        enableAndFocusPinEntry();
        break;
      case PIN_TOO_SHORT:
        Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show();
        cancelSpinning(pinButton);
        pinEntry.getText().clear();
        enableAndFocusPinEntry();
        break;
      case PIN_INCORRECT:
        cancelSpinning(pinButton);
        pinEntry.getText().clear();
        enableAndFocusPinEntry();
        break;
      case PIN_LOCKED:
        onAccountLocked();
        break;
      case NETWORK_ERROR:
        Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
        cancelSpinning(pinButton);
        pinEntry.setEnabled(true);
        enableAndFocusPinEntry();
        break;
    }
  }

  private PinKeyboardType getPinEntryKeyboardType() {
    boolean isNumeric = (pinEntry.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER;

    return isNumeric ? PinKeyboardType.NUMERIC : PinKeyboardType.ALPHA_NUMERIC;
  }

  private void onPinSubmitted() {
    pinEntry.setEnabled(false);
    viewModel.onPinSubmitted(pinEntry.getText().toString(), getPinEntryKeyboardType());
    setSpinning(pinButton);
  }

  private void onNeedHelpClicked() {
    new AlertDialog.Builder(requireContext())
                   .setTitle(R.string.PinRestoreEntryFragment_need_help)
                   .setMessage(getString(R.string.PinRestoreEntryFragment_your_pin_is_a_d_digit_code, KbsConstants.MINIMUM_PIN_LENGTH))
                   .setPositiveButton(R.string.PinRestoreEntryFragment_create_new_pin, ((dialog, which) -> {
                     PinState.onPinRestoreForgottenOrSkipped();
                     ((PinRestoreActivity) requireActivity()).navigateToPinCreation();
                   }))
                   .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support, (dialog, which) -> {
                     String body = SupportEmailUtil.generateSupportEmailBody(requireContext(),
                                                                             getString(R.string.PinRestoreEntryFragment_signal_registration_need_help_with_pin),
                                                                             null,
                                                                             null);
                     CommunicationActions.openEmail(requireContext(),
                                                    SupportEmailUtil.getSupportEmailAddress(requireContext()),
                                                    getString(R.string.PinRestoreEntryFragment_signal_registration_need_help_with_pin),
                                                    body);
                   })
                   .setNegativeButton(R.string.PinRestoreEntryFragment_cancel, null)
                   .show();
  }

  private void onSkipClicked() {
    new AlertDialog.Builder(requireContext())
                   .setTitle(R.string.PinRestoreEntryFragment_skip_pin_entry)
                   .setMessage(R.string.PinRestoreEntryFragment_if_you_cant_remember_your_pin)
                   .setPositiveButton(R.string.PinRestoreEntryFragment_create_new_pin, (dialog, which) -> {
                     PinState.onPinRestoreForgottenOrSkipped();
                     ((PinRestoreActivity) requireActivity()).navigateToPinCreation();
                   })
                   .setNegativeButton(R.string.PinRestoreEntryFragment_cancel, null)
                   .show();
  }

  private void onAccountLocked() {
    Navigation.findNavController(requireView()).navigate(PinRestoreEntryFragmentDirections.actionAccountLocked());
  }

  private void handleSuccess() {
    cancelSpinning(pinButton);
    SignalStore.onboarding().clearAll();

    Activity activity = requireActivity();

    if (Recipient.self().getProfileName().isEmpty() || !AvatarHelper.hasAvatar(activity, Recipient.self().getId())) {
      final Intent main    = MainActivity.clearTop(activity);
      final Intent profile = EditProfileActivity.getIntentForUserProfile(activity);

      profile.putExtra("next_intent", main);
      startActivity(profile);
    } else {
      RegistrationUtil.maybeMarkRegistrationComplete(requireContext());
      ApplicationDependencies.getJobManager().add(new ProfileUploadJob());
      startActivity(MainActivity.clearTop(activity));
    }

    activity.finish();
  }

  private void updateKeyboard(@NonNull PinKeyboardType keyboard) {
    boolean isAlphaNumeric = keyboard == PinKeyboardType.ALPHA_NUMERIC;

    pinEntry.setInputType(isAlphaNumeric ? InputType.TYPE_CLASS_TEXT   | InputType.TYPE_TEXT_VARIATION_PASSWORD
                                         : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

    pinEntry.getText().clear();
  }

  private @StringRes static int resolveKeyboardToggleText(@NonNull PinKeyboardType keyboard) {
    if (keyboard == PinKeyboardType.ALPHA_NUMERIC) {
      return R.string.PinRestoreEntryFragment_enter_alphanumeric_pin;
    } else {
      return R.string.PinRestoreEntryFragment_enter_numeric_pin;
    }
  }

  private void enableAndFocusPinEntry() {
    pinEntry.setEnabled(true);
    pinEntry.setFocusable(true);

    if (pinEntry.requestFocus()) {
      ServiceUtil.getInputMethodManager(pinEntry.getContext()).showSoftInput(pinEntry, 0);
    }
  }

  private static void setSpinning(@Nullable CircularProgressButton button) {
    if (button != null) {
      button.setClickable(false);
      button.setIndeterminateProgressMode(true);
      button.setProgress(50);
    }
  }

  private static void cancelSpinning(@Nullable CircularProgressButton button) {
    if (button != null) {
      button.setProgress(0);
      button.setIndeterminateProgressMode(false);
      button.setClickable(true);
    }
  }
}
