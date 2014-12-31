package org.thoughtcrime.securesms;

import static android.support.test.espresso.Espresso.*;
import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.matcher.ViewMatchers.*;
import static android.support.test.espresso.assertion.ViewAssertions.*;

import android.test.suitebuilder.annotation.LargeTest;

@LargeTest
public class RegistrationActivityTest extends RoutedInstrumentationTestCase {
  private final static String TAG = RegistrationActivityTest.class.getSimpleName();

  public RegistrationActivityTest() {
    super();
  }

  @SuppressWarnings("unchecked")
  public void testRegistrationButtons() throws Exception {
    waitOn(RegistrationActivity.class);
    onView(withId(R.id.registerButton)).check(matches(isDisplayed()));
    onView(withId(R.id.skipButton)).check(matches(isDisplayed())).perform(click());
  }
}
