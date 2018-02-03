package org.thoughtcrime.securesms.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.text.TextUtils;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import static android.app.Activity.RESULT_OK;

public class NotificationsPreferenceFragment extends ListSummaryPreferenceFragment {

  @SuppressWarnings("unused")
  private static final String TAG = NotificationsPreferenceFragment.class.getSimpleName();

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    this.findPreference(TextSecurePreferences.LED_COLOR_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(TextSecurePreferences.LED_BLINK_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(TextSecurePreferences.RINGTONE_PREF)
        .setOnPreferenceChangeListener(new RingtoneSummaryListener());
    this.findPreference(TextSecurePreferences.REPEAT_ALERTS_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(TextSecurePreferences.NOTIFICATION_PRIVACY_PREF)
        .setOnPreferenceChangeListener(new NotificationPrivacyListener());
    this.findPreference(TextSecurePreferences.NOTIFICATION_PRIORITY_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());

    this.findPreference(TextSecurePreferences.RINGTONE_PREF)
        .setOnPreferenceClickListener(preference -> {
          Uri current = TextSecurePreferences.getNotificationRingtone(getContext());

          Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
          intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
          intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
          intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
          intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
          intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current);

          startActivityForResult(intent, 1);

          return true;
        });

    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.LED_COLOR_PREF));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.LED_BLINK_PREF));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.REPEAT_ALERTS_PREF));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.NOTIFICATION_PRIVACY_PREF));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.NOTIFICATION_PRIORITY_PREF));
    initializeRingtoneSummary(findPreference(TextSecurePreferences.RINGTONE_PREF));
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_notifications);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__notifications);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
      Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

      if (Settings.System.DEFAULT_NOTIFICATION_URI.equals(uri)) {
        TextSecurePreferences.removeNotificationRingtone(getContext());
      } else {
        TextSecurePreferences.setNotificationRingtone(getContext(), uri != null ? uri.toString() : Uri.EMPTY.toString());
      }

      initializeRingtoneSummary(findPreference(TextSecurePreferences.RINGTONE_PREF));
    }
  }

  private class RingtoneSummaryListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      Uri value = (Uri) newValue;

      if (value == null || TextUtils.isEmpty(value.toString())) {
        preference.setSummary(R.string.preferences__silent);
      } else {
        Ringtone tone = RingtoneManager.getRingtone(getActivity(), value);

        if (tone != null) {
          preference.setSummary(tone.getTitle(getActivity()));
        }
      }

      return true;
    }
  }

  private void initializeRingtoneSummary(Preference pref) {
    RingtoneSummaryListener listener = (RingtoneSummaryListener) pref.getOnPreferenceChangeListener();
    Uri                     uri      = TextSecurePreferences.getNotificationRingtone(getContext());

    listener.onPreferenceChange(pref, uri);
  }

  public static CharSequence getSummary(Context context) {
    final int onCapsResId   = R.string.ApplicationPreferencesActivity_On;
    final int offCapsResId  = R.string.ApplicationPreferencesActivity_Off;

    return context.getString(TextSecurePreferences.isNotificationsEnabled(context) ? onCapsResId : offCapsResId);
  }

  private class NotificationPrivacyListener extends ListSummaryListener {
    @SuppressLint("StaticFieldLeak")
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          MessageNotifier.updateNotification(getActivity());
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

      return super.onPreferenceChange(preference, value);
    }

  }
}
