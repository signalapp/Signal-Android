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
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.telephony.SmsManager;
import org.thoughtcrime.securesms.logging.Log;

import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.pdu_alt.NotifyRespInd;
import com.google.android.mms.pdu_alt.PduComposer;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduParser;
import com.google.android.mms.pdu_alt.RetrieveConf;

import org.thoughtcrime.securesms.providers.MmsBodyProvider;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class IncomingLollipopMmsConnection extends LollipopMmsConnection implements IncomingMmsConnection {

  public  static final String ACTION = IncomingLollipopMmsConnection.class.getCanonicalName() + "MMS_DOWNLOADED_ACTION";
  private static final String TAG    = IncomingLollipopMmsConnection.class.getSimpleName();

  public IncomingLollipopMmsConnection(Context context) {
    super(context, ACTION);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  @Override
  public synchronized void onResult(Context context, Intent intent) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP_MR1) {
      Log.i(TAG, "HTTP status: " + intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1));
    }
    Log.i(TAG, "code: " + getResultCode() + ", result string: " + getResultData());
  }

  @Override
  @TargetApi(VERSION_CODES.LOLLIPOP)
  public synchronized @Nullable RetrieveConf retrieve(@NonNull String contentLocation,
                                                      byte[] transactionId,
                                                      int subscriptionId) throws MmsException
  {
    beginTransaction();

    try {
      MmsBodyProvider.Pointer pointer = MmsBodyProvider.makeTemporaryPointer(getContext());

      Log.i(TAG, "downloading multimedia from " + contentLocation + " to " + pointer.getUri());

      SmsManager smsManager;

      if (VERSION.SDK_INT >= 22 && subscriptionId != -1) {
        smsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
      } else {
        smsManager = SmsManager.getDefault();
      }

      smsManager.downloadMultimediaMessage(getContext(),
                                           contentLocation,
                                           pointer.getUri(),
                                           null,
                                           getPendingIntent());

      waitForResult();

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      Util.copy(pointer.getInputStream(), baos);
      pointer.close();

      Log.i(TAG, baos.size() + "-byte response: ");// + Hex.dump(baos.toByteArray()));

      RetrieveConf retrieved = (RetrieveConf) new PduParser(baos.toByteArray()).parse();

      if (retrieved == null) return null;

      sendRetrievedAcknowledgement(transactionId, retrieved.getMmsVersion(), subscriptionId);
      return retrieved;
    } catch (IOException | TimeoutException e) {
      Log.w(TAG, e);
      throw new MmsException(e);
    } finally {
      endTransaction();
    }
  }

  private void sendRetrievedAcknowledgement(byte[] transactionId, int mmsVersion, int subscriptionId) {
    try {
      NotifyRespInd retrieveResponse = new NotifyRespInd(mmsVersion, transactionId, PduHeaders.STATUS_RETRIEVED);
      new OutgoingLollipopMmsConnection(getContext()).send(new PduComposer(getContext(), retrieveResponse).make(), subscriptionId);
    } catch (UndeliverableMessageException e) {
      Log.w(TAG, e);
    } catch (InvalidHeaderValueException e) {
      Log.w(TAG, e);
    }
  }
}
