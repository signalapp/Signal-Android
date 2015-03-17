package org.thoughtcrime.securesms;

import android.content.Context;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class SkipRegistrationInstrumentationTestCase extends RoutedInstrumentationTestCase {
  private static final String TAG = SkipRegistrationInstrumentationTestCase.class.getSimpleName();

  public SkipRegistrationInstrumentationTestCase() {
    super();
  }

  public SkipRegistrationInstrumentationTestCase(Context context) {
    super(context);
  }

  @Override
  public void initAppState() throws Exception {
    super.initAppState();
    TextSecurePreferences.setPromptedPushRegistration(getContext(), true);
  }

}
