package org.thoughtcrime.securesms;

import android.test.suitebuilder.annotation.LargeTest;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.whispersystems.libaxolotl.IdentityKey;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

/**
 * rhodey
 */
@LargeTest
public class ViewLocalIdentityActivityTest extends SkipRegistrationInstrumentationTestCase {

  public ViewLocalIdentityActivityTest() {
    super();
  }

  public void testLayout() throws Exception {
    new ConversationListActivityTest(getContext()).testClickMyIdentity();
    waitOn(ViewLocalIdentityActivity.class);

    final IdentityKey idKey = IdentityKeyUtil.getIdentityKey(getContext());
    onView(withId(R.id.identity_fingerprint)).check(matches(withText(idKey.getFingerprint())));
  }

}
