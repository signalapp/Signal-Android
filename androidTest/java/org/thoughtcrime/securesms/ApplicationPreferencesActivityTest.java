package org.thoughtcrime.securesms;

import android.content.Context;
import android.test.suitebuilder.annotation.LargeTest;

import org.hamcrest.Matchers;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.PreferenceMatchers.withKey;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;

/**
 * rhodey
 */
@LargeTest
public class ApplicationPreferencesActivityTest extends SkipRegistrationInstrumentationTestCase {

  public ApplicationPreferencesActivityTest() {
    super();
  }

  public ApplicationPreferencesActivityTest(Context context) {
    super(context);
  }

  private void checkAllPreferencesDisplayed() throws Exception {
    onData(Matchers.<Object>allOf(withKey("preference_category_sms_mms")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("preference_category_notifications")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("preference_category_app_protection")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("preference_category_appearance")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("preference_category_storage")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("preference_category_advanced")))
          .check(matches(isDisplayed()));
  }

  private void clickSettingsAndTestState() throws Exception {
    new ConversationListActivityTest(getContext()).testClickSettings();
    waitOn(ApplicationPreferencesActivity.class);
    checkAllPreferencesDisplayed();
  }

  public void testClickNotificationsSetting() throws Exception {
    clickSettingsAndTestState();
    onData(Matchers.<Object>allOf(withKey("preference_category_notifications"))).perform(click());
  }

  public void testClickAppProtectionSetting() throws Exception {
    clickSettingsAndTestState();
    onData(Matchers.<Object>allOf(withKey("preference_category_app_protection"))).perform(click());
  }

}
