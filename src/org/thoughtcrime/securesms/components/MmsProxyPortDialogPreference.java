package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.mms.MmsConnection;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class MmsProxyPortDialogPreference extends MmsDialogPreference {

  public MmsProxyPortDialogPreference(Context context, AttributeSet attrs) {
    super(context, attrs, new CustomPreferenceValidator() {
      @Override
      public boolean isValid(String value) {
        try {
          Integer.parseInt(value);
          return true;
        } catch (NumberFormatException e) {
          return false;
        }
      }
    });
  }

  @Override
  protected boolean isCustom() {
    return TextSecurePreferences.getUseCustomMmscProxyPort(getContext());
  }

  @Override
  protected void setCustom(boolean custom) {
    TextSecurePreferences.setUseCustomMmscProxyPort(getContext(), custom);
  }

  @Override
  protected String getCustomValue() {
    return TextSecurePreferences.getMmscProxyPort(getContext());
  }

  @Override
  protected void setCustomValue(String value) {
    TextSecurePreferences.setMmscProxyPort(getContext(), value);
  }

  @Override
  protected String getDefaultValue(MmsConnection.Apn apn) {
    return String.valueOf(apn.getPort());
  }
}
