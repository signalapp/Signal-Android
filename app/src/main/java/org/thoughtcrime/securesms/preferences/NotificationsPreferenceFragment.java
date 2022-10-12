package org.thoughtcrime.securesms.preferences;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.session.libsession.utilities.TextSecurePreferences;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.notifications.NotificationChannels;

import network.loki.messenger.R;

public class NotificationsPreferenceFragment extends ListSummaryPreferenceFragment {

  @SuppressWarnings("unused")
  private static final String TAG = NotificationsPreferenceFragment.class.getSimpleName();

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    // Set up FCM toggle
    String fcmKey = "pref_key_use_fcm";
    ((SwitchPreferenceCompat)findPreference(fcmKey)).setChecked(TextSecurePreferences.isUsingFCM(getContext()));
    this.findPreference(fcmKey)
      .setOnPreferenceChangeListener((preference, newValue) -> {
        TextSecurePreferences.setIsUsingFCM(getContext(), (boolean) newValue);
        ApplicationContext.getInstance(getContext()).registerForFCMIfNeeded(true);
        return true;
      });

    if (NotificationChannels.supported()) {
      TextSecurePreferences.setNotificationRingtone(getContext(), NotificationChannels.getMessageRingtone(getContext()).toString());
      TextSecurePreferences.setNotificationVibrateEnabled(getContext(), NotificationChannels.getMessageVibrate(getContext()));
    }
    this.findPreference(TextSecurePreferences.RINGTONE_PREF)
        .setOnPreferenceChangeListener(new RingtoneSummaryListener());
    this.findPreference(TextSecurePreferences.NOTIFICATION_PRIVACY_PREF)
        .setOnPreferenceChangeListener(new NotificationPrivacyListener());
    this.findPreference(TextSecurePreferences.VIBRATE_PREF)
        .setOnPreferenceChangeListener((preference, newValue) -> {
          NotificationChannels.updateMessageVibrate(getContext(), (boolean) newValue);
          return true;
        });

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

    this.findPreference(TextSecurePreferences.NOTIFICATION_PRIVACY_PREF)
        .setOnPreferenceClickListener(preference -> {
          ListPreference listPreference = (ListPreference) preference;
          listPreference.setDialogMessage(R.string.preferences_notifications__content_message);
          new ListPreferenceDialog(listPreference, () -> {
              initializeListSummary((ListPreference) findPreference(TextSecurePreferences.NOTIFICATION_PRIVACY_PREF));
              return null;
          }).show(getChildFragmentManager(), "ListPreferenceDialog");
          return true;
        });

    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.NOTIFICATION_PRIVACY_PREF));

    if (NotificationChannels.supported()) {
      this.findPreference(TextSecurePreferences.NOTIFICATION_PRIORITY_PREF)
          .setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.getMessagesChannel(getContext()));
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            startActivity(intent);
            return true;
          });
    }

    initializeRingtoneSummary(findPreference(TextSecurePreferences.RINGTONE_PREF));
    initializeMessageVibrateSummary((SwitchPreferenceCompat)findPreference(TextSecurePreferences.VIBRATE_PREF));
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_notifications);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
      Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

      if (Settings.System.DEFAULT_NOTIFICATION_URI.equals(uri)) {
        NotificationChannels.updateMessageRingtone(getContext(), uri);
        TextSecurePreferences.removeNotificationRingtone(getContext());
      } else {
        uri = uri == null ? Uri.EMPTY : uri;
        NotificationChannels.updateMessageRingtone(getContext(), uri);
        TextSecurePreferences.setNotificationRingtone(getContext(), uri.toString());
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

  private void initializeMessageVibrateSummary(SwitchPreferenceCompat pref) {
    pref.setChecked(TextSecurePreferences.isNotificationVibrateEnabled(getContext()));
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
          ApplicationContext.getInstance(getActivity()).messageNotifier.updateNotification(getActivity());
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

      return super.onPreferenceChange(preference, value);
    }
  }

}
