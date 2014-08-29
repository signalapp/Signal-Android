/**
 * Copyright (C) 2011 Whisper Systems
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

public class MmsDownloadHelper extends MmsCommunication {
  private static final String TAG = MmsDownloadHelper.class.getSimpleName();

  private static byte[] makeRequest(String url, String proxy, int proxyPort)
      throws IOException
  {
    HttpURLConnection client = null;

    try {
      client = constructHttpClient(url, proxy, proxyPort);

      client.setDoInput(true);
      client.setRequestMethod("GET");
      client.setRequestProperty("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");

      Log.w(TAG, "Connecting to " + url);
      client.connect();

      int responseCode = client.getResponseCode();

      Log.w(TAG, "Response code: " + responseCode + "/" + client.getResponseMessage());

      if (responseCode != 200) {
        throw new IOException("non-200 response");
      }

      return parseResponse(client.getInputStream());
    } finally {
      if (client != null) client.disconnect();
    }
  }

  public static boolean isMmsConnectionParametersAvailable(Context context, String apn) {
    try {
      getMmsConnectionParameters(context, apn);
      return true;
    } catch (ApnUnavailableException e) {
      return false;
    }
  }

  public static RetrieveConf retrieveMms(Context context, String url, String apn,
                                         boolean usingMmsRadio, boolean proxyIfPossible)
      throws IOException, ApnUnavailableException
  {
    MmsConnectionParameters connectionParameters = getMmsConnectionParameters(context, apn);
    byte[] pdu = null;

    for (MmsConnectionParameters.Apn param : connectionParameters.get()) {
      try {
        if (proxyIfPossible && param.hasProxy()) {
          if (checkRouteToHost(context, param.getProxy(), usingMmsRadio)) {
            pdu = makeRequest(url, param.getProxy(), param.getPort());
          }
        } else {
          if (checkRouteToHost(context, Uri.parse(url).getHost(), usingMmsRadio)) {
            pdu = makeRequest(url, null, -1);
          }
        }

        if (pdu != null) break;
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
      }
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
