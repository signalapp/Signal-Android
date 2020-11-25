package org.thoughtcrime.securesms.webrtc;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;

import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.service.WebRtcCallService;

public class VoiceCallShare extends Activity {
  
  private static final String TAG = VoiceCallShare.class.getSimpleName();
  
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    if (getIntent().getData() != null && "content".equals(getIntent().getData().getScheme())) {
      Cursor cursor = null;
      
      try {
        cursor = getContentResolver().query(getIntent().getData(), null, null, null, null);

        if (cursor != null && cursor.moveToNext()) {
          String  destination = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.Data.DATA1));
          Address address     = Address.fromExternal(this, destination);
          
          if (!TextUtils.isEmpty(destination)) {
            Intent serviceIntent = new Intent(this, WebRtcCallService.class);
            serviceIntent.setAction(WebRtcCallService.ACTION_OUTGOING_CALL);
            serviceIntent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, address);
            startService(serviceIntent);

            Intent activityIntent = new Intent(this, WebRtcCallActivity.class);
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
