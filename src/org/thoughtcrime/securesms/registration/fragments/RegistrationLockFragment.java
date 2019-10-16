package org.thoughtcrime.securesms.registration.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.Navigation;

import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.registration.service.CodeVerificationRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationService;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;

import java.util.concurrent.TimeUnit;

public final class RegistrationLockFragment extends BaseRegistrationFragment {

  private EditText               pinEntry;
  private CircularProgressButton pinButton;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_lock, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.verify_header));

    pinEntry  = view.findViewById(R.id.pin);
    pinButton = view.findViewById(R.id.pinButton);

    View clarificationLabel = view.findViewById(R.id.clarification_label);
    View subHeader          = view.findViewById(R.id.verify_subheader);
    View pinForgotButton    = view.findViewById(R.id.forgot_button);

    String code = getModel().getTextCodeEntered();

    long timeRemaining = RegistrationLockFragmentArgs.fromBundle(requireArguments()).getTimeRemaining();

    pinForgotButton.setOnClickListener(v -> handleForgottenPin(timeRemaining));

    pinEntry.addTextChangedListener(new TextWatcher() {

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }

      @Override
      public void afterTextChanged(Editable s) {
        boolean matchesTextCode = s != null && s.toString().equals(code);
        clarificationLabel.setVisibility(matchesTextCode ? View.VISIBLE : View.INVISIBLE);
        subHeader.setVisibility(matchesTextCode ? View.INVISIBLE : View.VISIBLE);
      }
    });

    pinEntry.setImeOptions(EditorInfo.IME_ACTION_DONE);
    pinEntry.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        hideKeyboard(requireContext(), v);
        handlePinEntry();
        return true;
      }
      return false;
    });

    pinButton.setOnClickListener((v) -> {
      hideKeyboard(requireContext(), pinEntry);
      handlePinEntry();
    });
  }

  private void handlePinEntry() {
    final String pin = pinEntry.getText().toString();

    if (TextUtils.isEmpty(pin) || TextUtils.isEmpty(pin.replace(" ", ""))) {
      Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show();
      return;
    }

    RegistrationViewModel model               = getModel();
    RegistrationService   registrationService = RegistrationService.getInstance(model.getNumber().getE164Number(), model.getRegistrationSecret());

    setSpinning(pinButton);

    registrationService.verifyAccount(requireActivity(), model.getFcmToken(), model.getTextCodeEntered(), pin, new CodeVerificationRequest.VerifyCallback() {

        @Override
        public void onSuccessfulRegistration() {
          cancelSpinning(pinButton);

          Navigation.findNavController(requireView()).navigate(RegistrationLockFragmentDirections.actionSuccessfulRegistration());
        }

        @Override
        public void onIncorrectRegistrationLockPin(long timeRemaining) {
          cancelSpinning(pinButton);

          pinEntry.setText("");
          Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_registration_lock_pin, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onTooManyAttempts() {
          cancelSpinning(pinButton);

          new AlertDialog.Builder(requireContext())
                         .setTitle(R.string.RegistrationActivity_too_many_attempts)
                         .setMessage(R.string.RegistrationActivity_you_have_made_too_many_incorrect_registration_lock_pin_attempts_please_try_again_in_a_day)
                         .setPositiveButton(android.R.string.ok, null)
                         .show();
        }

        @Override
        public void onError() {
          cancelSpinning(pinButton);

          Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
        }
      });
  }

  private void handleForgottenPin(long timeRemainingMs) {
    new AlertDialog.Builder(requireContext())
                   .setTitle(R.string.RegistrationActivity_oh_no)
                   .setMessage(getString(R.string.RegistrationActivity_registration_of_this_phone_number_will_be_possible_without_your_registration_lock_pin_after_seven_days_have_passed, (TimeUnit.MILLISECONDS.toDays(timeRemainingMs) + 1)))
                   .setPositiveButton(android.R.string.ok, null)
                   .show();
  }
}
