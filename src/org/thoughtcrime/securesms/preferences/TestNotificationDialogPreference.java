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

package org.thoughtcrime.securesms.preferences;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.providers.NotificationContract.ContactNotifications;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.preference.DialogPreference;
import android.support.v4.app.NotificationCompat;
import android.util.AttributeSet;
import android.view.View;

public class TestNotificationDialogPreference extends DialogPreference {
  private Context context;
  private String rowId = null;

  public TestNotificationDialogPreference(Context _context, AttributeSet attrs) {
    super(_context, attrs);
    context = _context;
  }

  public TestNotificationDialogPreference(Context _context, AttributeSet attrs, int defStyle) {
    super(_context, attrs, defStyle);
    context = _context;
  }

  public void setRowId(long rowId) {
    this.rowId = String.valueOf(rowId);
  }

  @Override
  public void onDismiss(DialogInterface dialog) {
    super.onDismiss(dialog);
  }

  @Override
  protected View onCreateDialogView() {

    // Create a test SmsMmsMessage
    String testPhone = "123-456-7890";
    String contactLookup = null;
    String sysContactId = null;

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

    builder.setSmallIcon(R.drawable.icon_notification);
    PendingIntent pendingIntent =
        PendingIntent.getActivity(context, 0, new Intent(), Intent.FLAG_ACTIVITY_NEW_TASK);
    builder.setContentIntent(pendingIntent);

    // If contactId is set, use its notification details else just use a default.
    if (rowId != null) {
      Cursor contactCursor =
          context.getContentResolver().query(ContactNotifications.buildContactUri(rowId), null,
              null, null, null);
      if (contactCursor != null && contactCursor.moveToFirst()) {
        testPhone =
            contactCursor.getString(contactCursor
                .getColumnIndexOrThrow(ContactNotifications.CONTACT_NAME));
        sysContactId =
            contactCursor.getString(contactCursor
                .getColumnIndexOrThrow(ContactNotifications.CONTACT_ID));
        contactLookup =
            contactCursor.getString(contactCursor
                .getColumnIndexOrThrow(ContactNotifications.CONTACT_LOOKUPKEY));
        contactCursor.close();
      }
    }

    if (sysContactId != null) {
      MessageNotifier.setNotificationAlarms(context, builder, true, contactLookup, sysContactId);
    } else {
      MessageNotifier.setNotificationAlarms(context, builder, true);
    }

    builder.setContentTitle(testPhone);
    builder.setContentText(context.getString(R.string.preferences__notif_test_title));

    NotificationManager nm =
        (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
    nm.notify(0, builder.build());

    return super.onCreateDialogView();
  }

}
