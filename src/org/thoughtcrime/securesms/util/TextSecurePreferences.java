package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.preference.PreferenceManager;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;

public class TextSecurePreferences {

  public static String getLocalNumber(Context context) {
    return getStringPreference(context, ApplicationPreferencesActivity.LOCAL_NUMBER_PREF, "No Stored Number");
  }

  public static String getPushServerPassword(Context context) {
    return getStringPreference(context, ApplicationPreferencesActivity.GCM_PASSWORD_PREF, null);
  }

  public static boolean isEnterImeKeyEnabled(Context context) {
    return getBooleanPreference(context, ApplicationPreferencesActivity.ENTER_PRESENT_PREF, false);
  }

  public static boolean isEnterSendsEnabled(Context context) {
    return getBooleanPreference(context, ApplicationPreferencesActivity.ENTER_SENDS_PREF, false);
  }

  public static boolean isPasswordDisabled(Context context) {
    return getBooleanPreference(context, ApplicationPreferencesActivity.DISABLE_PASSPHRASE_PREF, false);
  }

  public static void setPasswordDisabled(Context context, boolean disabled) {
    setBooleanPreference(context, ApplicationPreferencesActivity.DISABLE_PASSPHRASE_PREF, disabled);
  }

  public static String getMmscUrl(Context context) {
    return getStringPreference(context, ApplicationPreferencesActivity.MMSC_HOST_PREF, "");
  }

  public static String getMmscProxy(Context context) {
    return getStringPreference(context, ApplicationPreferencesActivity.MMSC_PROXY_HOST_PREF, "");
  }

  public static String getMmscProxyPort(Context context) {
    return getStringPreference(context, ApplicationPreferencesActivity.MMSC_PROXY_PORT_PREF, "");
  }

  public static String getIdentityContactUri(Context context) {
    return getStringPreference(context, ApplicationPreferencesActivity.IDENTITY_PREF, null);
  }

  public static boolean isAutoRespondKeyExchangeEnabled(Context context) {
    return getBooleanPreference(context, ApplicationPreferencesActivity.AUTO_KEY_EXCHANGE_PREF, true);
  }

  public static boolean isUseLocalApnsEnabled(Context context) {
    return getBooleanPreference(context, ApplicationPreferencesActivity.USE_LOCAL_MMS_APNS_PREF, false);
  }

  private static void setBooleanPreference(Context context, String key, boolean value) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, value).commit();
  }

  private static boolean getBooleanPreference(Context context, String key, boolean defaultValue) {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, defaultValue);
  }

  private static String getStringPreference(Context context, String key, String defaultValue) {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(key, defaultValue);
  }
}
