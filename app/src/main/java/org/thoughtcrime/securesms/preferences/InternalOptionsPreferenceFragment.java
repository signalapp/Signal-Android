package org.thoughtcrime.securesms.preferences;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob;
import org.thoughtcrime.securesms.jobs.RemoteConfigRefreshJob;
import org.thoughtcrime.securesms.jobs.RotateProfileKeyJob;
import org.thoughtcrime.securesms.jobs.StorageForcePushJob;
import org.thoughtcrime.securesms.keyvalue.InternalValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;

public class InternalOptionsPreferenceFragment extends CorrectedPreferenceFragment {
  private static final String TAG = Log.tag(InternalOptionsPreferenceFragment.class);

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_internal);

    PreferenceDataStore preferenceDataStore = SignalStore.getPreferenceDataStore();

    initializeSwitchPreference(preferenceDataStore, InternalValues.RECIPIENT_DETAILS, SignalStore.internalValues().recipientDetails());
    initializeSwitchPreference(preferenceDataStore, InternalValues.GV2_DO_NOT_CREATE_GV2, SignalStore.internalValues().gv2DoNotCreateGv2Groups());
    initializeSwitchPreference(preferenceDataStore, InternalValues.GV2_FORCE_INVITES, SignalStore.internalValues().gv2ForceInvites());
    initializeSwitchPreference(preferenceDataStore, InternalValues.GV2_IGNORE_SERVER_CHANGES, SignalStore.internalValues().gv2IgnoreServerChanges());
    initializeSwitchPreference(preferenceDataStore, InternalValues.GV2_IGNORE_P2P_CHANGES, SignalStore.internalValues().gv2IgnoreP2PChanges());

    findPreference("pref_refresh_attributes").setOnPreferenceClickListener(preference -> {
      ApplicationDependencies.getJobManager()
                             .startChain(new RefreshAttributesJob())
                             .then(new RefreshOwnProfileJob())
                             .enqueue();
      Toast.makeText(getContext(), "Scheduled attribute refresh", Toast.LENGTH_SHORT).show();
      return true;
    });

    findPreference("pref_rotate_profile_key").setOnPreferenceClickListener(preference -> {
      ApplicationDependencies.getJobManager().add(new RotateProfileKeyJob());
      Toast.makeText(getContext(), "Scheduled profile key rotation", Toast.LENGTH_SHORT).show();
      return true;
    });

    findPreference("pref_refresh_remote_values").setOnPreferenceClickListener(preference -> {
      ApplicationDependencies.getJobManager().add(new RemoteConfigRefreshJob());
      Toast.makeText(getContext(), "Scheduled remote config refresh", Toast.LENGTH_SHORT).show();
      return true;
    });

    findPreference("pref_force_send").setOnPreferenceClickListener(preference -> {
      ApplicationDependencies.getJobManager().add(new StorageForcePushJob());
      Toast.makeText(getContext(), "Scheduled storage force push", Toast.LENGTH_SHORT).show();
      return true;
    });
  }

  private void initializeSwitchPreference(@NonNull PreferenceDataStore preferenceDataStore,
                                          @NonNull String key,
                                          boolean checked)
  {
    SwitchPreferenceCompat forceGv2Preference = (SwitchPreferenceCompat) findPreference(key);
    forceGv2Preference.setPreferenceDataStore(preferenceDataStore);
    forceGv2Preference.setChecked(checked);
  }

  @Override
  public void onResume() {
    super.onResume();
    //noinspection ConstantConditions
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__internal_preferences);
  }
}
