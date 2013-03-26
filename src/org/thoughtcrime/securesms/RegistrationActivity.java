package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.google.android.gcm.GCMRegistrar;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.thoughtcrime.securesms.util.PhoneNumberFormatter;
import org.thoughtcrime.securesms.util.Util;

/**
 * The create account activity.  Kicks off an account creation event, then waits
 * the server to respond with a challenge via SMS, receives the challenge, and
 * verifies it with the server.
 *
 * @author Moxie Marlinspike
 *
 */

public class RegistrationActivity extends SherlockActivity {

  private static final int PICK_COUNTRY = 1;

  private AsYouTypeFormatter countryFormatter;
  private ArrayAdapter<String> countrySpinnerAdapter;
  private Spinner countrySpinner;
  private TextView countryCode;
  private TextView             number;
  private Button createButton;


  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.registration_activity);

    ActionBar actionBar = this.getSupportActionBar();
    actionBar.setTitle("Connect With TextSecure");

    initializeResources();
    initializeNumber();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PICK_COUNTRY && resultCode == RESULT_OK && data != null) {
      this.countryCode.setText(data.getIntExtra("country_code", 1)+"");
      setCountryDisplay(data.getStringExtra("country_name"));
      setCountryFormatter(data.getIntExtra("country_code", 1));
    }
  }

  private void initializeResources() {
    this.countrySpinner = (Spinner)findViewById(R.id.country_spinner);
    this.countryCode    = (TextView)findViewById(R.id.country_code);
    this.number         = (TextView)findViewById(R.id.number);
    this.createButton   = (Button)findViewById(R.id.registerButton);

    this.countrySpinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
    this.countrySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    setCountryDisplay("Select Your Country");

    this.countrySpinner.setAdapter(this.countrySpinnerAdapter);
    this.countrySpinner.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
          Intent intent = new Intent(RegistrationActivity.this, CountrySelectionActivity.class);
          startActivityForResult(intent, PICK_COUNTRY);
        }
        return true;
      }
    });

    this.countryCode.addTextChangedListener(new CountryCodeChangedListener());
    this.number.addTextChangedListener(new NumberChangedListener());
    this.createButton.setOnClickListener(new CreateButtonListener());
  }


  private void initializeNumber() {
    String localNumber = ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE))
        .getLine1Number();

    if (!Util.isEmpty(localNumber) && !localNumber.startsWith("+")) {
      if (localNumber.length() == 10) localNumber = "+1" + localNumber;
      else                            localNumber = "+"  + localNumber;
    }

    try {
      if (!Util.isEmpty(localNumber)) {
        PhoneNumberUtil numberUtil                = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber localNumberObject = numberUtil.parse(localNumber, null);

        if (localNumberObject != null) {
          this.countryCode.setText(localNumberObject.getCountryCode()+"");
          this.number.setText(localNumberObject.getNationalNumber()+"");
        }
      }
    } catch (NumberParseException npe) {
      Log.w("CreateAccountActivity", npe);
    }
  }

  private void setCountryDisplay(String value) {
    this.countrySpinnerAdapter.clear();
    this.countrySpinnerAdapter.add(value);
  }

  private void setCountryFormatter(int countryCode) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    String regionCode    = util.getRegionCodeForCountryCode(countryCode);

    if (regionCode == null) {
      this.countryFormatter = null;
    } else {
      this.countryFormatter = util.getAsYouTypeFormatter(regionCode);
    }
  }

  private String getConfiguredE164Number() {
    return PhoneNumberFormatter.formatE164(countryCode.getText().toString(),
                                           number.getText().toString());
  }

  private class CreateButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      final RegistrationActivity self = RegistrationActivity.this;

      if (Util.isEmpty(countryCode.getText())) {
        Toast.makeText(self, "You must specify your country code",
                       Toast.LENGTH_LONG).show();
        return;
      }

      if (Util.isEmpty(number.getText())) {
        Toast.makeText(self, "You must specify your phone number",
                       Toast.LENGTH_LONG).show();
        return;
      }

      final String e164number = getConfiguredE164Number();

      if (!PhoneNumberFormatter.isValidNumber(e164number)) {
        Util.showAlertDialog(self,
                             "Invalid number",
                             String.format("The number you specified (%s) is invalid.", e164number));
        return;
      }

      try {
        GCMRegistrar.checkDevice(self);
      } catch (UnsupportedOperationException uoe) {
        Util.showAlertDialog(self, "Unsupported", "Sorry, this device is not supported for data messaging. Devices running versions of Android older than 4.0 must have a registered Google Account.  Devices running Android 4.0 or newer do not require a Google Account, but must have the Play Store app installed.");
        return;
      }

      AlertDialog.Builder dialog = new AlertDialog.Builder(self);
      dialog.setMessage(String.format("We will now verify that the following number is associated with this device:\n\n%s\n\nIs this number correct, or would you like to edit it before continuing?",
                                      PhoneNumberFormatter.getInternationalFormatFromE164(e164number)));
      dialog.setPositiveButton("Continue",
                               new DialogInterface.OnClickListener() {
                                 @Override
                                 public void onClick(DialogInterface dialog, int which) {
                                   Intent intent = new Intent(self, RegistrationProgressActivity.class);
                                   intent.putExtra("e164number", e164number);
                                   startActivity(intent);
                                   finish();
                                 }
                               });
      dialog.setNegativeButton("Edit", null);
      dialog.show();
    }
  }

  private class CountryCodeChangedListener implements TextWatcher {
    @Override
    public void afterTextChanged(Editable s) {
      if (Util.isEmpty(s)) {
        setCountryDisplay("Select your country");
        countryFormatter = null;
        return;
      }

      int countryCode   = Integer.parseInt(s.toString());
      String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);
      setCountryFormatter(countryCode);
      setCountryDisplay(PhoneNumberFormatter.getRegionDisplayName(regionCode));

      if (!Util.isEmpty(regionCode) && !regionCode.equals("ZZ")) {
        number.requestFocus();
      }
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
      if (countryFormatter == null)
        return;

      if (Util.isEmpty(s))
        return;

      countryFormatter.clear();

      String number          = s.toString().replaceAll("[^\\d.]", "");
      String formattedNumber = null;

      for (int i=0;i<number.length();i++) {
        formattedNumber = countryFormatter.inputDigit(number.charAt(i));
      }

      if (!s.toString().equals(formattedNumber)) {
        s.replace(0, s.length(), formattedNumber);
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }
  }
}
