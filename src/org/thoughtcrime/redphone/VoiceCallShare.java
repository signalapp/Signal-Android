package org.thoughtcrime.redphone;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

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
          String destination = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.Data.DATA1));

          if (!TextUtils.isEmpty(destination)) {
            Recipients                       recipients   = RecipientFactory.getRecipientsFromString(this, destination, true);
            DirectoryHelper.UserCapabilities capabilities = DirectoryHelper.getUserCapabilities(this, recipients);

            if (TextSecurePreferences.isWebrtcCallingEnabled(this) &&
                capabilities.getVideoCapability() == DirectoryHelper.UserCapabilities.Capability.SUPPORTED)
            {
              Intent serviceIntent = new Intent(this, WebRtcCallService.class);
              serviceIntent.setAction(WebRtcCallService.ACTION_OUTGOING_CALL);
              serviceIntent.putExtra(WebRtcCallService.EXTRA_REMOTE_NUMBER, destination);
              startService(serviceIntent);

              Intent activityIntent = new Intent(this, WebRtcCallActivity.class);
              activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(activityIntent);
            } else {
              Intent serviceIntent = new Intent(this, RedPhoneService.class);
              serviceIntent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
              serviceIntent.putExtra(RedPhoneService.EXTRA_REMOTE_NUMBER, destination);
              startService(serviceIntent);

              Intent activityIntent = new Intent(this, RedPhone.class);
              activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(activityIntent);
            }
          }
        }
      } finally {
        if (cursor != null) cursor.close();
      }
    }

    finish();
  }

}
