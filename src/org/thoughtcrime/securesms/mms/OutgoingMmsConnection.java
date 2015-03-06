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
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPostHC4;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntityHC4;
import org.thoughtcrime.securesms.util.TelephonyUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;

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
  protected HttpUriRequest constructRequest(boolean useProxy)
      throws IOException
  {
    try {
      HttpPostHC4 request = new HttpPostHC4(apn.getMmsc());
      for (Header header : getBaseHeaders()) {
        request.addHeader(header);
      }

      request.setEntity(new ByteArrayEntityHC4(mms));
      if (useProxy) {
        HttpHost proxy = new HttpHost(apn.getProxy(), apn.getPort());
        request.setConfig(RequestConfig.custom().setProxy(proxy).build());
      }
      return request;
    } catch (IllegalArgumentException iae) {
      throw new IOException(iae);
    }
  }

  public void sendNotificationReceived(boolean usingMmsRadio, boolean useProxyIfAvailable)
      throws IOException
  {
    sendBytes(usingMmsRadio, useProxyIfAvailable);
  }

  public SendConf send(boolean useMmsRadio, boolean useProxyIfAvailable)  throws IOException {
    byte[] response = sendBytes(useMmsRadio, useProxyIfAvailable);
    return (SendConf) new PduParser(response).parse();
  }

  private byte[] sendBytes(boolean useMmsRadio, boolean useProxyIfAvailable) throws IOException {
    final boolean useProxy   = useProxyIfAvailable && apn.hasProxy();
    final String  targetHost = useProxy
                             ? apn.getProxy()
                             : Uri.parse(apn.getMmsc()).getHost();

    Log.w(TAG, "Sending MMS of length: " + mms.length
               + (useMmsRadio ? ", using mms radio" : "")
               + (useProxy ? ", using proxy" : ""));

    try {
      if (checkRouteToHost(context, targetHost, useMmsRadio)) {
        Log.w(TAG, "got successful route to host " + targetHost);
        byte[] response = makeRequest(useProxy);
        if (response != null) return response;
      }
    } catch (IOException ioe) {
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

      getApn(context, networkInfo.getExtraInfo());
      return true;
    } catch (ApnUnavailableException e) {
      Log.w(TAG, e);
      return false;
    }
  }
}
