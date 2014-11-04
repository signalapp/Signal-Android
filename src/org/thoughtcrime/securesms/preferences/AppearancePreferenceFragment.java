package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.preference.PreferenceFragment;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class AppearancePreferenceFragment extends PreferenceFragment {

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_appearance);
  }

  @Override
  public void onStart() {
    super.onStart();
    getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener((ApplicationPreferencesActivity)getActivity());
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__appearance);
  }

  @Override
  public void onStop() {
    super.onStop();
    getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener((ApplicationPreferencesActivity) getActivity());
  }

  public static CharSequence getSummary(Context context) {
    String[] languageEntries     = context.getResources().getStringArray(R.array.language_entries);
    String[] languageEntryValues = context.getResources().getStringArray(R.array.language_values);
    String[] themeEntries        = context.getResources().getStringArray(R.array.pref_theme_entries);
    String[] themeEntryValues    = context.getResources().getStringArray(R.array.pref_theme_values);

    Integer langIndex  = findIndexOfValue(TextSecurePreferences.getLanguage(context), languageEntryValues);
    Integer themeIndex = findIndexOfValue(TextSecurePreferences.getTheme(context), themeEntryValues);

    return context.getString(R.string.preferences__theme)    + ": " + themeEntries[themeIndex] + ", " +
      context.getString(R.string.preferences__language) + ": " + languageEntries[langIndex];
  }

  // Copy from ListPreference
  private static int findIndexOfValue(String value,  CharSequence[] mEntryValues) {
    if (value != null && mEntryValues != null) {
      for (int i = mEntryValues.length - 1; i >= 0; i--) {
        if (mEntryValues[i].equals(value)) {
          return i;
        }
      }
    }
    return -1;
  }
}
