package org.thoughtcrime.securesms.preferences;

import android.Manifest;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupDialog;
import org.thoughtcrime.securesms.backup.FullBackupBase.BackupEvent;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.LocalBackupJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.preferences.widgets.ProgressPreference;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

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

    findPreference(TextSecurePreferences.BACKUP_ENABLED)
        .setOnPreferenceClickListener(new BackupClickListener());
    findPreference(TextSecurePreferences.BACKUP_NOW)
        .setOnPreferenceClickListener(new BackupCreateListener());
    findPreference(TextSecurePreferences.BACKUP_PASSPHRASE_VERIFY)
        .setOnPreferenceClickListener(new BackupVerifyListener());

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
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Permissions.with(ChatsPreferenceFragment.this)
                 .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                 .ifNecessary()
                 .onAllGranted(() -> {
                   Log.i(TAG, "Queing backup...");
                   ApplicationDependencies.getJobManager().add(new LocalBackupJob());
                 })
                 .withPermanentDenialDialog(getString(R.string.ChatsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups))
                 .execute();

      return true;
    }
  }

  private class BackupVerifyListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      BackupDialog.showVerifyBackupPassphraseDialog(requireContext());
      return true;
    }
  }

  private class MediaDownloadChangeListener implements Preference.OnPreferenceChangeListener {
    @SuppressWarnings("unchecked")
    @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
      Log.i(TAG, "onPreferenceChange");
      preference.setSummary(getSummaryForMediaPreference((Set<String>)newValue));
      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    return null;
  }
}
