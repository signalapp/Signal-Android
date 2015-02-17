package org.thoughtcrime.securesms.components;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.ApnDatabase;
import org.thoughtcrime.securesms.mms.MmsConnection;
import org.thoughtcrime.securesms.util.TelephonyUtil;

import java.io.IOException;


public abstract class MmsDialogPreference extends DialogPreference {

  protected static final String TAG = MmsDialogPreference.class.getSimpleName();

  private final int                       inputType;
  private final CustomPreferenceValidator validator;

  private Spinner  spinner;
  private EditText customText;
  private TextView defaultLabel;
  private Button   positiveButton;

  public MmsDialogPreference(Context context, AttributeSet attrs,
                             CustomPreferenceValidator validator)
  {
    super(context, attrs);

    TypedArray attributes = context.obtainStyledAttributes(attrs, new int[] {android.R.attr.inputType});
    this.inputType = attributes.getInt(0, 0);
    this.validator = validator;
    attributes.recycle();

    setPersistent(false);
    setDialogLayoutResource(R.layout.mms_preference_dialog);
  }

  @Override
  public String getSummary() {
    if (isCustom()) return "Using custom: " + getPrettyPrintValue(getCustomValue());
    else            return "Using default: " + getPrettyPrintValue(getDefaultValue());
  }

  @Override
  protected void onBindDialogView(View view) {
    super.onBindDialogView(view);

    this.spinner      = (Spinner) view.findViewById(R.id.default_or_custom);
    this.defaultLabel = (TextView) view.findViewById(R.id.default_label);
    this.customText   = (EditText) view.findViewById(R.id.custom_edit);

    this.customText.setInputType(inputType);
    this.customText.addTextChangedListener(new TextValidator());
    this.customText.setText(getCustomValue());
    this.spinner.setOnItemSelectedListener(new SelectionLister());

    new ApnDatabaseTask(defaultLabel).execute();
  }

  @Override
  protected void showDialog(Bundle instanceState) {
    super.showDialog(instanceState);
    positiveButton = ((AlertDialog)getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);

    if (isCustom()) spinner.setSelection(1, true);
    else            spinner.setSelection(0, true);
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    if (positiveResult) {
      if (spinner != null)    setCustom(spinner.getSelectedItemPosition() == 1);
      if (customText != null) setCustomValue(customText.getText().toString());

      setSummary(getSummary());
    }
  }

  protected MmsConnection.Apn getDefaultParameters() throws IOException {
    return ApnDatabase.getInstance(getContext()).getDefaultApnParameters(TelephonyUtil.getMccMnc(getContext()),
                                                                         TelephonyUtil.getApn(getContext()));
  }

  private String getPrettyPrintValue(String value) {
    if (TextUtils.isEmpty(value)) return "None";
    else                          return value;
  }

  private String getDefaultValue() {
    try {
      return getDefaultValue(getDefaultParameters());
    } catch (IOException e) {
      Log.w(TAG, e);
      return "";
    }
  }

  protected abstract boolean isCustom();
  protected abstract void setCustom(boolean custom);

  protected abstract String getCustomValue();
  protected abstract void setCustomValue(String value);

  protected abstract String getDefaultValue(MmsConnection.Apn apn);

  private class ApnDatabaseTask extends AsyncTask<Void, Void, String> {

    private final TextView result;

    protected ApnDatabaseTask(TextView result) {
      this.result  = result;
    }

    protected String doInBackground(Void... params) {
      return getDefaultValue();
    }

    @Override
    protected void onPostExecute(String value) {
      result.setText(getPrettyPrintValue(value));
    }
  }

  private class SelectionLister implements AdapterView.OnItemSelectedListener {

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      defaultLabel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
      customText.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
      positiveButton.setEnabled(position == 0 || validator.isValid(customText.getText().toString()));
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
      defaultLabel.setVisibility(View.VISIBLE);
      customText.setVisibility(View.GONE);
    }
  }

  private class TextValidator implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
      if (spinner.getSelectedItemPosition() == 1) {
        positiveButton.setEnabled(validator.isValid(s.toString()));
      }
    }
  }

  protected interface CustomPreferenceValidator {
    public boolean isValid(String value);
  }




}
