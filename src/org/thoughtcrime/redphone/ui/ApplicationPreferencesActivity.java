///*
// * Copyright (C) 2011 Whisper Systems
// *
// * This program is free software: you can redistribute it and/or modify
// * it under the terms of the GNU General Public License as published by
// * the Free Software Foundation, either version 3 of the License, or
// * (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with this program.  If not, see <http://www.gnu.org/licenses/>.
// */
//
//package org.thoughtcrime.redphone.ui;
//
//import android.content.Context;
//import android.os.Bundle;
//import android.preference.PreferenceManager;
//import android.support.v7.app.AppCompatActivity;
//
//import com.actionbarsherlock.app.SherlockPreferenceActivity;
//import com.actionbarsherlock.view.MenuItem;
//
//import org.thoughtcrime.redphone.R;
//import org.thoughtcrime.redphone.Release;
//
///**
// * Preferences menu Activity.
// *
// * Also provides methods for setting and getting application preferences.
// *
// * @author Stuart O. Anderson
// */
////TODO(Stuart Anderson): Consider splitting this into an Activity and a utility class
//public class ApplicationPreferencesActivity extends AppCompatActivity {
//
//  public static final String LOOPBACK_MODE_PREF         = "pref_loopback";
//  public static final String OPPORTUNISTIC_UPGRADE_PREF = "pref_prompt_upgrade";
//  public static final String BLUETOOTH_ENABLED          = "pref_bluetooth_enabled";
//
//  @Override
//  protected void onCreate(Bundle icicle) {
//    super.onCreate(icicle);
//    addPreferencesFromResource(R.xml.preferences);
//
//    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//    getSupportActionBar().setTitle(R.string.ApplicationPreferencesActivity_redphone_settings);
//
//    if(Release.DEBUG) {
//      addPreferencesFromResource(R.xml.debug);
//    }
//  }
//
//  @Override
//  protected void onDestroy() {
//    super.onDestroy();
//  }
//
//  @Override
//  public boolean onOptionsItemSelected(MenuItem item) {
//    switch (item.getItemId()) {
//      case android.R.id.home:
//        finish();
//        return true;
//    }
//
//    return false;
//  }
//
//  public static boolean getPromptUpgradePreference(Context context) {
//    return PreferenceManager
//           .getDefaultSharedPreferences(context).getBoolean(OPPORTUNISTIC_UPGRADE_PREF, true);
//  }
//
//  public static boolean getLoopbackEnabled(Context context) {
//    return Release.DEBUG &&
//           PreferenceManager
//           .getDefaultSharedPreferences(context).getBoolean(LOOPBACK_MODE_PREF, false);
//  }
//
//  public static boolean getBluetoothEnabled(Context context) {
//    return PreferenceManager
//      .getDefaultSharedPreferences(context).getBoolean(BLUETOOTH_ENABLED, false);
//  }
//}
