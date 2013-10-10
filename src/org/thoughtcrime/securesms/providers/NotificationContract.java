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

import java.util.List;

import org.thoughtcrime.securesms.database.NotificationsDatabase;

import android.net.Uri;
import android.provider.BaseColumns;

public class NotificationContract {
  
  interface ContactNotificationsColumns {
    String CONTACT_ID               = NotificationsDatabase.CONTACT_ID;
    String CONTACT_LOOKUPKEY        = NotificationsDatabase.CONTACT_LOOKUPKEY;
    String CONTACT_NAME             = NotificationsDatabase.CONTACT_NAME;
    String SUMMARY                  = NotificationsDatabase.SUMMARY;
    String ENABLED                  = NotificationsDatabase.ENABLED;
    String SOUND                    = NotificationsDatabase.SOUND;
    String VIBRATE                  = NotificationsDatabase.VIBRATE;
    String VIBRATE_PATTERN          = NotificationsDatabase.VIBRATE_PATTERN;
    String VIBRATE_PATTERN_CUSTOM   = NotificationsDatabase.VIBRATE_PATTERN_CUSTOM;
    String LED                      = NotificationsDatabase.LED;
    String LED_COLOR                = NotificationsDatabase.LED_COLOR;
    String LED_PATTERN              = NotificationsDatabase.LED_PATTERN;
    String LED_PATTERN_CUSTOM       = NotificationsDatabase.LED_PATTERN_CUSTOM;
  }

  public static class ContactNotifications implements ContactNotificationsColumns, BaseColumns {
    public static final String CONTENT_AUTHORITY = "org.thoughtcrime.notificationprovider.securesms";
    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    public static final String PATH_CONTACTS = "contacts";
    public static final String PATH_CONTACTS_LOOKUP = "contactslookup";
  
    public static final Uri CONTENT_URI =
            BASE_CONTENT_URI.buildUpon().appendPath(PATH_CONTACTS).build();
    public static final Uri CONTENT_LOOKUP_URI =
            BASE_CONTENT_URI.buildUpon().appendPath(PATH_CONTACTS_LOOKUP).build();
  
    public static final String[] PROJECTION_SUMMARY =
            new String[] { _ID, CONTACT_NAME, SUMMARY };

    public static final String DEFAULT_SORT = CONTACT_NAME + ", " + _ID;
  
    public static Uri buildContactUri(String id) {
        return CONTENT_URI.buildUpon().appendPath(id).build();
    }
  
    public static Uri buildContactUri(long id) {
        return CONTENT_URI.buildUpon().appendPath(String.valueOf(id)).build();
    }
  
    public static String getContactId(Uri uri) {
        final int size = uri.getPathSegments().size();
        if (size >= 2 && size <= 3) {
            return uri.getLastPathSegment();
        }
        return null;
    }
  
    public static Uri buildLookupUri(String lookupKey) {
        return buildLookupUri(lookupKey, null);
    }
  
    public static Uri buildLookupUri(String lookupKey, String contactId) {
        if (lookupKey == null) {
            return null;
        }
        if (contactId == null) {
            return CONTENT_LOOKUP_URI.buildUpon().appendPath(lookupKey).build();
        }
        return CONTENT_LOOKUP_URI.buildUpon()
                .appendPath(lookupKey).appendPath(contactId).build();
    }

    public static String getLookupKey(Uri uri) {
        final List<String> segments = uri.getPathSegments();
        if (segments.size() > 1) {
            // getPathSegments() decodes the segment, so we need to encode again as we want
            // to keep LOOKUP_URI in encoded format
            return Uri.encode(uri.getPathSegments().get(1));
        }
        return null;
    }
  }

}
