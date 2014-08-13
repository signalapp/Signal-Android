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
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import org.whispersystems.textsecure.util.Util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.SendConf;

public class MmsSendHelper extends MmsCommunication {
  private final static String TAG = MmsSendHelper.class.getSimpleName();

  private static byte[] makePost(String url, String proxy, int proxyPort, byte[] mms)
      throws IOException
  {
    if (mms == null) return null;

    HttpURLConnection client = null;

    try {
      client = constructHttpClient(url, proxy, proxyPort);
      client.setFixedLengthStreamingMode(mms.length);
      client.setDoInput(true);
      client.setDoOutput(true);
      client.setRequestMethod("POST");
      client.setRequestProperty("Content-Type", "application/vnd.wap.mms-message");
      client.setRequestProperty("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");
      client.setRequestProperty("x-wap-profile", "http://www.google.com/oha/rdf/ua-profile-kila.xml");

      Log.w(TAG, "Connecting to " + url);
      client.connect();

      Log.w(TAG, "Writing mms payload, " + mms.length + " bytes");
      OutputStream out = client.getOutputStream();
      out.write(mms);
      out.flush();
      out.close();

      Log.w(TAG, "Payload sent");

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
    return (SendConf) new PduParser(response).parse();
  }

  private static byte[] sendBytes(Context context, byte[] mms, String apn,
                                  boolean usingMmsRadio, boolean useProxyIfAvailable)
    throws IOException
  {
    Log.w(TAG, "Sending MMS of length: " + mms.length + "." + (usingMmsRadio ? " using mms radio" : ""));
    try {
      MmsConnectionParameters parameters = getMmsConnectionParameters(context, apn);

      for (MmsConnectionParameters.Apn param : parameters.get()) {
        try {
          if (useProxyIfAvailable && param.hasProxy()) {
            if (checkRouteToHost(context, param.getProxy(), usingMmsRadio)) {
              byte[] response = makePost(param.getMmsc(), param.getProxy(), param.getPort(), mms);
              if (response != null) return response;
            }
          } else {
            if (checkRouteToHost(context, Uri.parse(param.getMmsc()).getHost(), usingMmsRadio)) {
              byte[] response = makePost(param.getMmsc(), null, -1, mms);
              if (response != null) return response;
            }
          }
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
        }
      }

      throw new IOException("Connection manager could not obtain route to host.");
    } catch (ApnUnavailableException aue) {
      Log.w(TAG, aue);
      throw new IOException("Failed to get MMSC information...");
    }
  }

  public static boolean hasNecessaryApnDetails(Context context) {
    try {
      ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo         networkInfo         = connectivityManager.getNetworkInfo(MmsRadio.TYPE_MOBILE_MMS);
      if (networkInfo == null) {
        Log.w(TAG, "MMS network info was null, unsupported by this device");
        return false;
      }
      String apn = networkInfo.getExtraInfo();

      MmsCommunication.getMmsConnectionParameters(context, apn);
      return true;
    } catch (ApnUnavailableException e) {
      Log.w("MmsSendHelper", e);
      return false;
    }
  }
}
