package org.thoughtcrime.securesms;

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecretUtil;

public class RoutedInstrumentationTestCase extends ActivityInstrumentationTestCase2<RoutingActivity> {
  private static final String TAG = RoutedInstrumentationTestCase.class.getSimpleName();

  public RoutedInstrumentationTestCase() {
    super(RoutingActivity.class);
  }

  @Override
  public void setUp() throws Exception {
    System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().getPath());
    super.setUp();
    clearSharedPrefs();
    getActivity();
  }

  protected void clearSharedPrefs() {
    PreferenceManager.getDefaultSharedPreferences(getInstrumentation().getTargetContext())
                     .edit().clear().commit();
    getInstrumentation().getTargetContext().getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0)
                                           .edit().clear().commit();
  }

  protected static void waitOn(Class<? extends Activity> clazz) {
    Log.w(TAG, "waiting for " + clazz.getName());
    new ActivityMonitor(clazz.getName(), null, true).waitForActivityWithTimeout(10000);
  }
}
