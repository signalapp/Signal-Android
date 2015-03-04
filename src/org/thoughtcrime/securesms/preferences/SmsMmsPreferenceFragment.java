package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Telephony;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.preference.PreferenceFragment;
import android.text.TextUtils;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.OutgoingSmsPreference;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.util.LinkedList;
import java.util.List;

public class SmsMmsPreferenceFragment extends PreferenceFragment {
  private static final String KITKAT_DEFAULT_PREF = "pref_set_default";
  private static final String OUTGOING_SMS_PREF   = "pref_outgoing_sms";
  private static final String MMS_PREF            = "pref_mms_preferences";

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_sms_mms);

    this.findPreference(OUTGOING_SMS_PREF)
      .setOnPreferenceChangeListener(new OutgoingSmsPreferenceListener());
    this.findPreference(MMS_PREF)
      .setOnPreferenceClickListener(new ApnPreferencesClickListener());

    initializeOutgoingSmsSummary((OutgoingSmsPreference) findPreference(OUTGOING_SMS_PREF));
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__sms_mms);

    initializePlatformSpecificOptions();
  }

  private void initializePlatformSpecificOptions() {
    PreferenceScreen   preferenceScreen         = getPreferenceScreen();
    Preference         defaultPreference        = findPreference(KITKAT_DEFAULT_PREF);
    Preference         allSmsPreference         = findPreference(TextSecurePreferences.ALL_SMS_PREF);
    Preference         allMmsPreference         = findPreference(TextSecurePreferences.ALL_MMS_PREF);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ) {
      if (allSmsPreference != null) preferenceScreen.removePreference(allSmsPreference);
      if (allMmsPreference != null) preferenceScreen.removePreference(allMmsPreference);

      if (Util.isDefaultSmsProvider(getActivity())) {
        defaultPreference.setIntent(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_sms_enabled));
        defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_change_your_default_sms_app));
      } else {
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getActivity().getPackageName());
        defaultPreference.setIntent(intent);
        defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_sms_disabled));
        defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_make_textsecure_your_default_sms_app));
      }
    } else if (defaultPreference != null) {
      preferenceScreen.removePreference(defaultPreference);
    }
  }

  private void initializeOutgoingSmsSummary(OutgoingSmsPreference pref) {
    pref.setSummary(buildOutgoingSmsDescription());
  }

  private class OutgoingSmsPreferenceListener implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {

      preference.setSummary(buildOutgoingSmsDescription());
      return false;
    }
  }

  private String buildOutgoingSmsDescription() {
    final StringBuilder builder         = new StringBuilder();
    final boolean       dataFallback    = TextSecurePreferences.isFallbackSmsAllowed(getActivity());
    final boolean       dataFallbackAsk = TextSecurePreferences.isFallbackSmsAskRequired(getActivity());
    final boolean       mmsFallback     = TextSecurePreferences.isFallbackMmsEnabled(getActivity());
    final boolean       nonData         = TextSecurePreferences.isDirectSmsAllowed(getActivity());

    if (dataFallback) {
      builder.append(getString(R.string.preferences__sms_outgoing_push_users));

      List<String> fallbackOptions = new LinkedList<>();
      if (dataFallbackAsk) fallbackOptions.add(getString(R.string.preferences__sms_fallback_push_users_ask));
      if (!mmsFallback)    fallbackOptions.add(getString(R.string.preferences__sms_fallback_push_users_no_mms));

      if (fallbackOptions.size() > 0) {
        builder.append(" (")
               .append(TextUtils.join(", ", fallbackOptions))
               .append(")");
      }
    }
    if (nonData) {
      if (dataFallback) builder.append(", ");
      builder.append(getString(R.string.preferences__sms_fallback_non_push_users));
    }
    if (!dataFallback && !nonData) {
      builder.append(getString(R.string.preferences__sms_fallback_nobody));
    }
    return builder.toString();
  }

  private class ApnPreferencesClickListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      Fragment            fragment            = new MmsPreferencesFragment();
      FragmentManager     fragmentManager     = getActivity().getSupportFragmentManager();
      FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
      fragmentTransaction.replace(android.R.id.content, fragment);
      fragmentTransaction.addToBackStack(null);
      fragmentTransaction.commit();

      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    return getIncomingSmsSummary(context) + ", " + getOutgoingSmsSummary(context);
  }

  private static CharSequence getIncomingSmsSummary(Context context) {
    final int onResId          = R.string.ApplicationPreferencesActivity_on;
    final int offResId         = R.string.ApplicationPreferencesActivity_off;
    final int smsResId         = R.string.ApplicationPreferencesActivity_sms;
    final int mmsResId         = R.string.ApplicationPreferencesActivity_mms;
    final int incomingSmsResId = R.string.ApplicationPreferencesActivity_incoming_sms;

    final int incomingSmsSummary;
    boolean postKitkatSMS = Util.isDefaultSmsProvider(context);
    boolean preKitkatSMS  = TextSecurePreferences.isInterceptAllSmsEnabled(context);
    boolean preKitkatMMS  = TextSecurePreferences.isInterceptAllMmsEnabled(context);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      if (postKitkatSMS)                      incomingSmsSummary = onResId;
      else                                    incomingSmsSummary = offResId;
    } else {
      if      (preKitkatSMS && preKitkatMMS)  incomingSmsSummary = onResId;
      else if (preKitkatSMS && !preKitkatMMS) incomingSmsSummary = smsResId;
      else if (!preKitkatSMS && preKitkatMMS) incomingSmsSummary = mmsResId;
      else                                    incomingSmsSummary = offResId;
    }
    return context.getString(incomingSmsResId) + ": " + context.getString(incomingSmsSummary);
  }

  private static CharSequence getOutgoingSmsSummary(Context context) {
    final int onResId          = R.string.ApplicationPreferencesActivity_on;
    final int offResId         = R.string.ApplicationPreferencesActivity_off;
    final int partialResId     = R.string.ApplicationPreferencesActivity_partial;
    final int outgoingSmsResId = R.string.ApplicationPreferencesActivity_outgoing_sms;

    final int outgoingSmsSummary;
    if (TextSecurePreferences.isFallbackSmsAllowed(context) && TextSecurePreferences.isDirectSmsAllowed(context)) {
      outgoingSmsSummary = onResId;
    } else if (TextSecurePreferences.isFallbackSmsAllowed(context) ^ TextSecurePreferences.isDirectSmsAllowed(context)) {
      outgoingSmsSummary = partialResId;
    } else {
      outgoingSmsSummary = offResId;
    }
    return context.getString(outgoingSmsResId) + ": " + context.getString(outgoingSmsSummary);
  }
}
