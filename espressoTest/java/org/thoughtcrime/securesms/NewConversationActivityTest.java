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

import static org.hamcrest.Matchers.allOf;

@LargeTest
public class NewConversationActivityTest extends TextSecureEspressoTestCase<ConversationListActivity> {

  final String[][] TEST_CONTACTS = {
      {"Nadezhda Tolokonnikova", "11111111111", "Nad", "ezh", "ova"},
      {"Jules Bonnot",           "22222222222", "Jul", "nno", "not"},
      {"Masha Kolenkia",         "33333333333", "Mas", "len", "kia"},
      {"Chairman Meow",          "44444444444", "Cha", "rma", "eow"},
      {"Clement Duval",          "55555555555", "Cle", "eme", "val"},
      {"Nestor Makhno",          "66666666666", "Nes", "sto", "hno"},
      {"Wilhelm Reich",          "77777777777", "Wil", "hel", "ich"}
  };

  public NewConversationActivityTest() {
    super(ConversationListActivity.class);
  }

  private void populateContacts() throws Exception {
    for(String[] TEST_CONTACT : TEST_CONTACTS) {
      EspressoUtil.addContact(getContext(), TEST_CONTACT[0], TEST_CONTACT[1]);
    }
  }

  private void clickNewConversation() throws Exception{
    ConversationListActivityActions.clickNewConversation();
    EspressoUtil.waitOn(NewConversationActivity.class);
    EspressoUtil.actuallyCloseSoftKeyboard();
  }

  @SuppressWarnings("unchecked")
  public void testContactFilterPrefix() throws Exception {
    populateContacts();
    loadActivity(ConversationListActivity.class, STATE_REGISTERED);
    clickNewConversation();

    for (String[] TEST_CONTACT : TEST_CONTACTS) {
      NewConversationActivityActions.filterNameOrNumber(TEST_CONTACT[2]);
      onView(allOf(withId(R.id.name), withText(TEST_CONTACT[0]))).check(matches(isDisplayed()));
    }
  }

  // this is known to fail on some devices, see #2378
  @SuppressWarnings("unchecked")
  public void testContactFilterMiddle() throws Exception {
    populateContacts();
    loadActivity(ConversationListActivity.class, STATE_REGISTERED);
    clickNewConversation();

    for (String[] TEST_CONTACT : TEST_CONTACTS) {
      NewConversationActivityActions.filterNameOrNumber(TEST_CONTACT[3]);
      onView(allOf(withId(R.id.name), withText(TEST_CONTACT[0]))).check(matches(isDisplayed()));
    }
  }

  // this is known to fail on some devices, see #2378
  @SuppressWarnings("unchecked")
  public void testContactFilterPostfix() throws Exception {
    populateContacts();
    loadActivity(ConversationListActivity.class, STATE_REGISTERED);
    clickNewConversation();

    for (String[] TEST_CONTACT : TEST_CONTACTS) {
      NewConversationActivityActions.filterNameOrNumber(TEST_CONTACT[4]);
      onView(allOf(withId(R.id.name), withText(TEST_CONTACT[0]))).check(matches(isDisplayed()));
    }
  }

  public void testNewConversationWithNonPushContact() throws Exception {
    populateContacts();
    loadActivity(ConversationListActivity.class, STATE_REGISTERED);
    clickNewConversation();

    NewConversationActivityActions.clickContactWithName("Chairman Meow");
    EspressoUtil.waitOn(ConversationActivity.class);
  }

  @SuppressWarnings("unchecked")
  public void testNewConversationWithUnknownWhenRegistered() throws Exception {
    loadActivity(ConversationListActivity.class, STATE_REGISTERED);
    clickNewConversation();

    NewConversationActivityActions.filterNameOrNumber("8888888888");
    NewConversationActivityActions.clickContactWithNumber("8888888888");
    EspressoUtil.waitOn(ConversationActivity.class);
  }

  @SuppressWarnings("unchecked")
  public void testNewConversationWithUnknownWhenUnregistered() throws Exception {
    loadActivity(ConversationListActivity.class, STATE_REGISTRATION_SKIPPED);
    clickNewConversation();

    NewConversationActivityActions.filterNameOrNumber("8888888888");
    NewConversationActivityActions.clickContactWithNumber("8888888888");
    EspressoUtil.waitOn(ConversationActivity.class);
  }

}
