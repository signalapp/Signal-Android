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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;

import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.SendConf;

public class OutgoingMmsConnection extends MmsConnection {
  private final static String TAG = OutgoingMmsConnection.class.getSimpleName();

  private final byte[] mms;

  public OutgoingMmsConnection(Context context, String apnName, byte[] mms) throws ApnUnavailableException {
    super(context, getApn(context, apnName));
    this.mms = mms;
  }

  @Override
  protected HttpURLConnection constructHttpClient(boolean useProxy)
      throws IOException
  {
    HttpURLConnection client = super.constructHttpClient(useProxy);
    client.setFixedLengthStreamingMode(mms.length);
    client.setDoInput(true);
    client.setDoOutput(true);
    client.setRequestMethod("POST");
    client.setRequestProperty("Content-Type", "application/vnd.wap.mms-message");
    client.setRequestProperty("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");
    client.setRequestProperty("x-wap-profile", "http://www.google.com/oha/rdf/ua-profile-kila.xml");
    return client;
  }

  @Override
  protected void transact(HttpURLConnection client) throws IOException {
    Log.w(TAG, "* writing mms payload, " + mms.length + " bytes");
    OutputStream out = client.getOutputStream();
    out.write(mms);
    out.flush();
    out.close();
    Log.w(TAG, "* payload sent");
  }

  public void sendNotificationReceived(boolean usingMmsRadio, boolean useProxyIfAvailable)
      throws IOException
  {
    sendBytes(usingMmsRadio, useProxyIfAvailable);
  }

  public SendConf send(boolean usingMmsRadio, boolean useProxyIfAvailable)  throws IOException {
    byte[] response = sendBytes(usingMmsRadio, useProxyIfAvailable);
    return (SendConf) new PduParser(response).parse();
  }

  private byte[] sendBytes(boolean usingMmsRadio, boolean useProxyIfAvailable) throws IOException {
    Log.w(TAG, "Sending MMS of length: " + mms.length + "." + (usingMmsRadio ? " using mms radio" : ""));
    try {
      if (useProxyIfAvailable && apn.hasProxy()) {
        if (checkRouteToHost(context, apn.getProxy(), usingMmsRadio)) {
          byte[] response = makeRequest(true);
          if (response != null) return response;
        }
      } else {
        if (checkRouteToHost(context, Uri.parse(apn.getMmsc()).getHost(), usingMmsRadio)) {
          byte[] response = makeRequest(false);
          if (response != null) return response;
        }
      }
    } catch (IOException ioe) {
      Log.w(TAG, "caught an IOException when checking host routes and making requests.");
      Log.w(TAG, ioe);
    }
    throw new IOException("Connection manager could not obtain route to host.");
  }

  public static boolean isConnectionPossible(Context context) {
    try {
      ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo         networkInfo         = connectivityManager.getNetworkInfo(MmsRadio.TYPE_MOBILE_MMS);
      if (networkInfo == null) {
        Log.w(TAG, "MMS network info was null, unsupported by this device");
        return false;
      }
      String apn = networkInfo.getExtraInfo();

      getApn(context, apn);
      return true;
    } catch (ApnUnavailableException e) {
      Log.w(TAG, e);
      return false;
    }
  }
}
