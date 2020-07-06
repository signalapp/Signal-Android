package org.thoughtcrime.securesms.preferences;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
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

    SwitchPreferenceCompat forceGv2Preference = (SwitchPreferenceCompat) this.findPreference(InternalValues.GV2_FORCE_INVITES);
    forceGv2Preference.setPreferenceDataStore(preferenceDataStore);
    forceGv2Preference.setChecked(SignalStore.internalValues().forceGv2Invites());
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__internal_preferences);
  }
}
