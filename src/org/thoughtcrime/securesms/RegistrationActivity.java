package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;

/**
 * The register account activity.  Prompts ths user for their registration information
 * and begins the account registration process.
 *
 * @author Moxie Marlinspike
 *
 */
public class RegistrationActivity extends BaseActionBarActivity {

  private static final int PICK_COUNTRY = 1;

  private AsYouTypeFormatter   countryFormatter;
  private ArrayAdapter<String> countrySpinnerAdapter;
  private Spinner              countrySpinner;
  private TextView             countryCode;
  private TextView             number;
  private Button               createButton;
  private Button               skipButton;

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.registration_activity);

    getSupportActionBar().setTitle(getString(R.string.RegistrationActivity_connect_with_signal));

    initializeResources();
    initializeSpinner();
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
    this.masterSecret   = getIntent().getParcelableExtra("master_secret");
    this.countrySpinner = (Spinner)findViewById(R.id.country_spinner);
    this.countryCode    = (TextView)findViewById(R.id.country_code);
    this.number         = (TextView)findViewById(R.id.number);
    this.createButton   = (Button)findViewById(R.id.registerButton);
    this.skipButton     = (Button)findViewById(R.id.skipButton);

    this.countryCode.addTextChangedListener(new CountryCodeChangedListener());
    this.number.addTextChangedListener(new NumberChangedListener());
    this.createButton.setOnClickListener(new CreateButtonListener());
    this.skipButton.setOnClickListener(new CancelButtonListener());

    if (getIntent().getBooleanExtra("cancel_button", false)) {
      this.skipButton.setVisibility(View.VISIBLE);
    } else {
      this.skipButton.setVisibility(View.INVISIBLE);
    }

    findViewById(R.id.twilio_shoutout).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setData(Uri.parse("https://twilio.com"));
        startActivity(intent);
      }
    });
  }

  private void initializeSpinner() {
    this.countrySpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
    this.countrySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    setCountryDisplay(getString(R.string.RegistrationActivity_select_your_country));

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
  }

  private void initializeNumber() {
    PhoneNumberUtil numberUtil  = PhoneNumberUtil.getInstance();
    String          localNumber = Util.getDeviceE164Number(this);

    try {
      if (!TextUtils.isEmpty(localNumber)) {
        Phonenumber.PhoneNumber localNumberObject = numberUtil.parse(localNumber, null);

        if (localNumberObject != null) {
          this.countryCode.setText(localNumberObject.getCountryCode()+"");
          this.number.setText(localNumberObject.getNationalNumber()+"");
        }
      } else {
        String simCountryIso = ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE)).getSimCountryIso();

        if (!TextUtils.isEmpty(simCountryIso)) {
          this.countryCode.setText(numberUtil.getCountryCodeForRegion(simCountryIso.toUpperCase())+"");
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

    if (regionCode == null) this.countryFormatter = null;
    else                    this.countryFormatter = util.getAsYouTypeFormatter(regionCode);
  }

  private String getConfiguredE164Number() {
    return PhoneNumberFormatter.formatE164(countryCode.getText().toString(),
                                           number.getText().toString());
  }

  private class CreateButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      final RegistrationActivity self = RegistrationActivity.this;

      if (TextUtils.isEmpty(countryCode.getText())) {
        Toast.makeText(self,
                       getString(R.string.RegistrationActivity_you_must_specify_your_country_code),
                       Toast.LENGTH_LONG).show();
        return;
      }

      if (TextUtils.isEmpty(number.getText())) {
        Toast.makeText(self,
                       getString(R.string.RegistrationActivity_you_must_specify_your_phone_number),
                       Toast.LENGTH_LONG).show();
        return;
      }

      final String e164number = getConfiguredE164Number();

      if (!PhoneNumberFormatter.isValidNumber(e164number)) {
        Dialogs.showAlertDialog(self,
                             getString(R.string.RegistrationActivity_invalid_number),
                             String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid),
                                           e164number));
        return;
      }

      int gcmStatus = GooglePlayServicesUtil.isGooglePlayServicesAvailable(self);

      if (gcmStatus != ConnectionResult.SUCCESS) {
        if (GooglePlayServicesUtil.isUserRecoverableError(gcmStatus)) {
          GooglePlayServicesUtil.getErrorDialog(gcmStatus, self, 9000).show();
        } else {
          Dialogs.showAlertDialog(self, getString(R.string.RegistrationActivity_unsupported),
                                  getString(R.string.RegistrationActivity_sorry_this_device_is_not_supported_for_data_messaging));
        }
      }

      AlertDialogWrapper.Builder dialog = new AlertDialogWrapper.Builder(self);
      dialog.setTitle(PhoneNumberFormatter.getInternationalFormatFromE164(e164number));
      dialog.setMessage(R.string.RegistrationActivity_we_will_now_verify_that_the_following_number_is_associated_with_your_device_s);
      dialog.setPositiveButton(getString(R.string.RegistrationActivity_continue),
                               new DialogInterface.OnClickListener() {
                                 @Override
                                 public void onClick(DialogInterface dialog, int which) {
                                   Intent intent = new Intent(self, RegistrationProgressActivity.class);
                                   intent.putExtra("e164number", e164number);
                                   intent.putExtra("master_secret", masterSecret);
                                   startActivity(intent);
                                   finish();
                                 }
                               });
      dialog.setNegativeButton(getString(R.string.RegistrationActivity_edit), null);
      dialog.show();
    }
  }

  private class CountryCodeChangedListener implements TextWatcher {
    @Override
    public void afterTextChanged(Editable s) {
      if (TextUtils.isEmpty(s)) {
        setCountryDisplay(getString(R.string.RegistrationActivity_select_your_country));
        countryFormatter = null;
        return;
      }

      int countryCode   = Integer.parseInt(s.toString());
      String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);

      setCountryFormatter(countryCode);
      setCountryDisplay(PhoneNumberFormatter.getRegionDisplayName(regionCode));

      if (!TextUtils.isEmpty(regionCode) && !regionCode.equals("ZZ")) {
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

      if (TextUtils.isEmpty(s))
        return;

      countryFormatter.clear();

      String number          = s.toString().replaceAll("[^\\d.]", "");
      String formattedNumber = null;

      for (int i=0;i<number.length();i++) {
        formattedNumber = countryFormatter.inputDigit(number.charAt(i));
      }

      if (formattedNumber != null && !s.toString().equals(formattedNumber)) {
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

  private class CancelButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      TextSecurePreferences.setPromptedPushRegistration(RegistrationActivity.this, true);
      Intent nextIntent = getIntent().getParcelableExtra("next_intent");

      if (nextIntent == null) {
        nextIntent = new Intent(RegistrationActivity.this, ConversationListActivity.class);
      }

      startActivity(nextIntent);
      finish();
    }
  }
}
