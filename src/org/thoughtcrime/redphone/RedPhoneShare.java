package org.thoughtcrime.redphone;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;

public class RedPhoneShare extends Activity {

  private static final String TAG = RedPhone.class.getSimpleName();

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    if (getIntent().getData() != null && "content".equals(getIntent().getData().getScheme())) {
      Cursor cursor = null;

      try {
        cursor = getContentResolver().query(getIntent().getData(), null, null, null, null);

        if (cursor != null && cursor.moveToNext()) {
          String destination = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.Data.DATA1));

          if (!TextUtils.isEmpty(destination)) {
            Intent serviceIntent = new Intent(this, RedPhoneService.class);
            serviceIntent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
            serviceIntent.putExtra(RedPhoneService.EXTRA_REMOTE_NUMBER, destination);
            startService(serviceIntent);

            Intent activityIntent = new Intent(this, RedPhone.class);
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(activityIntent);
          }
        }
      } finally {
        if (cursor != null) cursor.close();
      }
    }

    finish();
  }

}
