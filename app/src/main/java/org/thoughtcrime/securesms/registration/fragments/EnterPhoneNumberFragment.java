package org.thoughtcrime.securesms.registration.fragments;

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.registration.service.RegistrationCodeRequest;
import org.thoughtcrime.securesms.registration.service.RegistrationService;
import org.thoughtcrime.securesms.registration.viewmodel.NumberViewState;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.Dialogs;

public final class EnterPhoneNumberFragment extends BaseRegistrationFragment  implements View.OnFocusChangeListener {

  private static final String TAG = Log.tag(EnterPhoneNumberFragment.class);

  private TextView mPhoneNumberEntry;
  private TextView mCountryCodeHeader;
  private EditText mPhoneNumberEdit;
  private TextView mPhoneNumberNext;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_enter_phone_number, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mPhoneNumberEntry = view.findViewById(R.id.phone_number_entry);
    mCountryCodeHeader = view.findViewById(R.id.country_code_header);
    mPhoneNumberEdit = view.findViewById(R.id.phone_number_input);
    mPhoneNumberNext = view.findViewById(R.id.phone_number_nav);

    mPhoneNumberEntry.setTextSize(24);
    mPhoneNumberEntry.setOnClickListener(view12 -> {
      mPhoneNumberEdit.setEnabled(true);
      mPhoneNumberEdit.requestFocus();
    });

    mPhoneNumberEdit.requestFocus();

    mPhoneNumberEdit.setOnKeyListener((view13, i, keyEvent) -> {
      if (mPhoneNumberEdit.isEnabled() && keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK) {
        mPhoneNumberEdit.setEnabled(false);
        return true;
      }
      if (KeyEvent.ACTION_DOWN == keyEvent.getAction()) {
        int index = mPhoneNumberEdit.getSelectionStart();
        switch (i) {
          case KeyEvent.KEYCODE_DPAD_UP:
            if (index == 0) {
              return false;
            }
            if (index > 0) {
              index--;
              mPhoneNumberEdit.setSelection(index);
              return true;
            }
          case KeyEvent.KEYCODE_DPAD_DOWN:
            if (index == mPhoneNumberEdit.getText().toString().length()) {
              return false;
            }
            if (index < mPhoneNumberEdit.getText().toString().length()) {
              index++;
              mPhoneNumberEdit.setSelection(index);
              return true;
            }
        }
      }
      return false;
    });
    mPhoneNumberEdit.addTextChangedListener(new NumberChangedListener());

    mPhoneNumberNext.setOnClickListener(view1 -> handleRegister(requireActivity()));

    mPhoneNumberEntry.setOnFocusChangeListener(this);
    mPhoneNumberEdit.setOnFocusChangeListener(this);
    mPhoneNumberNext.setOnFocusChangeListener(this);

