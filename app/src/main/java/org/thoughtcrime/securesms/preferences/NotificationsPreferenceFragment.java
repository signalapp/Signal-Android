package org.thoughtcrime.securesms.preferences;

import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.mp02anim.ItemAnimViewController;
import org.thoughtcrime.securesms.preferences.widgets.Mp02CommonPreference;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import static android.app.Activity.RESULT_OK;

public class NotificationsPreferenceFragment extends CorrectedPreferenceFragment implements Preference.OnPreferenceClickListener {

  @SuppressWarnings("unused")
  private static final String TAG = NotificationsPreferenceFragment.class.getSimpleName();

  private Mp02CommonPreference mNotiPref;
  private Mp02CommonPreference mRingTonePref;
  private Mp02CommonPreference mVibratePref;
  private Mp02CommonPreference mInchatSoundPref;
  private Mp02CommonPreference mCallNotiPref;
  private Mp02CommonPreference mCallRingTonePref;
  private Mp02CommonPreference mCallVibratePref;
  private Mp02CommonPreference mContactJoinPref;

  private boolean mNotiEnabled;
  private boolean mVibrateEnabled;
  private boolean mInchatSoundEnabled;
  private boolean mCallNotiEnabled;
  private boolean mCallVibrateEnabled;
  private boolean mContactJoinEnabled;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    TextSecurePreferences.setNotificationRingtone(getContext(), NotificationChannels.getMessageRingtone(getContext()).toString());
    TextSecurePreferences.setNotificationVibrateEnabled(getContext(), NotificationChannels.getMessageVibrate(getContext()));
    this.findPreference(TextSecurePreferences.VIBRATE_PREF)
        .setOnPreferenceChangeListener((preference, newValue) -> {
          NotificationChannels.updateMessageVibrate(getContext(), (boolean) newValue);
          return true;
        });

