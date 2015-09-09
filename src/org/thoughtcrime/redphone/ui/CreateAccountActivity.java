///*
// * Copyright (C) 2011 Whisper Systems
// *
// * This program is free software: you can redistribute it and/or modify
// * it under the terms of the GNU General Public License as published by
// * the Free Software Foundation, either version 3 of the License, or
// * (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with this program.  If not, see <http://www.gnu.org/licenses/>.
// */
//
//package org.thoughtcrime.redphone.ui;
//
//import android.app.AlertDialog;
//import android.content.Context;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.os.Bundle;
//import android.telephony.TelephonyManager;
//import android.text.Editable;
//import android.text.TextWatcher;
//import android.util.Log;
//import android.view.MotionEvent;
//import android.view.View;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.Spinner;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import com.actionbarsherlock.app.ActionBar;
//import com.actionbarsherlock.app.SherlockActivity;
//import com.actionbarsherlock.view.Menu;
//import com.actionbarsherlock.view.MenuInflater;
//import com.actionbarsherlock.view.MenuItem;
//import com.google.i18n.phonenumbers.AsYouTypeFormatter;
//import com.google.i18n.phonenumbers.NumberParseException;
//import com.google.i18n.phonenumbers.PhoneNumberUtil;
//import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
//
//import org.thoughtcrime.redphone.ApplicationContext;
//import org.thoughtcrime.redphone.R;
//import org.thoughtcrime.redphone.util.PhoneNumberFormatter;
//import org.thoughtcrime.redphone.util.Util;
//
///**
// * The create account activity.  Kicks off an account creation event, then waits
// * the server to respond with a challenge via SMS, receives the challenge, and
// * verifies it with the server.
// *
// * @author Moxie Marlinspike
// *
// */
//
//public class CreateAccountActivity extends SherlockActivity {
//
//  private static final int PICK_COUNTRY = 1;
//
//  private AsYouTypeFormatter countryFormatter;
//  private ArrayAdapter<String> countrySpinnerAdapter;
//  private Spinner countrySpinner;
//  private TextView countryCode;
//  private TextView number;
//  private Button createButton;
//
//
//  @Override
//  public void onCreate(Bundle icicle) {
//    super.onCreate(icicle);
//    setContentView(R.layout.create_account);
//
//    ActionBar actionBar = this.getSupportActionBar();
//    actionBar.setTitle(R.string.CreateAccountActivity_register_your_redphone);
//
//    initializeResources();
//    initializeNumber();
//  }
//
//  @Override
//  public boolean onCreateOptionsMenu(Menu menu) {
//    MenuInflater inflater = this.getSupportMenuInflater();
//    inflater.inflate(R.menu.about_menu, menu);
//    return true;
//  }
//
//  @Override
//  public boolean onOptionsItemSelected(MenuItem item) {
//    switch (item.getItemId()) {
//    case R.id.aboutItem:
//      Intent aboutIntent = new Intent(this, AboutActivity.class);
//      startActivity(aboutIntent);
//      return true;
//    }
//    return false;
//  }
//
//  @Override
//  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//    if (requestCode == PICK_COUNTRY && resultCode == RESULT_OK && data != null) {
//      this.countryCode.setText(data.getIntExtra("country_code", 1)+"");
//      setCountryDisplay(data.getStringExtra("country_name"));
//      setCountryFormatter(data.getIntExtra("country_code", 1));
//    }
//  }
//
//  private void initializeResources() {
//    ApplicationContext.getInstance().setContext(this);
//
//    this.countrySpinner = (Spinner)findViewById(R.id.country_spinner);
//    this.countryCode    = (TextView)findViewById(R.id.country_code);
//    this.number         = (TextView)findViewById(R.id.number);
//    this.createButton   = (Button)findViewById(R.id.registerButton);
//
//    this.countrySpinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
//    this.countrySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//    setCountryDisplay(getString(R.string.CreateAccountActivity_select_your_country));
//
//    this.countrySpinner.setAdapter(this.countrySpinnerAdapter);
//    this.countrySpinner.setOnTouchListener(new View.OnTouchListener() {
//      @Override
//      public boolean onTouch(View v, MotionEvent event) {
//        if (event.getAction() == MotionEvent.ACTION_UP) {
//          Intent intent = new Intent(CreateAccountActivity.this, CountrySelectionActivity.class);
//          startActivityForResult(intent, PICK_COUNTRY);
//        }
//        return true;
//      }
//    });
//
//    this.countryCode.addTextChangedListener(new CountryCodeChangedListener());
//    this.number.addTextChangedListener(new NumberChangedListener());
//    this.createButton.setOnClickListener(new CreateButtonListener());
//  }
//
//
//  private void initializeNumber() {
//    String localNumber = ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE))
//                         .getLine1Number();
//
//    if (!Util.isEmpty(localNumber) && !localNumber.startsWith("+")) {
//      if (localNumber.length() == 10) localNumber = "+1" + localNumber;
//      else                            localNumber = "+"  + localNumber;
//    }
//
//    try {
//      if (!Util.isEmpty(localNumber)) {
//        PhoneNumberUtil numberUtil    = PhoneNumberUtil.getInstance();
//        PhoneNumber localNumberObject = numberUtil.parse(localNumber, null);
//
//        if (localNumberObject != null) {
//          this.countryCode.setText(localNumberObject.getCountryCode()+"");
//          this.number.setText(localNumberObject.getNationalNumber()+"");
//        }
//      }
//    } catch (NumberParseException npe) {
//      Log.w("CreateAccountActivity", npe);
//    }
//  }
//
//  private void setCountryDisplay(String value) {
//    this.countrySpinnerAdapter.clear();
//    this.countrySpinnerAdapter.add(value);
//  }
//
//  private void setCountryFormatter(int countryCode) {
//    PhoneNumberUtil util = PhoneNumberUtil.getInstance();
//    String regionCode    = util.getRegionCodeForCountryCode(countryCode);
//
//    if (regionCode == null) {
//      this.countryFormatter = null;
//    } else {
//      this.countryFormatter = util.getAsYouTypeFormatter(regionCode);
//    }
//  }
//
//  private String getConfiguredE164Number() {
//    return PhoneNumberFormatter.formatE164(countryCode.getText().toString(),
//                                           number.getText().toString());
//  }
//
//  private class CreateButtonListener implements View.OnClickListener {
//    @Override
//    public void onClick(View v) {
//      final CreateAccountActivity self = CreateAccountActivity.this;
//
//      if (Util.isEmpty(countryCode.getText())) {
//        Toast.makeText(self,
//                       R.string.CreateAccountActivity_you_must_specify_your_country_code,
//                       Toast.LENGTH_LONG).show();
//        return;
//      }
//
//      if (Util.isEmpty(number.getText())) {
//        Toast.makeText(self,
//                       R.string.CreateAccountActivity_you_must_specify_your_phone_number,
//                       Toast.LENGTH_LONG).show();
//        return;
//      }
//
//      final String e164number = getConfiguredE164Number();
//
//      if (!PhoneNumberFormatter.isValidNumber(e164number)) {
//        Util.showAlertDialog(self,
//                             getString(R.string.CreateAccountActivity_invalid_number),
//                             String.format(getString(R.string.CreateAccountActivity_the_number_you_specified_s_is_invalid), e164number));
//        return;
//      }
//
//      AlertDialog.Builder dialog = new AlertDialog.Builder(self);
//      dialog.setMessage(String.format(getString(R.string.CreateAccountActivity_we_will_now_verify_that_the_following_number_is_associated),
//                                      PhoneNumberFormatter.getInternationalFormatFromE164(e164number)));
//      dialog.setPositiveButton(getString(R.string.CreateAccountActivity_continue),
//                               new DialogInterface.OnClickListener() {
//        @Override
//        public void onClick(DialogInterface dialog, int which) {
//          Intent intent = new Intent(self, RegistrationProgressActivity.class);
//          intent.putExtra("e164number", e164number);
//          startActivity(intent);
//          finish();
//        }
//      });
//      dialog.setNegativeButton(getString(R.string.CreateAccountActivity_edit), null);
//      dialog.show();
//    }
//  }
//
//  private class CountryCodeChangedListener implements TextWatcher {
//    @Override
//    public void afterTextChanged(Editable s) {
//      if (Util.isEmpty(s)) {
//        setCountryDisplay(getString(R.string.CreateAccountActivity_select_your_country));
//        countryFormatter = null;
//        return;
//      }
//
//      int countryCode   = Integer.parseInt(s.toString());
//      String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);
//      setCountryFormatter(countryCode);
//      setCountryDisplay(PhoneNumberFormatter.getRegionDisplayName(regionCode));
//
//      if (!Util.isEmpty(regionCode) && !regionCode.equals("ZZ")) {
//        number.requestFocus();
//      }
//    }
//
//    @Override
//    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//    }
//
//    @Override
//    public void onTextChanged(CharSequence s, int start, int before, int count) {
//    }
//  }
//
//  private class NumberChangedListener implements TextWatcher {
//
//    @Override
//    public void afterTextChanged(Editable s) {
//      if (countryFormatter == null)
//        return;
//
//      if (Util.isEmpty(s))
//        return;
//
//      countryFormatter.clear();
//
//      String number          = s.toString().replaceAll("[^\\d.]", "");
//      String formattedNumber = null;
//
//      for (int i=0;i<number.length();i++) {
//        formattedNumber = countryFormatter.inputDigit(number.charAt(i));
//      }
//
//      if (!s.toString().equals(formattedNumber)) {
//        s.replace(0, s.length(), formattedNumber);
//      }
//    }
//
//    @Override
//    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//    }
//
//    @Override
//    public void onTextChanged(CharSequence s, int start, int before, int count) {
//
//    }
//  }
//}
