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
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.pdu_alt.NotifyRespInd;
import com.google.android.mms.pdu_alt.PduComposer;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduParser;
import com.google.android.mms.pdu_alt.RetrieveConf;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.providers.MmsBodyProvider;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

public class IncomingLollipopMmsConnection extends LollipopMmsConnection implements IncomingMmsConnection {

  public  static final String ACTION = IncomingLollipopMmsConnection.class.getCanonicalName() + "MMS_DOWNLOADED_ACTION";
  private static final String TAG    = Log.tag(IncomingLollipopMmsConnection.class);

  public IncomingLollipopMmsConnection(Context context) {
    super(context, ACTION);
  }

  @Override
  public synchronized void onResult(Context context, Intent intent) {
    if (VERSION.SDK_INT >= 22) {
      Log.i(TAG, "HTTP status: " + intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1));
    }
    Log.i(TAG, "code: " + getResultCode() + ", result string: " + getResultData());
  }

  @Override
  public synchronized @Nullable RetrieveConf retrieve(@NonNull String contentLocation,
                                                      byte[] transactionId,
                                                      int subscriptionId) throws MmsException
  {
    beginTransaction();

    try {
      MmsBodyProvider.Pointer pointer = MmsBodyProvider.makeTemporaryPointer(getContext());

      final String transactionIdString = Util.toIsoString(transactionId);
      Log.i(TAG, String.format(Locale.ENGLISH, "Downloading subscriptionId=%s multimedia from '%s' [transactionId='%s'] to '%s'",
                                               subscriptionId,
                                               contentLocation,
                                               transactionIdString,
                                               pointer.getUri()));

      SmsManager smsManager;

      if (VERSION.SDK_INT >= 22 && subscriptionId != -1) {
        smsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
      } else {
        smsManager = SmsManager.getDefault();
      }

      final Bundle configOverrides = smsManager.getCarrierConfigValues();

      if (configOverrides.getBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID)) {
        if (!contentLocation.contains(transactionIdString)) {
          Log.i(TAG, "Appending transactionId to contentLocation at the direction of CarrierConfigValues. New location: " + contentLocation);
          contentLocation += transactionIdString;
        } else {
          Log.i(TAG, "Skipping 'append transaction id' as contentLocation already contains it");
        }
      }

      if (TextUtils.isEmpty(configOverrides.getString(SmsManager.MMS_CONFIG_USER_AGENT))) {
        configOverrides.remove(SmsManager.MMS_CONFIG_USER_AGENT);
      }

      if (TextUtils.isEmpty(configOverrides.getString(SmsManager.MMS_CONFIG_UA_PROF_URL))) {
        configOverrides.remove(SmsManager.MMS_CONFIG_UA_PROF_URL);
      }
      
      smsManager.downloadMultimediaMessage(getContext(),
                                           contentLocation,
                                           pointer.getUri(),
                                           configOverrides,
                                           getPendingIntent());

      waitForResult();

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      StreamUtil.copy(pointer.getInputStream(), baos);
      pointer.close();

      Log.i(TAG, baos.size() + "-byte response: ");// + Hex.dump(baos.toByteArray()));

      Bundle  configValues            = smsManager.getCarrierConfigValues();
      boolean parseContentDisposition = configValues.getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION);

      RetrieveConf retrieved;

      try {
        retrieved = (RetrieveConf) new PduParser(baos.toByteArray(), parseContentDisposition).parse();
      } catch (AssertionError | NullPointerException e) {
        Log.w(TAG, "Badly formatted MMS message caused the parser to fail.", e);
        throw new MmsException(e);
      }

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
