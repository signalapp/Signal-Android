/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.support.v4.preference.PreferenceFragment;
import android.widget.Toast;

import org.thoughtcrime.securesms.mms.OutgoingMmsConnection;
import org.thoughtcrime.securesms.util.TextSecurePreferences;


public class MmsPreferencesFragment extends PreferenceFragment {

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    initializePreferences();
    initializeEditTextSummaries();
    ((PassphraseRequiredActionBarActivity) getActivity()).getSupportActionBar()
        .setTitle(R.string.preferences__advanced_mms_access_point_names);
  }

  private void initializePreferences() {
    if (!OutgoingMmsConnection.isConnectionPossible(getActivity())) {
      TextSecurePreferences.setUseLocalApnsEnabled(getActivity(), true);
      addPreferencesFromResource(R.xml.preferences_manual_mms);
      this.findPreference(TextSecurePreferences.ENABLE_MANUAL_MMS_PREF)
          .setOnPreferenceChangeListener(new OverrideMmsChangeListener());
    } else {
      addPreferencesFromResource(R.xml.preferences_manual_mms);
    }
  }

  private void initializeEditTextSummary(final EditTextPreference preference) {
    if (preference.getText() == null) {
      preference.setSummary("Not set");
    } else {
      preference.setSummary(preference.getText());
    }

    preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference pref, Object newValue) {
        preference.setSummary(newValue == null ? "Not set" : ((String) newValue));
        return true;
      }
    });
  }

  private void initializeEditTextSummaries() {
    initializeEditTextSummary((EditTextPreference)this.findPreference(TextSecurePreferences.MMSC_HOST_PREF));
    initializeEditTextSummary((EditTextPreference)this.findPreference(TextSecurePreferences.MMSC_PROXY_HOST_PREF));
    initializeEditTextSummary((EditTextPreference)this.findPreference(TextSecurePreferences.MMSC_PROXY_PORT_PREF));
    initializeEditTextSummary((EditTextPreference)this.findPreference(TextSecurePreferences.MMSC_USERNAME_PREF));
    initializeEditTextSummary((EditTextPreference)this.findPreference(TextSecurePreferences.MMSC_PASSWORD_PREF));
  }

  private class OverrideMmsChangeListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
      TextSecurePreferences.setUseLocalApnsEnabled(getActivity(), true);
      Toast.makeText(getActivity(), R.string.MmsPreferencesFragment__manual_mms_settings_are_required,
                     Toast.LENGTH_SHORT).show();
      return false;
    }
  }

  public static CharSequence getSummary(Context context) {
    final int enabledResId  = R.string.MmsPreferencesFragment__enabled;
    final int disabledResId = R.string.MmsPreferencesFragment__disabled;

    return context.getString(TextSecurePreferences.isUseLocalApnsEnabled(context) ? enabledResId : disabledResId);
  }
}
