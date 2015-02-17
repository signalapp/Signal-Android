package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import org.thoughtcrime.securesms.mms.MmsConnection;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class MmsMmscDialogPreference extends MmsDialogPreference {

  public MmsMmscDialogPreference(Context context, AttributeSet attrs) {
    super(context, attrs, new CustomPreferenceUriValidator());
  }

  @Override
  protected boolean isCustom() {
    return TextSecurePreferences.getUseCustomMmsc(getContext());
  }

  @Override
  protected void setCustom(boolean custom) {
    TextSecurePreferences.setUseCustomMmsc(getContext(), custom);
  }

  @Override
  protected String getCustomValue() {
    return TextSecurePreferences.getMmscUrl(getContext());
  }

  @Override
  protected void setCustomValue(String value) {
    TextSecurePreferences.setMmscUrl(getContext(), value);
  }

  @Override
  protected String getDefaultValue(MmsConnection.Apn apn) {
    return apn.getMmsc();
  }

  protected static class CustomPreferenceUriValidator implements CustomPreferenceValidator {
    @Override
    public boolean isValid(String value) {
      if (TextUtils.isEmpty(value)) return true;

      try {
        new URI(value);
        return true;
      } catch (URISyntaxException mue) {
        return false;
      }
    }
  }
}
