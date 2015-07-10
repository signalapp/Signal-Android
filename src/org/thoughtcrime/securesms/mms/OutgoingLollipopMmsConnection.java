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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.SmsManager;
import android.util.Log;

import de.gdata.messaging.providers.MmsBodyProvider;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.SendConf;

public class OutgoingLollipopMmsConnection extends LollipopMmsConnection implements OutgoingMmsConnection {
  private static final String TAG    = OutgoingLollipopMmsConnection.class.getSimpleName();
  private static final String ACTION = OutgoingLollipopMmsConnection.class.getCanonicalName() + "MMS_SENT_ACTION";

  private byte[] response;

  public OutgoingLollipopMmsConnection(Context context) {
    super(context, ACTION);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP_MR1)
  @Override
  public synchronized void onResult(Context context, Intent intent) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP_MR1) {
      Log.w(TAG, "HTTP status: " + intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1));
    }

    response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA);
  }

  @Override
  @TargetApi(VERSION_CODES.LOLLIPOP)
  public @Nullable synchronized SendConf send(@NonNull byte[] pduBytes) throws UndeliverableMessageException {
    beginTransaction();
    try {
      MmsBodyProvider.Pointer pointer = MmsBodyProvider.makeTemporaryPointer(getContext());
      Util.copy(new ByteArrayInputStream(pduBytes), pointer.getOutputStream());

      SmsManager.getDefault().sendMultimediaMessage(getContext(),
                                                    pointer.getUri(),
                                                    null,
                                                    null,
                                                    getPendingIntent());

      waitForResult();

      Log.w(TAG, "MMS broadcast received and processed.");
      pointer.close();

      if (response == null) {
        throw new UndeliverableMessageException("Null response.");
      }

      return (SendConf) new PduParser(response).parse();
    } catch (IOException | TimeoutException e) {
      throw new UndeliverableMessageException(e);
    } finally {
      endTransaction();
    }
  }
}

