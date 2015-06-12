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

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.Espresso.openContextualActionModeOverflowMenu;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.thoughtcrime.securesms.EspressoUtil.typeTextAndCloseKeyboard;

public class ConversationActivityActions {

  public static void clickAddAttachment(Context context) throws Exception {
    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(R.string.conversation__menu_add_attachment)).perform(click());
  }

  public static void clickAllImages(Context context) throws Exception {
    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(R.string.conversation__menu_view_media)).perform(click());
  }

  public static void clickDeleteThread(Context context) throws Exception {
    openActionBarOverflowOrOptionsMenu(context);
    onView(withText(R.string.conversation__menu_delete_thread)).perform(click());
  }

  public static void clickForwardMessage() throws Exception {
    openContextualActionModeOverflowMenu();
    onView(withText(R.string.conversation_context__menu_forward_message)).perform(click());
  }

  public static void toggleEmojiKeyboard() throws Exception {
    onView(withId(R.id.emoji_toggle)).perform(click());
  }

  public static void typeMessage(String message) throws Exception {
    typeTextAndCloseKeyboard(onView(withId(R.id.embedded_text_editor)), message);
  }

  public static void clickSend() throws Exception {
    onView(withId(R.id.send_button)).perform(click());
  }

}
