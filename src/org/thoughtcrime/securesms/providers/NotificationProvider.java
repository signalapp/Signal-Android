/**
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

package org.thoughtcrime.securesms.providers;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NotificationsDatabase;
import org.thoughtcrime.securesms.providers.NotificationContract.ContactNotifications;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;

public class NotificationProvider extends ContentProvider {

  public static final String CONTENT_TYPE =
      "vnd.android.cursor.dir/vnd.thoughtcrime.securesms.notification";
  public static final String CONTENT_ITEM_TYPE =
      "vnd.android.cursor.item/vnd.thoughtcrime.securesms.notification";

  private static final int CONTACTS = 100;
  private static final int CONTACTS_ID = 101;
  private static final int CONTACTS_LOOKUP = 102;

  private static final UriMatcher uriMatcher;

  static {
    final String authority = ContactNotifications.CONTENT_AUTHORITY;
    final String contactsPath = ContactNotifications.PATH_CONTACTS;
    final String contactsLookupPath = ContactNotifications.PATH_CONTACTS_LOOKUP;

    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    uriMatcher.addURI(authority, contactsPath, CONTACTS);
    uriMatcher.addURI(authority, contactsPath + "/#", CONTACTS_ID);
    uriMatcher.addURI(authority, contactsLookupPath + "/*", CONTACTS_LOOKUP);
    uriMatcher.addURI(authority, contactsLookupPath + "/*/#", CONTACTS_LOOKUP);
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    NotificationsDatabase db = DatabaseFactory.getNotificationsDatabase(getContext());
    final int match = uriMatcher.match(uri);
    int count = 0;
    switch (match) {
    case CONTACTS_ID:
      count = db.deleteNotification(ContactNotifications.getContactId(uri));
      break;
    default:
      throw new UnsupportedOperationException("Unknown uri: " + uri);
    }
    getContext().getContentResolver().notifyChange(uri, null);

    return count;
  }

  @Override
  public String getType(Uri uri) {
    final int match = uriMatcher.match(uri);
    switch (match) {
    case CONTACTS:
      return CONTENT_TYPE;
    case CONTACTS_ID:
      return CONTENT_ITEM_TYPE;
    default:
      throw new UnsupportedOperationException("Unknown uri: " + uri);
    }
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {

    final int match = uriMatcher.match(uri);
    Uri newUri = null;
    final long id;
    switch (match) {
    case CONTACTS:
      NotificationsDatabase db = DatabaseFactory.getNotificationsDatabase(getContext());
      id = db.insertNotification(values);
      newUri = ContactNotifications.buildContactUri(id);
      updateContactNotificationSummary(newUri);
      break;
    default:
      throw new UnsupportedOperationException("Unknown uri: " + uri);
    }

    if (id == -1) {
      return null;
    }

    getContext().getContentResolver().notifyChange(newUri, null);

    return newUri;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
      String sortOrder) {

    NotificationsDatabase db = DatabaseFactory.getNotificationsDatabase(getContext());
    Cursor c = null;

    final int match = uriMatcher.match(uri);
    switch (match) {
    case CONTACTS:
      c = db.getNotifications();
      break;

    case CONTACTS_ID:
      c = db.getNotification(ContactNotifications.getContactId(uri));
      break;

    case CONTACTS_LOOKUP:
      c =
      db.getNotification(ContactNotifications.getContactId(uri),
          ContactNotifications.getLookupKey(uri));
      break;

    default:
      throw new UnsupportedOperationException("Unknown uri: " + uri);
    }

    if (c != null)
      c.setNotificationUri(getContext().getContentResolver(), uri);

    return c;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

    NotificationsDatabase db = DatabaseFactory.getNotificationsDatabase(getContext());

    final int match = uriMatcher.match(uri);
    int count = 0;
    switch (match) {
    case CONTACTS_ID:
      count = db.updateNotification(Long.parseLong(ContactNotifications.getContactId(uri)), values);
      if (!values.containsKey(NotificationsDatabase.SUMMARY)
          && !values.containsKey(NotificationsDatabase.CONTACT_NAME)) {
        updateContactNotificationSummary(uri);
      }
      break;
    default:
      throw new UnsupportedOperationException("Unknown uri: " + uri);
    }

    getContext().getContentResolver().notifyChange(uri, null);

    return count;
  }

  /**
   * Update the custom contact notification summary field.
   * 
   * @param uri
   */
  private void updateContactNotificationSummary(Uri uri) {
    final Cursor c = query(uri, null, null, null, null);
    final String one = "1";
    if (c == null) {
      return;
    }

    if (c.getCount() != 1) {
      c.close();
      return;
    }

    c.moveToFirst();

    Resources res = getContext().getResources();

    StringBuilder summary = new StringBuilder();

    if (!one.equals(c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.ENABLED)))) {
      summary.append(res.getString(R.string.preferences__summary_notifications_disabled));
    } else {
      summary.append(res.getString(R.string.preferences__summary_notifications_enabled));

      if (one.equals(c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.VIBRATE)))) {
        summary.append(", " + res.getString(R.string.preferences__summary_vibrate_enabled));
      }
      if (one.equals(c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.LED)))) {

        // To find the color string, we first find the stored value's position in the non-localized
        // array of values
        // and then extract the corresponding localized string from the entries array
        String[] colorEntries = res.getStringArray(R.array.pref_led_color_entries);
        String[] colorValues = res.getStringArray(R.array.pref_led_color_values);

        String ledColor = c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.LED_COLOR));

        int index = java.util.Arrays.asList(colorValues).indexOf(ledColor);
        String colorName = java.util.Arrays.asList(colorEntries).get(index);

        summary.append(", " + res.getString(R.string.preferences__summary_led_color, colorName));
      }
    }

    ContentValues vals = new ContentValues();
    vals.put(NotificationsDatabase.SUMMARY, summary.toString());
    update(uri, vals, null, null);

    c.close();
  }

}
