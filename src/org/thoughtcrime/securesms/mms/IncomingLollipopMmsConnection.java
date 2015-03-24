/**
 * Copyright (C) 2015 Open Whisper Systems
 *
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
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.RetrieveConf;

public class IncomingLollipopMmsConnection extends BroadcastReceiver implements IncomingMmsConnection {
  public static final String ACTION = IncomingLollipopMmsConnection.class.getCanonicalName() + "MMS_DOWNLOADED_ACTION";
  private static final String TAG = IncomingLollipopMmsConnection.class.getSimpleName();

  private Context context;
  private boolean finished;

  public IncomingLollipopMmsConnection(Context context) {
    super();
    this.context = context;
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  @Override
  public synchronized void onReceive(Context context, Intent intent) {
    Log.w(TAG, "onReceive()");
    if (!ACTION.equals(intent.getAction())) {
      Log.w(TAG, "received broadcast with unexpected action " + intent.getAction());
      return;
    }
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP_MR1) {
      Log.w(TAG, "HTTP status: " + intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1));
    }
    Log.w(TAG, "code: " + getResultCode() + ", result string: " + getResultData());

    finished = true;
    notifyAll();
  }

  @Override
  @TargetApi(VERSION_CODES.LOLLIPOP)
  public synchronized RetrieveConf retrieve(String contentLocation, byte[] transactionId) throws MmsException {
    context.getApplicationContext().registerReceiver(this, new IntentFilter(ACTION));
    long nonce = System.currentTimeMillis();
    try {
      PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1, new Intent(ACTION), PendingIntent.FLAG_ONE_SHOT);
      Uri contentUri = ContentUris.withAppendedId(MmsBodyProvider.CONTENT_URI, nonce);
      Log.w(TAG, "downloading multimedia from " + contentLocation + " to " + contentUri);
      SmsManager.getDefault().downloadMultimediaMessage(context, contentLocation, contentUri, null, pendingIntent);

      while (!finished) Util.wait(this, 30000);

      context.getApplicationContext().unregisterReceiver(this);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      Util.copy(context.getContentResolver().openInputStream(contentUri), baos);

      Log.w(TAG, baos.size() + "-byte response: " + Hex.dump(baos.toByteArray()));

      return (RetrieveConf) new PduParser(baos.toByteArray()).parse();
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      throw new MmsException(ioe);
    }
  }
}
