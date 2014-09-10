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
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.RetrieveConf;

public class MmsDownloadHelper extends MmsCommunication {
  private static final String TAG = MmsDownloadHelper.class.getSimpleName();

  private static byte[] makeRequest(String url, String proxy, int proxyPort)
      throws IOException
  {
    HttpURLConnection client = null;

    int redirects = MAX_REDIRECTS;
    final Set<String> previousUrls = new HashSet<String>();
    String currentUrl = url;
    while (redirects-- > 0) {
      if (previousUrls.contains(currentUrl)) {
        throw new IOException("redirect loop detected");
      }
      try {
        client = constructHttpClient(currentUrl, proxy, proxyPort);

        client.setDoInput(true);
        client.setRequestMethod("GET");
        client.setRequestProperty("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");

        Log.w(TAG, "connecting to " + currentUrl);
        client.connect();

        int responseCode = client.getResponseCode();
        Log.w(TAG, "* response code: " + responseCode + "/" + client.getResponseMessage());

        if (responseCode == 301 || responseCode == 302) {
          final String redirectUrl = client.getHeaderField("Location");
          Log.w(TAG, "* Location: " + redirectUrl);
          if (TextUtils.isEmpty(redirectUrl)) {
            throw new IOException("Got redirect response code, but Location header was empty or missing");
          }
          previousUrls.add(currentUrl);
          currentUrl = redirectUrl;
        } else if (responseCode == 200) {
          final InputStream is = client.getInputStream();
          return parseResponse(is);
        } else {
          throw new IOException("unhandled response code");
        }
      } finally {
        if (client != null) client.disconnect();
      }
    }
    throw new IOException("max redirects hit");
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
