package org.thoughtcrime.securesms.preferences;


import androidx.preference.ListPreference;
import androidx.preference.Preference;

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
