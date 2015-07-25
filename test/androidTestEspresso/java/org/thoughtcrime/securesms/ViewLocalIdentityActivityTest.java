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

import android.support.test.espresso.matcher.ViewMatchers;
import android.test.suitebuilder.annotation.LargeTest;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.whispersystems.libaxolotl.IdentityKey;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

@LargeTest
public class ViewLocalIdentityActivityTest extends TextSecureEspressoTestCase<ConversationListActivity> {

  public ViewLocalIdentityActivityTest() {
    super(ConversationListActivity.class);
  }

  private void clickMyIdentity() throws Exception {
    loadActivity(ConversationListActivity.class, STATE_REGISTRATION_SKIPPED);
    ConversationListActivityActions.clickMyIdentity(getContext());
    EspressoUtil.waitOn(ViewLocalIdentityActivity.class);
  }

  public void testLayout() throws Exception {
    clickMyIdentity();
    final IdentityKey idKey = IdentityKeyUtil.getIdentityKey(getContext());
    onView(ViewMatchers.withId(R.id.identity_fingerprint)).check(matches(withText(idKey.getFingerprint())));
  }

}
