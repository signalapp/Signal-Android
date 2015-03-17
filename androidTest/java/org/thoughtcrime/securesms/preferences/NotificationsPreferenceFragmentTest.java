package org.thoughtcrime.securesms.preferences;

import android.test.suitebuilder.annotation.LargeTest;

import org.hamcrest.Matchers;
import org.thoughtcrime.securesms.ApplicationPreferencesActivityTest;
import org.thoughtcrime.securesms.SkipRegistrationInstrumentationTestCase;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.PreferenceMatchers.withKey;
import static android.support.test.espresso.matcher.ViewMatchers.isChecked;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isNotChecked;

/**
 * rhodey
 */
@LargeTest
public class NotificationsPreferenceFragmentTest extends SkipRegistrationInstrumentationTestCase {

  public NotificationsPreferenceFragmentTest() {
    super();
  }

  private void checkAllPreferencesDisplayed() throws Exception {
    onData(Matchers.<Object>allOf(withKey("pref_key_enable_notifications")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("pref_key_ringtone")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("pref_key_vibrate")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("pref_led_color")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("pref_led_blink")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("pref_key_inthread_notifications")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("pref_repeat_alerts")))
          .check(matches(isDisplayed()));
  }

  private void checkViewsMatchPreferences() throws Exception {
    if (TextSecurePreferences.isNotificationsEnabled(getContext())) {
      isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_key_enable_notifications"))));
    } else {
      isNotChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_key_enable_notifications"))));
    }

    if (TextSecurePreferences.isNotificationVibrateEnabled(getContext())) {
      isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_key_vibrate"))));
    } else {
      isNotChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_key_vibrate"))));
    }

    if (TextSecurePreferences.isInThreadNotifications(getContext())) {
      isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_key_inthread_notifications"))));
    } else {
      isNotChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_key_inthread_notifications"))));
    }
  }

  private void clickNotificationsSettingAndTestState() throws Exception {
    new ApplicationPreferencesActivityTest(getContext()).testClickNotificationsSetting();
    checkAllPreferencesDisplayed();
    checkViewsMatchPreferences();
  }

  public void testEnableNotifications() throws Exception {
    clickNotificationsSettingAndTestState();

    if (!TextSecurePreferences.isNotificationsEnabled(getContext())) {
      onData(Matchers.<Object>allOf(withKey("pref_key_enable_notifications")))
            .perform(click());
      isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_key_enable_notifications"))));
    } else {
      isNotChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_key_enable_notifications"))));
    }
  }

}