    mNotiPref = (Mp02CommonPreference) findPreference(TextSecurePreferences.NOTIFICATION_PREF);
    mRingTonePref = (Mp02CommonPreference) findPreference(TextSecurePreferences.RINGTONE_PREF);
    mRingTonePref.setOnPreferenceClickListener(preference -> {
      Uri current = TextSecurePreferences.getNotificationRingtone(getContext());
      Intent intent = new Intent(getContext(), RingtonePickerActivity.class);//RingtoneManager.ACTION_RINGTONE_PICKER);
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current);
      startActivityForResult(intent, 1);
      return true;
    });
    mVibratePref = (Mp02CommonPreference) findPreference(TextSecurePreferences.VIBRATE_PREF);//TextSecurePreferences.isNotificationVibrateEnabled(getContext())
    mInchatSoundPref = (Mp02CommonPreference) findPreference(TextSecurePreferences.IN_THREAD_NOTIFICATION_PREF);

    mCallNotiPref = (Mp02CommonPreference) findPreference(TextSecurePreferences.CALL_NOTIFICATIONS_PREF);
    mCallVibratePref = (Mp02CommonPreference) findPreference(TextSecurePreferences.CALL_VIBRATE_PREF);//TextSecurePreferences.isCallNotificationVibrateEnabled(getContext())
    mContactJoinPref = (Mp02CommonPreference) findPreference(TextSecurePreferences.NEW_CONTACTS_NOTIFICATIONS);

    mNotiPref.setOnPreferenceClickListener(this);
    mVibratePref.setOnPreferenceClickListener(this);
    mInchatSoundPref.setOnPreferenceClickListener(this);
    mCallNotiPref.setOnPreferenceClickListener(this);
    mCallRingTonePref = (Mp02CommonPreference) findPreference(TextSecurePreferences.CALL_RINGTONE_PREF);
    mCallRingTonePref.setOnPreferenceClickListener(preference -> {
      Uri current = TextSecurePreferences.getCallNotificationRingtone(getContext());
      Intent intent = new Intent(getContext(), RingtonePickerActivity.class);//RingtoneManager.ACTION_RINGTONE_PICKER);
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_RINGTONE_URI);
      intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current);
      startActivityForResult(intent, 2);
      return true;
    });
    mCallVibratePref.setOnPreferenceClickListener(this);
    mContactJoinPref.setOnPreferenceClickListener(this);

    updatePrefStatus(TextSecurePreferences.NOTIFICATION_PREF, TextSecurePreferences.isNotificationsEnabled(getContext()));
    updatePrefStatus(TextSecurePreferences.VIBRATE_PREF, TextSecurePreferences.isNotificationVibrateEnabled(getContext()));
    updatePrefStatus(TextSecurePreferences.IN_THREAD_NOTIFICATION_PREF, TextSecurePreferences.isInThreadNotifications(getContext()));
    updatePrefStatus(TextSecurePreferences.CALL_NOTIFICATIONS_PREF, TextSecurePreferences.isCallNotificationsEnabled(getContext()));
    updatePrefStatus(TextSecurePreferences.CALL_VIBRATE_PREF, TextSecurePreferences.isCallNotificationVibrateEnabled(getContext()));
    updatePrefStatus(TextSecurePreferences.NEW_CONTACTS_NOTIFICATIONS, TextSecurePreferences.isNewContactsNotificationEnabled(getContext()));
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return super.onCreateView(inflater, container, savedInstanceState);
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
        NotificationChannels.updateMessageRingtone(requireContext(), uri);
        TextSecurePreferences.removeNotificationRingtone(getContext());
      } else {
        uri = uri == null ? Uri.EMPTY : uri;
        NotificationChannels.updateMessageRingtone(requireContext(), uri);
        TextSecurePreferences.setNotificationRingtone(getContext(), uri.toString());
      }
    } else if (requestCode == 2 && resultCode == RESULT_OK && data != null) {
      Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

      if (Settings.System.DEFAULT_RINGTONE_URI.equals(uri)) {
        TextSecurePreferences.removeCallNotificationRingtone(getContext());
      } else {
        TextSecurePreferences.setCallNotificationRingtone(getContext(), uri != null ? uri.toString() : Uri.EMPTY.toString());
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  private void updatePrefStatus(String key, boolean enabled) {
    String status = enabled ? PREF_STATUS_ON : PREF_STATUS_OFF;
    switch (key) {
      case TextSecurePreferences.NOTIFICATION_PREF:
        mNotiEnabled = enabled;
        mNotiPref.setTitle(getString(R.string.preferences__notifications) + status);
        break;
      case TextSecurePreferences.VIBRATE_PREF:
        mVibrateEnabled = enabled;
        mVibratePref.setTitle(getString(R.string.preferences__vibrate) + status);
        break;
      case TextSecurePreferences.IN_THREAD_NOTIFICATION_PREF:
        mInchatSoundEnabled = enabled;
        mInchatSoundPref.setTitle(getString(R.string.preferences_notifications__in_chat_sounds) + status);
        break;
      case TextSecurePreferences.CALL_NOTIFICATIONS_PREF:
        mCallNotiEnabled = enabled;
        mCallNotiPref.setTitle(getString(R.string.preferences__notifications) + status);
        break;
      case TextSecurePreferences.CALL_VIBRATE_PREF:
        mCallVibrateEnabled = enabled;
        mCallVibratePref.setTitle(getString(R.string.preferences__vibrate) + status);
        break;
      case TextSecurePreferences.NEW_CONTACTS_NOTIFICATIONS:
        mContactJoinEnabled = enabled;
        mContactJoinPref.setTitle(getString(R.string.preferences_events__contact_joined_signal) + status);
        break;
      default:
        break;
    }
  }

  @Override
  public boolean onPreferenceClick(Preference preference) {
    String key = preference.getKey();
    switch (key) {
      case TextSecurePreferences.NOTIFICATION_PREF:
        TextSecurePreferences.setNotificationsEnabled(getContext(), !mNotiEnabled);
        updatePrefStatus(TextSecurePreferences.NOTIFICATION_PREF, TextSecurePreferences.isNotificationsEnabled(getContext()));
        break;
      case TextSecurePreferences.VIBRATE_PREF:
        TextSecurePreferences.setNotificationVibrateEnabled(getContext(), !mVibrateEnabled);
        updatePrefStatus(TextSecurePreferences.VIBRATE_PREF, TextSecurePreferences.isNotificationVibrateEnabled(getContext()));
        break;
      case TextSecurePreferences.IN_THREAD_NOTIFICATION_PREF:
        TextSecurePreferences.setInThreadNotifications(getContext(), !mInchatSoundEnabled);
        updatePrefStatus(TextSecurePreferences.IN_THREAD_NOTIFICATION_PREF, TextSecurePreferences.isInThreadNotifications(getContext()));
        break;
      case TextSecurePreferences.CALL_NOTIFICATIONS_PREF:
        TextSecurePreferences.setCallNotificationsEnabled(getContext(), !mCallNotiEnabled);
        updatePrefStatus(TextSecurePreferences.CALL_NOTIFICATIONS_PREF, TextSecurePreferences.isCallNotificationsEnabled(getContext()));
        break;
      case TextSecurePreferences.CALL_VIBRATE_PREF:
        TextSecurePreferences.setCallNotificationVibrateEnabled(getContext(), !mCallVibrateEnabled);
        updatePrefStatus(TextSecurePreferences.CALL_VIBRATE_PREF, TextSecurePreferences.isCallNotificationVibrateEnabled(getContext()));
        break;
      case TextSecurePreferences.NEW_CONTACTS_NOTIFICATIONS:
        TextSecurePreferences.setNewContactsNotificationEnabled(getContext(), !mContactJoinEnabled);
        updatePrefStatus(TextSecurePreferences.NEW_CONTACTS_NOTIFICATIONS, TextSecurePreferences.isNewContactsNotificationEnabled(getContext()));
        break;
    }
    return false;
  }
}
