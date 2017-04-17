package org.thoughtcrime.securesms.preferences;

import android.preference.ListPreference;
import android.preference.Preference;
import android.support.v4.preference.PreferenceFragment;

import org.thoughtcrime.securesms.R;

import java.util.Arrays;

public abstract class ListSummaryPreferenceFragment extends CorrectedPreferenceFragment {

  protected class ListSummaryListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
      ListPreference listPref   = (ListPreference) preference;
      int            entryIndex = Arrays.asList(listPref.getEntryValues()).indexOf(value);

      listPref.setSummary(entryIndex >= 0 && entryIndex < listPref.getEntries().length
                          ? listPref.getEntries()[entryIndex]
                          : getString(R.string.preferences__led_color_unknown));
      return true;
    }
  }

  protected void initializeListSummary(ListPreference pref) {
    pref.setSummary(pref.getEntry());
  }
}
