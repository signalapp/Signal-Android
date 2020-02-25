package org.thoughtcrime.securesms.preferences;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.preferences.widgets.StoragePreferenceCategory;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Trimmer;

public class StoragePreferenceFragment extends ListSummaryPreferenceFragment {

  private static final String TAG = Log.tag(StoragePreferenceFragment.class);

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    findPreference(TextSecurePreferences.THREAD_TRIM_NOW)
        .setOnPreferenceClickListener(new TrimNowClickListener());
    findPreference(TextSecurePreferences.THREAD_TRIM_LENGTH)
        .setOnPreferenceChangeListener(new TrimLengthValidationListener());

    StoragePreferenceCategory       storageCategory = (StoragePreferenceCategory) findPreference("pref_storage_category");
    FragmentActivity                activity        = requireActivity();
    ApplicationPreferencesViewModel viewModel       = ApplicationPreferencesViewModel.getApplicationPreferencesViewModel(activity);

    storageCategory.setOnFreeUpSpace(() -> activity.startActivity(MediaOverviewActivity.forAll(activity)));

    viewModel.getStorageBreakdown().observe(activity, storageCategory::setStorage);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_storage);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity)getActivity()).getSupportActionBar().setTitle(R.string.preferences__storage);

    FragmentActivity                activity  = requireActivity();
    ApplicationPreferencesViewModel viewModel = ApplicationPreferencesViewModel.getApplicationPreferencesViewModel(activity);

    viewModel.refreshStorageBreakdown(activity.getApplicationContext());
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private class TrimNowClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final int threadLengthLimit = TextSecurePreferences.getThreadTrimLength(getActivity());
      new AlertDialog.Builder(requireActivity())
                     .setTitle(R.string.ApplicationPreferencesActivity_delete_all_old_messages_now)
                     .setMessage(getResources().getQuantityString(R.plurals.ApplicationPreferencesActivity_this_will_immediately_trim_all_conversations_to_the_d_most_recent_messages,
                                                                        threadLengthLimit, threadLengthLimit))
                     .setPositiveButton(R.string.ApplicationPreferencesActivity_delete, (dialog, which) -> Trimmer.trimAllThreads(getActivity(), threadLengthLimit))
                     .setNegativeButton(android.R.string.cancel, null)
                     .show();

      return true;
    }
  }

  private class TrimLengthValidationListener implements Preference.OnPreferenceChangeListener {

    TrimLengthValidationListener() {
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
}
