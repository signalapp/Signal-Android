package org.thoughtcrime.securesms.mms;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.telephony.SmsManager;
import android.util.Log;

import org.thoughtcrime.securesms.providers.MmsBodyProvider;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.SendConf;

public class OutgoingLollipopMmsConnection extends BroadcastReceiver implements OutgoingMmsConnection {
  private static final String TAG = OutgoingLollipopMmsConnection.class.getSimpleName();
  private static final String ACTION = OutgoingLollipopMmsConnection.class.getCanonicalName() + "MMS_SENT_ACTION";

  private Context context;
  private byte[] response;
  private boolean finished;
  private long messageId;
  private byte[] pduBytes;

  public OutgoingLollipopMmsConnection(Context context, byte[] pduBytes, long messageId) {
    this.context = context;
    this.pduBytes = pduBytes;
    this.messageId = messageId;
  }

  @TargetApi(VERSION_CODES.LOLLIPOP_MR1)
  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w(TAG, "onReceive()");
    if (!ACTION.equals(intent.getAction())) {
      Log.w(TAG, "received broadcast with unexpected action " + intent.getAction());
      return;
    }
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP_MR1) {
      Log.w(TAG, "HTTP status: " + intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1));
    }

    response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA);
    finished = true;
    synchronized (this) {
      notifyAll();
    }
  }

  @Override
  @TargetApi(VERSION_CODES.LOLLIPOP)
  public SendConf send() throws UndeliverableMessageException {
    context.getApplicationContext().registerReceiver(this, new IntentFilter(ACTION));
    try {
      Uri contentUri = ContentUris.withAppendedId(MmsBodyProvider.CONTENT_URI, messageId);
      Util.copy(new ByteArrayInputStream(pduBytes), context.getContentResolver().openOutputStream(contentUri, "w"));

      SmsManager.getDefault().sendMultimediaMessage(context, contentUri, null, null,
                                                    PendingIntent.getBroadcast(context, 1, new Intent(ACTION), PendingIntent.FLAG_ONE_SHOT));

      synchronized (this) {
        while (!finished) Util.wait(this, 30000);
      }
      Log.w(TAG, "MMS broadcast received and processed.");
      context.getApplicationContext().unregisterReceiver(this);
      context.getContentResolver().delete(contentUri, null, null);

      return (SendConf) new PduParser(response).parse();
    } catch (IOException ioe) {
      throw new UndeliverableMessageException(ioe);
    }
  }
}

