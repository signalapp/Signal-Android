package org.thoughtcrime.securesms.service;

import android.app.IntentService;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.widget.Toast;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.Rfc5724Uri;

import java.net.URISyntaxException;
import java.net.URLDecoder;

public class QuickResponseService extends IntentService {

  private static final String TAG = Log.tag(QuickResponseService.class);

  public QuickResponseService() {
    super("QuickResponseService");
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(intent.getAction())) {
      Log.w(TAG, "Received unknown intent: " + intent.getAction());
      return;
    }

    if (KeyCachingService.isLocked(this)) {
      Log.w(TAG, "Got quick response request when locked...");
      Toast.makeText(this, R.string.QuickResponseService_quick_response_unavailable_when_Signal_is_locked, Toast.LENGTH_LONG).show();
      return;
    }

    try {
      Rfc5724Uri uri        = new Rfc5724Uri(intent.getDataString());
      String     content    = intent.getStringExtra(Intent.EXTRA_TEXT);
      String     number     = uri.getPath();

      if (number.contains("%")){
        number = URLDecoder.decode(number);
      }

      Recipient recipient = Recipient.external(this, number);

      if (!TextUtils.isEmpty(content)) {
        MessageSender.send(this, OutgoingMessage.sms(recipient, content), -1, MessageSender.SendType.SIGNAL, null, null);
      }
    } catch (URISyntaxException e) {
      Toast.makeText(this, R.string.QuickResponseService_problem_sending_message, Toast.LENGTH_LONG).show();
      Log.w(TAG, e);
    }
  }
}
