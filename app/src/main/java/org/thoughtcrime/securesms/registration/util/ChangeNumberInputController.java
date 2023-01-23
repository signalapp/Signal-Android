package org.thoughtcrime.securesms.registration.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.LabeledEditText;
import org.thoughtcrime.securesms.registration.viewmodel.NumberViewState;

/**
 * Handle the logic and formatting of phone number input specifically for change number flows.
 */
public final class ChangeNumberInputController {

  private final Context         context;
  private final LabeledEditText countryCode;
  private final LabeledEditText number;
  private final boolean         lastInput;
  private final Callbacks       callbacks;

  private ArrayAdapter<String> countrySpinnerAdapter;
  private AsYouTypeFormatter countryFormatter;
  private boolean            isUpdating = true;

  public ChangeNumberInputController(@NonNull Context context,
                                     @NonNull LabeledEditText countryCode,
                                     @NonNull LabeledEditText number,
                                     @NonNull Spinner countrySpinner,
                                     boolean lastInput,
                                     @NonNull Callbacks callbacks)
  {
    this.context     = context;
    this.countryCode = countryCode;
    this.number      = number;
    this.lastInput   = lastInput;
    this.callbacks   = callbacks;

    initializeSpinner(countrySpinner);
    setUpNumberInput();

    this.countryCode.getInput().addTextChangedListener(new CountryCodeChangedListener());
    this.countryCode.getInput().setImeOptions(EditorInfo.IME_ACTION_NEXT);
  }

  private void setUpNumberInput() {
    EditText numberInput = number.getInput();

    numberInput.addTextChangedListener(new NumberChangedListener());

    number.setOnFocusChangeListener((v, hasFocus) -> {
      if (hasFocus) {
        callbacks.onNumberFocused();
      }
    });

    numberInput.setImeOptions(lastInput ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
    numberInput.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_NEXT) {
        callbacks.onNumberInputNext(v);
        return true;
      } else if (actionId == EditorInfo.IME_ACTION_DONE) {
        callbacks.onNumberInputDone(v);
        return true;
      }
      return false;
    });
  }

  @SuppressLint("ClickableViewAccessibility")
  private void initializeSpinner(@NonNull Spinner countrySpinner) {
    countrySpinnerAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item);
    countrySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    setCountryDisplay(context.getString(R.string.RegistrationActivity_select_your_country));

    countrySpinner.setAdapter(countrySpinnerAdapter);

    countrySpinner.setOnTouchListener((view, event) -> {
      if (event.getAction() == MotionEvent.ACTION_UP) {
        callbacks.onPickCountry(view);
      }
      return true;
    });

    countrySpinner.setOnKeyListener((view, keyCode, event) -> {
      if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_UP) {
        callbacks.onPickCountry(view);
        return true;
      }
      return false;
    });
  }

  public void updateNumber(@NonNull NumberViewState numberViewState) {
    int    countryCode       = numberViewState.getCountryCode();
    String countryCodeString = String.valueOf(countryCode);
    String number            = numberViewState.getNationalNumber();
    String regionDisplayName = numberViewState.getCountryDisplayName();

    isUpdating = true;

    setCountryDisplay(regionDisplayName);

    if (this.countryCode.getText() == null || !this.countryCode.getText().toString().equals(countryCodeString)) {
      this.countryCode.setText(countryCodeString);

      String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);
      setCountryFormatter(regionCode);
    }

    if (!justDigits(this.number.getText()).equals(number) && !TextUtils.isEmpty(number)) {
      this.number.setText(number);
    }

    isUpdating = false;
  }

  private String justDigits(@Nullable Editable text) {
    if (text == null) {
      return "";
    }

    StringBuilder justDigits = new StringBuilder();

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isDigit(c)) {
        justDigits.append(c);
      }
    }

    return justDigits.toString();
  }

  private void setCountryDisplay(@Nullable String regionDisplayName) {
    countrySpinnerAdapter.clear();
    if (regionDisplayName == null) {
      countrySpinnerAdapter.add(context.getString(R.string.RegistrationActivity_select_your_country));
    } else {
      countrySpinnerAdapter.add(regionDisplayName);
    }
  }

  private void setCountryFormatter(@Nullable String regionCode) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();

    countryFormatter = regionCode != null ? util.getAsYouTypeFormatter(regionCode) : null;

    reformatText(number.getText());
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

  private class NumberChangedListener implements TextWatcher {

    @Override
    public void afterTextChanged(Editable s) {
      String number = reformatText(s);

      if (number == null) return;

      if (!isUpdating) {
        callbacks.setNationalNumber(number);
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

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
        if (!isUpdating) {
          number.requestFocus();
        }

        int numberLength = number.getText().length();
        number.getInput().setSelection(numberLength, numberLength);
      }

      if (!isUpdating) {
        callbacks.setCountry(countryCode);
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  }

  public interface Callbacks {
    void onNumberFocused();

    void onNumberInputNext(@NonNull View view);

    void onNumberInputDone(@NonNull View view);

    void onPickCountry(@NonNull View view);

    void setNationalNumber(@NonNull String number);

    void setCountry(int countryCode);
  }
}
