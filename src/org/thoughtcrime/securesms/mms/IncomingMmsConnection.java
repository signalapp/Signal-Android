/**
 * Copyright (C) 2014 Open Whisper Systems
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

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;

import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.RetrieveConf;

public class IncomingMmsConnection extends MmsConnection {
  private static final String TAG = IncomingMmsConnection.class.getSimpleName();

  public IncomingMmsConnection(Context context, Apn apn) {
    super(context, apn);
  }

  public IncomingMmsConnection(Context context, String apnName) throws ApnUnavailableException {
    super(context, getApn(context, apnName));
  }

  @Override
  protected HttpURLConnection constructHttpClient(boolean useProxy) throws IOException {
    HttpURLConnection client = super.constructHttpClient(useProxy);
    client.setDoInput(true);
    client.setRequestMethod("GET");
    client.setRequestProperty("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");
    return client;
  }

  public static boolean isConnectionPossible(Context context, String apn) {
    try {
      getApn(context, apn);
      return true;
    } catch (ApnUnavailableException e) {
      return false;
    }
  }

  public RetrieveConf retrieve(boolean usingMmsRadio, boolean proxyIfPossible)
      throws IOException, ApnUnavailableException
  {
    byte[] pdu = null;

    try {
      if (proxyIfPossible && apn.hasProxy()) {
        if (checkRouteToHost(context, apn.getProxy(), usingMmsRadio)) {
          pdu = makeRequest(true);
        }
      } else {
        if (checkRouteToHost(context, Uri.parse(apn.getMmsc()).getHost(), usingMmsRadio)) {
          pdu = makeRequest(false);
        }
      }
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
    }

    if (pdu == null) {
      throw new IOException("Connection manager could not obtain route to host.");
    }

    RetrieveConf retrieved = (RetrieveConf)new PduParser(pdu).parse();

    if (retrieved == null) {
      Log.w(TAG, "Couldn't parse PDU, raw server response: " + Arrays.toString(pdu));
      throw new IOException("Bad retrieved PDU");
    }

    return retrieved;
  }
}
