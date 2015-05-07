package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
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
    String body = "";
    String data = "";

    if (original.getAction().equals(Intent.ACTION_SENDTO)) {
      body = original.getStringExtra("sms_body");
      data = original.getData().getSchemeSpecificPart();
    } else {
      try {
        Rfc5724Uri smsUri = new Rfc5724Uri(original.getData().toString());
        body = smsUri.getQueryParams().get("body");
        data = smsUri.getPath();
      } catch (URISyntaxException e) {
        Log.w(TAG, "unable to parse RFC5724 URI from intent", e);
      }
    }

    Recipients recipients = RecipientFactory.getRecipientsFromString(this, data, false);
    long       threadId   = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipients);

    final Intent nextIntent;
    if (recipients == null || recipients.isEmpty()) {
      nextIntent = new Intent(this, NewConversationActivity.class);
      nextIntent.putExtra(ConversationActivity.DRAFT_TEXT_EXTRA, body);
      Toast.makeText(this, R.string.ConversationActivity_specify_recipient, Toast.LENGTH_LONG).show();
    } else {
      nextIntent = new Intent(this, ConversationActivity.class);
      nextIntent.putExtra(ConversationActivity.DRAFT_TEXT_EXTRA, body);
      nextIntent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
      nextIntent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    }
    return nextIntent;
  }
}
