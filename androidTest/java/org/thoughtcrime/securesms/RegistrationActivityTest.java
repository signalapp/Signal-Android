package org.thoughtcrime.securesms;

import android.test.suitebuilder.annotation.LargeTest;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

@LargeTest
public class RegistrationActivityTest extends RoutedInstrumentationTestCase {
  private final static String TAG = RegistrationActivityTest.class.getSimpleName();

  public RegistrationActivityTest() {
    super();
  }

  @SuppressWarnings("unchecked")
  public void testLayout() throws Exception {
    waitOn(RegistrationActivity.class);
    onView(withId(R.id.registerButton)).check(matches(isDisplayed()));
  }

}