    RegistrationViewModel model = getModel();
    NumberViewState number = model.getNumber();
    initNumber(number);
    if (model.hasCaptchaToken()) {
      handleRegister(requireContext());
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
      Navigation.findNavController(requireView()).navigate(EnterPhoneNumberFragmentDirections.actionEditProxy());
      return true;
    } else {
      return false;
    }
  }

  private void handleRegister(@NonNull Context context) {
    disableAllEntries();

    if (TextUtils.isEmpty(mCountryCodeHeader.getText())) {
      Toast.makeText(context, getString(R.string.RegistrationActivity_you_must_specify_your_country_code), Toast.LENGTH_LONG).show();
      enableAllEntries();
      return;
    }

    if (TextUtils.isEmpty(this.mPhoneNumberEdit.getText())) {
      Toast.makeText(context, getString(R.string.RegistrationActivity_you_must_specify_your_phone_number), Toast.LENGTH_LONG).show();
      enableAllEntries();
      return;
    }

    final NumberViewState number     = getModel().getNumber();
    final String          e164number = number.getE164Number();

    if (!number.isValid()) {
      Dialogs.showAlertDialog(context,
        getString(R.string.RegistrationActivity_invalid_number),
        String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid), e164number));
      enableAllEntries();
      return;
    }
    requestVerificationCode(e164number);
  }

  private void enableAllEntries() {
    mPhoneNumberEntry.setEnabled(true);
    mPhoneNumberEdit.setEnabled(true);
    mPhoneNumberNext.setEnabled(true);
  }

  private void disableAllEntries() {
    mPhoneNumberEntry.setEnabled(false);
    mPhoneNumberEdit.setEnabled(false);
    mPhoneNumberNext.setEnabled(false);
  }

  private void requestVerificationCode(String e164number) {
    RegistrationViewModel model   = getModel();
    String                captcha = model.getCaptchaToken();
    model.clearCaptchaResponse();

    NavController navController = Navigation.findNavController(mPhoneNumberNext);

    if (!model.getRequestLimiter().canRequest(RegistrationCodeRequest.Mode.SMS_WITHOUT_LISTENER, e164number, System.currentTimeMillis())) {
      Log.i(TAG, "Local rate limited");
      enableAllEntries();
      return;
    }

    RegistrationService registrationService = RegistrationService.getInstance(e164number, model.getRegistrationSecret());

    registrationService.requestVerificationCode(requireActivity(), RegistrationCodeRequest.Mode.SMS_WITHOUT_LISTENER, captcha,
      new RegistrationCodeRequest.SmsVerificationCodeCallback() {

        @Override
        public void onNeedCaptcha() {
          if (getContext() == null) {
            Log.i(TAG, "Got onNeedCaptcha response, but fragment is no longer attached.");
            return;
          }
          Log.i(TAG, "Need captcha!");
          navController.navigate(EnterPhoneNumberFragmentDirections.actionRequestCaptcha());
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
          Log.i(TAG, "Success RequestSent!");
          model.setFcmToken(fcmToken);
          model.markASuccessfulAttempt();
          navController.navigate(EnterPhoneNumberFragmentDirections.actionEnterVerificationCode());
          enableAllEntries();
          model.getRequestLimiter().onSuccessfulRequest(RegistrationCodeRequest.Mode.SMS_WITHOUT_LISTENER, e164number, System.currentTimeMillis());
          model.updateLimiter();
        }

        @Override
        public void onRateLimited() {
          enableAllEntries();
          model.getRequestLimiter().onUnsuccessfulRequest();
          model.updateLimiter();
        }

        @Override
        public void onError() {
          Log.e(TAG, "SmsVerificationCodeCallback onError()");
          Toast.makeText(mPhoneNumberNext.getContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
          enableAllEntries();
          model.getRequestLimiter().onUnsuccessfulRequest();
          model.updateLimiter();
        }
      });
  }

  private void initNumber(@NonNull NumberViewState numberViewState) {
    int    countryCode       = numberViewState.getCountryCode();
    String   number            = numberViewState.getNationalNumber();
    mCountryCodeHeader.setText(String.format("+%s", String.valueOf(countryCode)));
    if (!TextUtils.isEmpty(number)) {
      this.mPhoneNumberEdit.setText(String.valueOf(number));
      this.mPhoneNumberEdit.setSelection(mPhoneNumberEdit.getText().length());
    }
  }

  @Override
  public void onFocusChange(View view, boolean b) {
    int id = view.getId();
    switch (id) {
      case R.id.phone_number_entry:
      case R.id.country_code_header:
      case R.id.phone_number_input:
        mPhoneNumberEntry.setTextColor(b ? getResources().getColor(R.color.white_focus) : getResources().getColor(R.color.white_not_focus));
        mCountryCodeHeader.setTextColor(b ? getResources().getColor(R.color.white_focus) : getResources().getColor(R.color.white_not_focus));
        mPhoneNumberEdit.setTextColor(b ? getResources().getColor(R.color.white_focus) : getResources().getColor(R.color.white_not_focus));
        break;
    }
  }

  private class NumberChangedListener implements TextWatcher {

    @Override
    public void afterTextChanged(Editable s) {
      if (s.length() == 0) return;
      RegistrationViewModel model = getModel();
      model.setNationalNumber(s.toString());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }
  }

}
