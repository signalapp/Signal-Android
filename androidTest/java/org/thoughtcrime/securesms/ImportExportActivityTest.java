package org.thoughtcrime.securesms;

import android.test.suitebuilder.annotation.LargeTest;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.swipeLeft;
import static android.support.test.espresso.action.ViewActions.swipeRight;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

/**
 * rhodey
 */
@LargeTest
public class ImportExportActivityTest extends SkipRegistrationInstrumentationTestCase {

  public ImportExportActivityTest() {
    super();
  }

  private void checkImportLayout() throws Exception {
    onView(withId(R.id.import_sms)).check(matches(isDisplayed()));
    onView(withId(R.id.import_encrypted_backup)).check(matches(isDisplayed()));
    onView(withId(R.id.import_plaintext_backup)).check(matches(isDisplayed()));
  }

  private void checkExportLayout() throws Exception {
    onView(withId(R.id.export_plaintext_backup)).check(matches(isDisplayed()));
  }

  public void testLayout() throws Exception {
    new ConversationListActivityTest(getContext()).testClickImportExport();
    waitOn(ImportExportActivity.class);

    checkImportLayout();
    onView(withId(R.id.import_sms)).perform(swipeLeft());
    checkExportLayout();
    onView(withId(R.id.export_plaintext_backup)).perform(swipeRight());
  }

}
