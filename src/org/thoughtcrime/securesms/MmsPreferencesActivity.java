/**
 * Copyright (C) 2011 Whisper Systems
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

import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.actionbarsherlock.view.MenuItem;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MemoryCleaner;

public class MmsPreferencesActivity extends PassphraseRequiredSherlockPreferenceActivity {

  public static final String MANUAL_MMS_REQUIRED = "org.thoughtcrime.securesms.MmsPreferencesActivity.MANUAL_MMS_REQUIRED";

  private MasterSecret masterSecret;

  private final DynamicTheme dynamicTheme       = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(icicle);

    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    initializePreferences();

    masterSecret = getIntent().getParcelableExtra("master_secret");

    initializeEditTextSummaries();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onDestroy() {
    MemoryCleaner.clean(masterSecret);
    MemoryCleaner.clean((MasterSecret) getIntent().getParcelableExtra("master_secret"));
    super.onDestroy();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        handleDownloadMmsPendingApn();
        finish();
        return true;
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    handleDownloadMmsPendingApn();
    super.onBackPressed();
  }

  private void initializePreferences() {
    if (this.getIntent().getExtras() != null &&
        this.getIntent().getExtras().getBoolean(MANUAL_MMS_REQUIRED, false)) {
      PreferenceManager.getDefaultSharedPreferences(this).edit()
          .putBoolean(ApplicationPreferencesActivity.USE_LOCAL_MMS_APNS_PREF, true).commit();
      addPreferencesFromResource(R.xml.mms_preferences);
      this.findPreference(ApplicationPreferencesActivity.USE_LOCAL_MMS_APNS_PREF).setOnPreferenceChangeListener(new OverrideMmsChangeListener());
    }
    else
      addPreferencesFromResource(R.xml.mms_preferences);
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
    initializeEditTextSummary((EditTextPreference)this.findPreference(ApplicationPreferencesActivity.MMSC_HOST_PREF));
    initializeEditTextSummary((EditTextPreference)this.findPreference(ApplicationPreferencesActivity.MMSC_PROXY_HOST_PREF));
    initializeEditTextSummary((EditTextPreference)this.findPreference(ApplicationPreferencesActivity.MMSC_PROXY_PORT_PREF));
  }

  private void handleDownloadMmsPendingApn() {
    Intent intent = new Intent(this, SendReceiveService.class);
    intent.setAction(SendReceiveService.DOWNLOAD_MMS_PENDING_APN_ACTION);
    startService(intent);
  }

  private class OverrideMmsChangeListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
      PreferenceManager.getDefaultSharedPreferences(MmsPreferencesActivity.this).edit()
          .putBoolean(ApplicationPreferencesActivity.USE_LOCAL_MMS_APNS_PREF, true).commit();
      Toast.makeText(MmsPreferencesActivity.this, R.string.mms_preferences_activity__manual_mms_settings_are_required, Toast.LENGTH_SHORT).show();
      return false;
    }
  }

}
