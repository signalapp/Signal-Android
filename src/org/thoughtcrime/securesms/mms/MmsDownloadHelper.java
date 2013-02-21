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
import android.net.http.AndroidHttpClient;
import android.util.Log;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;

import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.RetrieveConf;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class MmsDownloadHelper extends MmsCommunication {

  private static byte[] makeRequest(Context context, MmsConnectionParameters connectionParameters, String url)
      throws ClientProtocolException, IOException
  {
    AndroidHttpClient client = null;

    try {
      client            = constructHttpClient(context, connectionParameters);
      URI targetUrl     = new URI(url.trim());
      HttpHost target   = new HttpHost(targetUrl.getHost(), targetUrl.getPort(), HttpHost.DEFAULT_SCHEME_NAME);
      HttpGet request   = new HttpGet(url.trim());

      request.setParams(client.getParams());
      request.addHeader("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");

      HttpResponse response = client.execute(target, request);
      StatusLine status     = response.getStatusLine();

      if (status.getStatusCode() != 200)
        throw new IOException("Non-successful HTTP response: " + status.getReasonPhrase());

      return parseResponse(response.getEntity());
    } catch (URISyntaxException use) {
      Log.w("MmsDownloadHelper", use);
      throw new IOException("Couldn't parse URI");
    } finally {
      if (client != null)
        client.close();
    }
  }

  public static RetrieveConf retrieveMms(Context context, String url, String apn,
                                         boolean usingMmsRadio, boolean proxyIfPossible)
      throws IOException
  {
    MmsConnectionParameters connectionParameters;

    try {
      connectionParameters = getMmsConnectionParameters(context, apn, proxyIfPossible);
    } catch (ApnUnavailableException aue) {
      Log.w("MmsDownloadHelper", aue);
      connectionParameters = new MmsConnectionParameters(null, null, null);
    }

    checkRouteToHost(context, connectionParameters, url, usingMmsRadio);

    byte[] pdu = makeRequest(context, connectionParameters, url);

    RetrieveConf retrieved = (RetrieveConf)new PduParser(pdu).parse();

    if (retrieved == null) {
      throw new IOException("Bad retrieved PDU");
    }

    return retrieved;
  }
}
