package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.support.v4.preference.PreferenceFragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Trimmer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChatsPreferenceFragment extends PreferenceFragment {
  private static final String TAG = ChatsPreferenceFragment.class.getSimpleName();

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_chats);

    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_MOBILE_PREF)
        .setOnPreferenceChangeListener(new MediaDownloadChangeListener());
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_WIFI_PREF)
        .setOnPreferenceChangeListener(new MediaDownloadChangeListener());
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_ROAMING_PREF)
        .setOnPreferenceChangeListener(new MediaDownloadChangeListener());

    findPreference(TextSecurePreferences.THREAD_TRIM_NOW)
        .setOnPreferenceClickListener(new TrimNowClickListener());
    findPreference(TextSecurePreferences.THREAD_TRIM_LENGTH)
        .setOnPreferenceChangeListener(new TrimLengthValidationListener());

  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity)getActivity()).getSupportActionBar().setTitle(R.string.preferences__chats);
    setMediaDownloadSummaries();
  }

  private void setMediaDownloadSummaries() {
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_MOBILE_PREF)
        .setSummary(getSummaryForMediaPreference(TextSecurePreferences.getMobileMediaDownloadAllowed(getActivity())));
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_WIFI_PREF)
        .setSummary(getSummaryForMediaPreference(TextSecurePreferences.getWifiMediaDownloadAllowed(getActivity())));
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_ROAMING_PREF)
        .setSummary(getSummaryForMediaPreference(TextSecurePreferences.getRoamingMediaDownloadAllowed(getActivity())));
  }

  private CharSequence getSummaryForMediaPreference(Set<String> allowedNetworks) {
    String[]     keys      = getResources().getStringArray(R.array.pref_media_download_entries);
    String[]     values    = getResources().getStringArray(R.array.pref_media_download_values);
    List<String> outValues = new ArrayList<>(allowedNetworks.size());

    for (int i=0; i < keys.length; i++) {
      if (allowedNetworks.contains(keys[i])) outValues.add(values[i]);
    }

    return outValues.isEmpty() ? getResources().getString(R.string.preferences__none)
                               : TextUtils.join(", ", outValues);
  }

  private class TrimNowClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final int threadLengthLimit = TextSecurePreferences.getThreadTrimLength(getActivity());
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setTitle(R.string.ApplicationPreferencesActivity_delete_all_old_messages_now);
      builder.setMessage(getString(R.string.ApplicationPreferencesActivity_are_you_sure_you_would_like_to_immediately_trim_all_conversation_threads_to_the_s_most_recent_messages,
                                   threadLengthLimit));
      builder.setPositiveButton(R.string.ApplicationPreferencesActivity_delete,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Trimmer.trimAllThreads(getActivity(), threadLengthLimit);
          }
        });

      builder.setNegativeButton(android.R.string.cancel, null);
      builder.show();

      return true;
    }
  }

  private class MediaDownloadChangeListener implements OnPreferenceChangeListener {
    @SuppressWarnings("unchecked")
    @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
      Log.w(TAG, "onPreferenceChange");
      preference.setSummary(getSummaryForMediaPreference((Set<String>)newValue));
      return true;
    }
  }

  private class TrimLengthValidationListener implements Preference.OnPreferenceChangeListener {

    public TrimLengthValidationListener() {
      EditTextPreference preference = (EditTextPreference)findPreference(TextSecurePreferences.THREAD_TRIM_LENGTH);
      preference.setSummary(getString(R.string.ApplicationPreferencesActivity_messages_per_conversation, preference.getText()));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      if (newValue == null || ((String)newValue).trim().length() == 0) {
        return false;
      }

      try {
        Integer.parseInt((String)newValue);
      } catch (NumberFormatException nfe) {
        Log.w(TAG, nfe);
        return false;
      }

      if (Integer.parseInt((String)newValue) < 1) {
        return false;
      }

      preference.setSummary(getString(R.string.ApplicationPreferencesActivity_messages_per_conversation, newValue));
      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    return null;
  }
}
