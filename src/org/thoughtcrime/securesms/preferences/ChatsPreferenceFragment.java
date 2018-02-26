package org.thoughtcrime.securesms.preferences;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.text.TextUtils;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupDialog;
import org.thoughtcrime.securesms.backup.FullBackupBase;
import org.thoughtcrime.securesms.backup.FullBackupBase.BackupEvent;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.jobs.LocalBackupJob;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.preferences.widgets.ProgressPreference;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Trimmer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChatsPreferenceFragment extends ListSummaryPreferenceFragment {
  private static final String TAG = ChatsPreferenceFragment.class.getSimpleName();

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_MOBILE_PREF)
        .setOnPreferenceChangeListener(new MediaDownloadChangeListener());
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_WIFI_PREF)
        .setOnPreferenceChangeListener(new MediaDownloadChangeListener());
    findPreference(TextSecurePreferences.MEDIA_DOWNLOAD_ROAMING_PREF)
        .setOnPreferenceChangeListener(new MediaDownloadChangeListener());
    findPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF)
        .setOnPreferenceChangeListener(new ListSummaryListener());

    findPreference(TextSecurePreferences.THREAD_TRIM_NOW)
        .setOnPreferenceClickListener(new TrimNowClickListener());
    findPreference(TextSecurePreferences.THREAD_TRIM_LENGTH)
        .setOnPreferenceChangeListener(new TrimLengthValidationListener());

    findPreference(TextSecurePreferences.BACKUP_ENABLED)
        .setOnPreferenceClickListener(new BackupClickListener());
    findPreference(TextSecurePreferences.BACKUP_NOW)
        .setOnPreferenceClickListener(new BackupCreateListener());

    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF));

    EventBus.getDefault().register(this);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_chats);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity)getActivity()).getSupportActionBar().setTitle(R.string.preferences__chats);
    setMediaDownloadSummaries();
    setBackupSummary();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(BackupEvent event) {
    ProgressPreference preference = (ProgressPreference)findPreference(TextSecurePreferences.BACKUP_NOW);

    if (event.getType() == BackupEvent.Type.PROGRESS) {
      preference.setEnabled(false);
      preference.setSummary(getString(R.string.ChatsPreferenceFragment_in_progress));
      preference.setProgress(event.getCount());
    } else if (event.getType() == BackupEvent.Type.FINISHED) {
      preference.setEnabled(true);
      preference.setProgressVisible(false);
      setBackupSummary();
    }
  }

  private void setBackupSummary() {
    findPreference(TextSecurePreferences.BACKUP_NOW)
        .setSummary(String.format(getString(R.string.ChatsPreferenceFragment_last_backup_s), BackupUtil.getLastBackupTime(getContext(), Locale.US)));
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

  private class BackupClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Permissions.with(ChatsPreferenceFragment.this)
                 .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                 .ifNecessary()
                 .onAllGranted(() -> {
                   if (!((SwitchPreferenceCompat)preference).isChecked()) {
                     BackupDialog.showEnableBackupDialog(getActivity(), (SwitchPreferenceCompat)preference);
                   } else {
                     BackupDialog.showDisableBackupDialog(getActivity(), (SwitchPreferenceCompat)preference);
                   }
                 })
                 .withPermanentDenialDialog(getString(R.string.ChatsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups))
                 .execute();

      return true;
    }
  }

  private class BackupCreateListener implements Preference.OnPreferenceClickListener {
    @SuppressLint("StaticFieldLeak")
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Permissions.with(ChatsPreferenceFragment.this)
                 .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                 .ifNecessary()
                 .onAllGranted(() -> {
                   Log.w(TAG, "Queing backup...");
                   ApplicationContext.getInstance(getContext())
                                     .getJobManager()
                                     .add(new LocalBackupJob(getContext()));
                 })
                 .withPermanentDenialDialog(getString(R.string.ChatsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups))
                 .execute();

      return true;
    }
  }

  private class TrimNowClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final int threadLengthLimit = TextSecurePreferences.getThreadTrimLength(getActivity());
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setTitle(R.string.ApplicationPreferencesActivity_delete_all_old_messages_now);
      builder.setMessage(getResources().getQuantityString(R.plurals.ApplicationPreferencesActivity_this_will_immediately_trim_all_conversations_to_the_d_most_recent_messages,
                                                          threadLengthLimit, threadLengthLimit));
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

  private class MediaDownloadChangeListener implements Preference.OnPreferenceChangeListener {
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
      onPreferenceChange(preference, preference.getText());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      if (newValue == null || ((String)newValue).trim().length() == 0) {
        return false;
      }

      int value;
      try {
        value = Integer.parseInt((String)newValue);
      } catch (NumberFormatException nfe) {
        Log.w(TAG, nfe);
        return false;
      }

      if (value < 1) {
        return false;
      }

      preference.setSummary(getResources().getQuantityString(R.plurals.ApplicationPreferencesActivity_messages_per_conversation, value, value));
      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    return null;
  }
}
