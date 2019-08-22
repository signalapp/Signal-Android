package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.logging.Log;
import android.widget.Toast;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Rfc5724Uri;

import java.net.URISyntaxException;

public class SmsSendtoActivity extends Activity {

  private static final String TAG = SmsSendtoActivity.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    startActivity(getNextIntent(getIntent()));
    finish();
    super.onCreate(savedInstanceState);
  }

  private Intent getNextIntent(Intent original) {
    DestinationAndBody destination;

    if (original.getAction().equals(Intent.ACTION_SENDTO)) {
      destination = getDestinationForSendTo(original);
    } else if (original.getData() != null && "content".equals(original.getData().getScheme())) {
      destination = getDestinationForSyncAdapter(original);
    } else {
      destination = getDestinationForView(original);
    }

    final Intent nextIntent;

    if (TextUtils.isEmpty(destination.destination)) {
      nextIntent = new Intent(this, NewConversationActivity.class);
      nextIntent.putExtra(ConversationActivity.TEXT_EXTRA, destination.getBody());
      Toast.makeText(this, R.string.ConversationActivity_specify_recipient, Toast.LENGTH_LONG).show();
    } else {
      Recipient recipient = Recipient.from(this, Address.fromExternal(this, destination.getDestination()), true);
      long      threadId  = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient);

      nextIntent = new Intent(this, ConversationActivity.class);
      nextIntent.putExtra(ConversationActivity.TEXT_EXTRA, destination.getBody());
      nextIntent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
      nextIntent.putExtra(ConversationActivity.ADDRESS_EXTRA, recipient.getAddress());
    }
    return nextIntent;
  }

  private @NonNull DestinationAndBody getDestinationForSendTo(Intent intent) {
    return new DestinationAndBody(intent.getData().getSchemeSpecificPart(),
                                  intent.getStringExtra("sms_body"));
  }

  private @NonNull DestinationAndBody getDestinationForView(Intent intent) {
    try {
      Rfc5724Uri smsUri = new Rfc5724Uri(intent.getData().toString());
      return new DestinationAndBody(smsUri.getPath(), smsUri.getQueryParams().get("body"));
    } catch (URISyntaxException e) {
      Log.w(TAG, "unable to parse RFC5724 URI from intent", e);
      return new DestinationAndBody("", "");
    }
  }

  private @NonNull DestinationAndBody getDestinationForSyncAdapter(Intent intent) {
    Cursor cursor = null;

    try {
      cursor = getContentResolver().query(intent.getData(), null, null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        return new DestinationAndBody(cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.Data.DATA1)), "");
      }

      return new DestinationAndBody("", "");
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  private static class DestinationAndBody {
    private final String destination;
    private final String body;

    private DestinationAndBody(String destination, String body) {
      this.destination = destination;
      this.body = body;
    }

    public String getDestination() {
      return destination;
    }

    public String getBody() {
      return body;
    }
  }
}
