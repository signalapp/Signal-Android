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

import android.content.Context;

import org.thoughtcrime.securesms.R;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

public class ConversationListActivityActions {

  public static void dismissReminder() throws Exception {
    onView(withId(R.id.cancel)).perform(click());
  }

  public static void clickNewConversation() throws Exception {
    onView(withId(R.id.fab)).perform(click());
  }

  public static void clickNewGroup(Context context) throws Exception {
    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(R.string.text_secure_normal__menu_new_group)).perform(click());
  }

  public static void clickLock(Context context) throws Exception {
    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(R.string.text_secure_normal__menu_clear_passphrase)).perform(click());
  }

  public static void clickMarkAllRead(Context context) throws Exception {
    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(R.string.text_secure_normal__mark_all_as_read)).perform(click());
  }

  public static void clickImportExport(Context context) throws Exception {
    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(R.string.arrays__import_export)).perform(click());
  }

  public static void clickMyIdentity(Context context) throws Exception {
    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(R.string.arrays__your_identity_key)).perform(click());
  }

  public static void clickSettings(Context context) throws Exception {
    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(R.string.text_secure_normal__menu_settings)).perform(click());
  }

  public static void deleteSelected() throws Exception {
    onView(withId(R.id.menu_delete_selected)).perform(click());
    onView(withText(R.string.delete)).perform(click());
  }

  public static void cancelDeleteSelected() throws Exception {
    onView(withId(R.id.menu_delete_selected)).perform(click());
    onView(withText(android.R.string.cancel)).perform(click());
  }

}
