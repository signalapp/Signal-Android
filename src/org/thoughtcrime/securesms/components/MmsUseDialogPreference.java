package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.mms.MmsConnection;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class MmsUseDialogPreference extends MmsDialogPreference {

  public MmsUseDialogPreference(Context context, AttributeSet attrs) {
    super(context, attrs, new CustomPreferenceValidator() {
      @Override
      public boolean isValid(String value) {
        return true;
      }
    });
  }

  @Override
  protected boolean isCustom() {
    return TextSecurePreferences.getUseCustomMmscUsername(getContext());
  }

  @Override
  protected void setCustom(boolean custom) {
    TextSecurePreferences.setUseCustomMmscUsername(getContext(), custom);
  }

  @Override
  protected String getCustomValue() {
    return TextSecurePreferences.getMmscUsername(getContext());
  }

  @Override
  protected void setCustomValue(String value) {
    TextSecurePreferences.setMmscUsername(getContext(), value);
  }

  @Override
  protected String getDefaultValue(MmsConnection.Apn apn) {
    return apn.getUsername();
  }
}
