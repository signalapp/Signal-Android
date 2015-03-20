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

import android.test.suitebuilder.annotation.LargeTest;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

@LargeTest
public class GroupCreateActivityTest extends SkipRegistrationInstrumentationTestCase {

  public GroupCreateActivityTest() {
    super();
  }

  public void testLayout() throws Exception {
    new ConversationListActivityTest(getContext()).testClickNewGroup();
    waitOn(GroupCreateActivity.class);

    onView(withId(R.id.push_disabled)).check(matches(isDisplayed()));
    onView(withId(R.id.push_disabled_reason))
          .check(matches(withText(R.string.GroupCreateActivity_you_dont_support_push)));

    waitOn(GroupCreateActivity.class);
  }

}
