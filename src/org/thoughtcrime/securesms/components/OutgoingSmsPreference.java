package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class OutgoingSmsPreference extends DialogPreference {
  private CheckBox dataUsers;
  private CheckBox askForSmsFallback;
  private CheckBox askForMmsFallback;
  private CheckBox nonDataUsers;
  private CheckBox disableMms;
  public OutgoingSmsPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    setPersistent(false);
    setDialogLayoutResource(R.layout.outgoing_sms_preference);
  }

  @Override
  protected void onBindDialogView(final View view) {
    super.onBindDialogView(view);
    dataUsers      = (CheckBox) view.findViewById(R.id.data_users);
    askForSmsFallback = (CheckBox) view.findViewById(R.id.ask_before_sms_fallback);
    askForMmsFallback = (CheckBox) view.findViewById(R.id.ask_before_mms_fallback);
    nonDataUsers   = (CheckBox) view.findViewById(R.id.non_data_users);
    disableMms = (CheckBox) view.findViewById(R.id.disable_mms);

    dataUsers.setChecked(TextSecurePreferences.isSmsFallbackEnabled(getContext()));
    askForSmsFallback.setChecked(TextSecurePreferences.isSmsFallbackAskEnabled(getContext()));
    askForMmsFallback.setChecked(TextSecurePreferences.isMmsFallbackAskEnabled(getContext()));
    nonDataUsers.setChecked(TextSecurePreferences.isSmsNonDataOutEnabled(getContext()));
    disableMms.setChecked(TextSecurePreferences.isMmsCompletelyDisabled(getContext()));

    dataUsers.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        askForSmsFallback.setEnabled(dataUsers.isChecked());
        askForMmsFallback.setEnabled(dataUsers.isChecked() && !disableMms.isChecked());
      }
    });

    disableMms.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        askForMmsFallback.setEnabled(dataUsers.isChecked() && !disableMms.isChecked());
      }
    });

    askForSmsFallback.setEnabled(dataUsers.isChecked());
    askForMmsFallback.setEnabled(dataUsers.isChecked() && !disableMms.isChecked());
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);

    if (positiveResult) {
      TextSecurePreferences.setSmsFallbackEnabled(getContext(), dataUsers.isChecked());
      TextSecurePreferences.setSmsFallbackAskEnabled(getContext(), askForSmsFallback.isChecked());
      TextSecurePreferences.setMmsFallbackAskEnabled(getContext(), askForMmsFallback.isChecked());
      TextSecurePreferences.setSmsNonDataOutEnabled(getContext(), nonDataUsers.isChecked());
      TextSecurePreferences.setMmsCompletelyDisabled(getContext(), disableMms.isChecked());
      if (getOnPreferenceChangeListener() != null) getOnPreferenceChangeListener().onPreferenceChange(this, null);
    }
  }
}
