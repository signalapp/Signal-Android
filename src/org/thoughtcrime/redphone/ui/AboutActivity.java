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
//import android.content.pm.PackageInfo;
//import android.content.pm.PackageManager.NameNotFoundException;
//import android.os.Bundle;
//import android.support.v7.app.AppCompatActivity;
//import android.view.MenuItem;
//import android.widget.TextView;
//
//import org.thoughtcrime.securesms.R;
//
//`
///**
// * The RedPhone "about" dialog.
// *
// * @author Moxie Marlinspike
// *
// */
//public class AboutActivity extends AppCompatActivity {
//
//  @Override
//  public void onCreate(Bundle icicle) {
//    super.onCreate(icicle);
//    setContentView(R.layout.about_activity);
//
//    getSupportActionBar().setTitle(R.string.AboutActivity_about_redphone);
//    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//
//    TextView versionTextView = (TextView)findViewById(R.id.versionText);
//    versionTextView.setText(String.format(getString(R.string.AboutActivity_redphone_beta_s),
//                                          getCurrentVersion()));
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
//  private String getCurrentVersion() {
//    try {
//      PackageInfo packageInfo = getPackageManager().getPackageInfo(this.getPackageName(), 0);
//      return packageInfo.versionName;
//    } catch (NameNotFoundException e) {
//      throw new AssertionError(e);
//    }
//  }
//}
