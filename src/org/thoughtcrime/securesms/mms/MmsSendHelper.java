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
import android.net.ConnectivityManager;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.thoughtcrime.securesms.service.MmscProcessor;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.SendConf;

public class MmsSendHelper extends MmsCommunication {

  private static byte[] makePost(Context context, MmsConnectionParameters parameters, byte[] mms) throws ClientProtocolException, IOException {
    AndroidHttpClient client = null;

    try {
      Log.w("MmsSender", "Sending MMS1 of length: " + (mms != null ? mms.length : "null"));
      client                 = constructHttpClient(context, parameters);
      URI targetUrl          = new URI(parameters.getMmsc());

      if (Util.isEmpty(targetUrl.getHost()))
        throw new IOException("Invalid target host: " + targetUrl.getHost() + " , " + targetUrl);

      HttpHost target        = new HttpHost(targetUrl.getHost(), targetUrl.getPort(), HttpHost.DEFAULT_SCHEME_NAME);
      HttpPost request       = new HttpPost(parameters.getMmsc());
      ByteArrayEntity entity = new ByteArrayEntity(mms);

      entity.setContentType("application/vnd.wap.mms-message");

      request.setEntity(entity);
      request.setParams(client.getParams());
      request.addHeader("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");
      request.addHeader("x-wap-profile", "http://www.google.com/oha/rdf/ua-profile-kila.xml");
      HttpResponse response = client.execute(target, request);
      StatusLine status     = response.getStatusLine();

      if (status.getStatusCode() != 200)
        throw new IOException("Non-successful HTTP response: " + status.getReasonPhrase());

      return parseResponse(response.getEntity());
    } catch (URISyntaxException use) {
      Log.w("MmsSendHelper", use);
      throw new IOException("Couldn't parse URI.");
    } finally {
      if (client != null)
        client.close();
    }
  }

  public static void sendNotificationReceived(Context context, byte[] mms, String apn,
                                              boolean usingMmsRadio, boolean useProxyIfAvailable)
    throws IOException
  {
    sendBytes(context, mms, apn, usingMmsRadio, useProxyIfAvailable);
  }

  public static SendConf sendMms(Context context, byte[] mms, String apn,
                                 boolean usingMmsRadio, boolean useProxyIfAvailable)
      throws IOException
  {
    byte[] response = sendBytes(context, mms, apn, usingMmsRadio, useProxyIfAvailable);

    if (response == null) {
      throw new IOException("Got null response!");
    }

    return (SendConf) new PduParser(response).parse();
  }

  private static byte[] sendBytes(Context context, byte[] mms, String apn,
                                  boolean usingMmsRadio, boolean useProxyIfAvailable)
    throws IOException
  {
    Log.w("MmsSender", "Sending MMS of length: " + mms.length);
    try {
      MmsConnectionParameters parameters = getMmsConnectionParameters(context, apn, useProxyIfAvailable);
      checkRouteToHost(context, parameters, parameters.getMmsc(), usingMmsRadio);
      return makePost(context, parameters, mms);
    } catch (ApnUnavailableException aue) {
      Log.w("MmsSender", aue);
      throw new IOException("Failed to get MMSC information...");
    }
  }

  public static boolean hasNecessaryApnDetails(Context context) {
    try {
      ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
      String apn = connectivityManager.getNetworkInfo(MmscProcessor.TYPE_MOBILE_MMS).getExtraInfo();

      MmsCommunication.getMmsConnectionParameters(context, apn, true);
      return true;
    } catch (ApnUnavailableException e) {
      Log.w("MmsSendHelper", e);
      return false;
    }
  }
}
