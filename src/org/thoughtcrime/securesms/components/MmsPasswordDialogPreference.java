package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.mms.MmsConnection;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class MmsPasswordDialogPreference extends MmsDialogPreference {
  public MmsPasswordDialogPreference(Context context, AttributeSet attrs) {
    super(context, attrs, new CustomPreferenceValidator() {
      @Override
      public boolean isValid(String value) {
        return true;
      }
    });
  }

  @Override
  protected boolean isCustom() {
    return TextSecurePreferences.getUseCustomMmscPassword(getContext());
  }

  @Override
  protected void setCustom(boolean custom) {
    TextSecurePreferences.setUseCustomMmscPassword(getContext(), custom);
  }

  @Override
  protected String getCustomValue() {
    return TextSecurePreferences.getMmscPassword(getContext());
  }

  @Override
  protected void setCustomValue(String value) {
    TextSecurePreferences.setMmscPassword(getContext(), value);
  }

  @Override
  protected String getDefaultValue(MmsConnection.Apn apn) {
    return apn.getPassword();
  }
}
