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
import androidx.autofill.HintConstants;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.lock.v2.SvrConstants;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.registration.util.RegistrationUtil;
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate;
import org.thoughtcrime.securesms.restore.RestoreActivity;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

public class PinRestoreEntryFragment extends LoggingFragment {
  private static final String TAG = Log.tag(PinRestoreActivity.class);

  private static final int MINIMUM_PIN_LENGTH = 4;

  private EditText                       pinEntry;
  private View                           helpButton;
  private View                           skipButton;
  private CircularProgressMaterialButton pinButton;
  private TextView                       errorLabel;
  private MaterialButton                 keyboardToggle;
  private PinRestoreViewModel            viewModel;

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
    RegistrationViewDelegate.setDebugLogSubmitMultiTapView(root.findViewById(R.id.pin_restore_pin_title));

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

      keyboardToggle.setIconResource(keyboardType.getIconResource());

      updateKeyboard(keyboardType.getOther());
    });

    keyboardToggle.setIconResource(getPinEntryKeyboardType().getOther().getIconResource());
  }

  private void initViewModel() {
    viewModel = new ViewModelProvider(this).get(PinRestoreViewModel.class);

    viewModel.triesRemaining.observe(getViewLifecycleOwner(), this::presentTriesRemaining);
    viewModel.getEvent().observe(getViewLifecycleOwner(), this::presentEvent);
  }

  private void presentTriesRemaining(PinRestoreViewModel.TriesRemaining triesRemaining) {
    if (triesRemaining.hasIncorrectGuess()) {
      if (triesRemaining.getCount() == 1) {
        new MaterialAlertDialogBuilder(requireContext())
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
        new MaterialAlertDialogBuilder(requireContext())
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
        pinButton.cancelSpinning();
        pinEntry.getText().clear();
        enableAndFocusPinEntry();
        break;
      case PIN_TOO_SHORT:
        Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show();
        pinButton.cancelSpinning();
        pinEntry.getText().clear();
        enableAndFocusPinEntry();
        break;
      case PIN_INCORRECT:
        pinButton.cancelSpinning();
        pinEntry.getText().clear();
        enableAndFocusPinEntry();
        break;
      case PIN_LOCKED:
        onAccountLocked();
        break;
      case NETWORK_ERROR:
        Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
        pinButton.cancelSpinning();
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
    pinButton.setSpinning();
  }

  private void onNeedHelpClicked() {
    new MaterialAlertDialogBuilder(requireContext())
                   .setTitle(R.string.PinRestoreEntryFragment_need_help)
                   .setMessage(getString(R.string.PinRestoreEntryFragment_your_pin_is_a_d_digit_code, SvrConstants.MINIMUM_PIN_LENGTH))
                   .setPositiveButton(R.string.PinRestoreEntryFragment_create_new_pin, ((dialog, which) -> {
                     SvrRepository.onPinRestoreForgottenOrSkipped();
                     ((PinRestoreActivity) requireActivity()).navigateToPinCreation();
                   }))
                   .setNeutralButton(R.string.PinRestoreEntryFragment_contact_support, (dialog, which) -> {
                     String body = SupportEmailUtil.generateSupportEmailBody(requireContext(),
                                                                             R.string.PinRestoreEntryFragment_signal_registration_need_help_with_pin,
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
    new MaterialAlertDialogBuilder(requireContext())
                   .setTitle(R.string.PinRestoreEntryFragment_skip_pin_entry)
                   .setMessage(R.string.PinRestoreEntryFragment_if_you_cant_remember_your_pin)
                   .setPositiveButton(R.string.PinRestoreEntryFragment_create_new_pin, (dialog, which) -> {
                     SvrRepository.onPinRestoreForgottenOrSkipped();
                     ((PinRestoreActivity) requireActivity()).navigateToPinCreation();
                   })
                   .setNegativeButton(R.string.PinRestoreEntryFragment_cancel, null)
                   .show();
  }

  private void onAccountLocked() {
    SvrRepository.onPinRestoreForgottenOrSkipped();
    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), PinRestoreEntryFragmentDirections.actionAccountLocked());
  }

  private void handleSuccess() {
    pinButton.cancelSpinning();
    SignalStore.onboarding().clearAll();

    Activity activity = requireActivity();

    if (RemoteConfig.messageBackups() && !SignalStore.registration().hasCompletedRestore()) {
      final Intent transferOrRestore = RestoreActivity.getIntentForTransferOrRestore(activity);
      transferOrRestore.putExtra(PassphraseRequiredActivity.NEXT_INTENT_EXTRA, MainActivity.clearTop(requireContext()));
      startActivity(transferOrRestore);
    } else if (Recipient.self().getProfileName().isEmpty() || !AvatarHelper.hasAvatar(activity, Recipient.self().getId())) {
      final Intent main    = MainActivity.clearTop(activity);
      final Intent profile = CreateProfileActivity.getIntentForUserProfile(activity);

      profile.putExtra("next_intent", main);
      startActivity(profile);
    } else {
      RegistrationUtil.maybeMarkRegistrationComplete();
      AppDependencies.getJobManager().add(new ProfileUploadJob());
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

  private void enableAndFocusPinEntry() {
    pinEntry.setEnabled(true);
    pinEntry.setFocusable(true);
    ViewUtil.focusAndShowKeyboard(pinEntry);
  }
}
