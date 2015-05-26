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

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.test.espresso.NoActivityResumedException;
import android.support.test.espresso.ViewInteraction;
import android.util.Log;

import java.util.ArrayList;

import static android.support.test.espresso.action.ViewActions.replaceText;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.Espresso.closeSoftKeyboard;
import static android.support.test.espresso.Espresso.pressBack;

public class EspressoUtil {
  private final static String TAG = EspressoUtil.class.getSimpleName();

  public static void waitOn(Class<? extends Activity> clazz) {
    Log.w(TAG, "waiting for " + clazz.getName());
    new ActivityMonitor(clazz.getName(), null, true).waitForActivityWithTimeout(10000);
  }

  public static void actuallyCloseSoftKeyboard() throws Exception {
    closeSoftKeyboard();
    Thread.sleep(800);
  }

  public static void typeTextAndCloseKeyboard(ViewInteraction view, String text) throws Exception {
    view.perform(typeText(text));
    actuallyCloseSoftKeyboard();
  }

  public static void replaceTextAndCloseKeyboard(ViewInteraction view, String text) throws Exception {
    view.perform(replaceText(text));
    actuallyCloseSoftKeyboard();
  }

  public static void closeAllActivities() throws Exception {
    for (int i = 0; i < 10; i++) {
      try {

        pressBack();

      } catch (NoActivityResumedException e) {
        Log.d(TAG, "you made me do this, android");
        return;
      }
    }

    throw new IllegalStateException("what are you doing with 10 open activities?!");
  }

  public static long addContact(Context context, String name, String number) throws Exception {
    ArrayList<ContentProviderOperation> operations = new ArrayList<>();

    operations.add(ContentProviderOperation
        .newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
        .build());

    operations.add(ContentProviderOperation
        .newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
        .build());

      operations.add(ContentProviderOperation.
          newInsert(ContactsContract.Data.CONTENT_URI)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
          .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
          .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
          .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
          .build());

    return context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations).length;
  }

  public static void removeAllContacts(Context context) throws Exception {
    ContentResolver contentResolver = context.getContentResolver();
    Cursor          cursor          = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

    try {

      while (cursor.moveToNext()) {
        String contactKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
        Uri    contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, contactKey);
        contentResolver.delete(contactUri, null, null);
      }

    } finally {
      cursor.close();
    }
  }
}
