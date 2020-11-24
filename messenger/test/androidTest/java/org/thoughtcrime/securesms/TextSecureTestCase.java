package org.thoughtcrime.securesms;

import android.content.Context;
import android.test.InstrumentationTestCase;

public class TextSecureTestCase extends InstrumentationTestCase {

  @Override
  public void setUp() {
    System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().getPath());
  }

  protected Context getContext() {
    return getInstrumentation().getContext();
  }
}
