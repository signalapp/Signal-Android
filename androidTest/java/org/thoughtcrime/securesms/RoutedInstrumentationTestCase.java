/**
 * Copyright (C) 2015 Open Whisper Systems
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

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.preference.PreferenceManager;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.ViewInteraction;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecretUtil;

import static android.support.test.espresso.action.ViewActions.typeText;

public class RoutedInstrumentationTestCase extends ActivityInstrumentationTestCase2<RoutingActivity> {
  private static final String TAG = RoutedInstrumentationTestCase.class.getSimpleName();

  private Context context;

  public RoutedInstrumentationTestCase() {
    super(RoutingActivity.class);
  }

  public RoutedInstrumentationTestCase(Context context) {
    super(RoutingActivity.class);
    this.context = context;
  }

  protected void initAppState() throws Exception {
    PreferenceManager.getDefaultSharedPreferences(getContext()).edit().clear().commit();
    getContext().getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0).edit().clear().commit();
    getContext().getSharedPreferences("SecureSMS", 0).edit().clear().commit();
  }

  @Override
  public void setUp() throws Exception {
    System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().getPath());
    super.setUp();
    initAppState();
    getActivity();
  }

  @Override
  public void tearDown() throws Exception {
    actuallyCloseSoftKeyboard();
    super.tearDown();
  }

  protected Context getContext() {
    if (context != null) return context;
    else                 return getInstrumentation().getTargetContext();
  }

  protected static void waitOn(Class<? extends Activity> clazz) {
    Log.w(TAG, "waiting for " + clazz.getName());
    new ActivityMonitor(clazz.getName(), null, true).waitForActivityWithTimeout(10000);
  }

  public static void actuallyCloseSoftKeyboard() throws Exception {
    Espresso.closeSoftKeyboard();
    Thread.sleep(800);
  }

  public static void typeTextAndClose(ViewInteraction view, String text) throws Exception {
    view.perform(typeText(text));
    actuallyCloseSoftKeyboard();
  }

}
