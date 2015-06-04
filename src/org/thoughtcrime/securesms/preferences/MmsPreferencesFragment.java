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
package org.thoughtcrime.securesms.preferences;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.preference.PreferenceFragment;
import android.util.Log;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.CustomDefaultPreference;
import org.thoughtcrime.securesms.database.ApnDatabase;
import org.thoughtcrime.securesms.mms.LegacyMmsConnection;
import org.thoughtcrime.securesms.util.TelephonyUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;


public class MmsPreferencesFragment extends PreferenceFragment {

  private static final String TAG = MmsPreferencesFragment.class.getSimpleName();
  private static final String LOADAPN_FILTER = "MmsPreferencesFragment_loadApnReceiver";

  private LoadApnDefaultsReceiver loadApnReceiver;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_manual_mms);

    ((PassphraseRequiredActionBarActivity) getActivity()).getSupportActionBar()
        .setTitle(R.string.preferences__advanced_mms_access_point_names);
  }

  @Override
  public void onResume() {
    super.onResume();
    loadApnReceiver = new LoadApnDefaultsReceiver();
    LocalBroadcastManager.getInstance(getActivity())
      .registerReceiver(loadApnReceiver, new IntentFilter(LOADAPN_FILTER));
    Intent loadApn = new Intent(getActivity(), LoadApnDefaultsService.class);
    getActivity().startService(loadApn);
  }

  @Override
  public void onPause() {
    if (loadApnReceiver != null)
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(loadApnReceiver);
  }

  private class LoadApnDefaultsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context receiverContext, Intent receiverIntent) {
      LegacyMmsConnection.Apn apnDefaults = (LegacyMmsConnection.Apn) receiverIntent.getSerializableExtra("apn");
      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_HOST_PREF))
          .setValidator(new CustomDefaultPreference.UriValidator())
          .setDefaultValue(apnDefaults.getMmsc());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_PROXY_HOST_PREF))
          .setValidator(new CustomDefaultPreference.HostnameValidator())
          .setDefaultValue(apnDefaults.getProxy());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_PROXY_PORT_PREF))
          .setValidator(new CustomDefaultPreference.PortValidator())
          .setDefaultValue(apnDefaults.getPort());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_USERNAME_PREF))
          .setDefaultValue(apnDefaults.getPort());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_PASSWORD_PREF))
          .setDefaultValue(apnDefaults.getPassword());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMS_USER_AGENT))
          .setDefaultValue(LegacyMmsConnection.USER_AGENT);
    }
  }

  public static class LoadApnDefaultsService extends IntentService {
    public LoadApnDefaultsService() {
      super("LoadApnDefaultsService");
  }

    public void onHandleIntent(Intent intent) {
      Intent resultIntent = new Intent(LOADAPN_FILTER);
      try {
        resultIntent.putExtra("apn", ApnDatabase.getInstance(this).getDefaultApnParameters(
                                     TelephonyUtil.getMccMnc(this), TelephonyUtil.getApn(this)));
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
  }
}
