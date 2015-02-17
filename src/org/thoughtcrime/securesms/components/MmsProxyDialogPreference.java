package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.mms.MmsConnection;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.net.URI;
import java.net.URISyntaxException;

public class MmsProxyDialogPreference extends MmsDialogPreference {

  public MmsProxyDialogPreference(Context context, AttributeSet attrs) {
    super(context, attrs, new CustomPreferenceHostnameValidator());
  }

  @Override
  protected boolean isCustom() {
    return TextSecurePreferences.getUseCustomMmscProxy(getContext());
  }

  @Override
  protected void setCustom(boolean custom) {
    TextSecurePreferences.setUseCustomMmscProxy(getContext(), custom);
  }

  @Override
  protected String getCustomValue() {
    return TextSecurePreferences.getMmscProxy(getContext());
  }

  @Override
  protected void setCustomValue(String value) {
    TextSecurePreferences.setMmscProxy(getContext(), value);
  }

  @Override
  protected String getDefaultValue(MmsConnection.Apn apn) {
    return apn.getProxy();
  }

  private static class CustomPreferenceHostnameValidator implements CustomPreferenceValidator {
    @Override
    public boolean isValid(String value) {
      if (TextUtils.isEmpty(value)) return true;

      try {
        URI uri = new URI(null, value, null, null);
        return true;
      } catch (URISyntaxException mue) {
        return false;
      }
    }
  }
}
