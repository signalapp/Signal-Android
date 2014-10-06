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
import android.text.TextUtils;
import android.util.Log;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Request.Builder;
import com.squareup.okhttp.Response;

import org.thoughtcrime.securesms.database.ApnDatabase;
import org.thoughtcrime.securesms.util.TelephonyUtil;
import org.whispersystems.textsecure.util.Conversions;
import org.whispersystems.textsecure.util.Util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.concurrent.TimeUnit;

public abstract class MmsConnection {
  private static final String TAG = "MmsCommunication";

  protected final Context context;
  protected final Apn     apn;

  protected MmsConnection(Context context, Apn apn) {
    this.context = context;
    this.apn     = apn;
  }

  protected static Apn getLocalApn(Context context) throws ApnUnavailableException {
    try {
      Apn params = ApnDatabase.getInstance(context)
                              .getMmsConnectionParameters(TelephonyUtil.getMccMnc(context),
                                                          TelephonyUtil.getApn(context));

      if (params == null) {
        throw new ApnUnavailableException("No parameters available from ApnDefaults.");
      }

      return params;
    } catch (IOException ioe) {
      throw new ApnUnavailableException("ApnDatabase threw an IOException", ioe);
    }
  }

  public static Apn getApn(Context context, String apnName) throws ApnUnavailableException {
    Log.w(TAG, "Getting MMSC params for apn " + apnName);
    return getLocalApn(context);
  }

  protected static boolean checkRouteToHost(Context context, String host, boolean usingMmsRadio)
      throws IOException
  {
    InetAddress inetAddress = InetAddress.getByName(host);

    if (!usingMmsRadio) {
      if (inetAddress.isSiteLocalAddress()) {
        throw new IOException("RFC1918 address in non-MMS radio situation!");
      }

      return true;
    }

    Log.w(TAG, "Checking route to address: " + host + " , " + inetAddress.getHostAddress());

    byte[] ipAddressBytes = inetAddress.getAddress();

    if (ipAddressBytes != null && ipAddressBytes.length == 4) {
      int ipAddress               = Conversions.byteArrayToIntLittleEndian(ipAddressBytes, 0);
      ConnectivityManager manager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

      boolean routeToHostObtained = manager.requestRouteToHost(MmsRadio.TYPE_MOBILE_MMS, ipAddress);
      Log.w(TAG, "requestRouteToHost result: " + routeToHostObtained);
      return routeToHostObtained;
    }
    Log.w(TAG, "returning vacuous true");
    return true;
  }

  protected static byte[] parseResponse(InputStream is) throws IOException {
    InputStream           in   = new BufferedInputStream(is);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Util.copy(in, baos);

    Log.w(TAG, "Received full server response, " + baos.size() + " bytes");

    return baos.toByteArray();
  }

  protected OkHttpClient constructHttpClient(boolean useProxy)
      throws IOException {
    OkHttpClient client = new OkHttpClient();
    client.setConnectTimeout(20, TimeUnit.SECONDS);
    client.setReadTimeout(20, TimeUnit.SECONDS);
    client.setWriteTimeout(20, TimeUnit.SECONDS);

    if (apn.hasProxy() && useProxy) {
      Log.w(TAG, String.format("Constructing http client using a proxy: (%s:%d)", apn.getProxy(), apn.getPort()));
      Proxy proxyRoute = new Proxy(Type.HTTP, new InetSocketAddress(apn.getProxy(), apn.getPort()));
      client.setProxy(proxyRoute);
    }

    return client;
  }

  protected Request.Builder constructBaseRequest() {
    return new Builder().url(apn.getMmsc())
                        .header("User-Agent", "Android-Mms/2.0")
                        .header("Accept-Charset", "UTF-8");
  }

  protected byte[] makeRequest(boolean useProxy) throws IOException {
    String currentUrl = apn.getMmsc();
    Call call = constructCall(useProxy);
    Log.w(TAG, "connecting to " + currentUrl);
    Response response = call.execute();

    Log.w(TAG, "* response code: " + response.code());
    if (response.isSuccessful()) {
      return parseResponse(response.body().byteStream());
    }

    throw new IOException("unhandled response code");
  }

  protected abstract Call constructCall(boolean useProxy) throws IOException;

  public static class Apn {
    private final String mmsc;
    private final String proxy;
    private final String port;

    public Apn(String mmsc, String proxy, String port) {
      this.mmsc  = mmsc;
      this.proxy = proxy;
      this.port  = port;
    }

    public boolean hasProxy() {
      return !TextUtils.isEmpty(proxy);
    }

    public String getMmsc() {
      return mmsc;
    }

    public String getProxy() {
      return hasProxy() ? proxy : null;
    }

    public int getPort() {
      return TextUtils.isEmpty(port) ? 80 : Integer.parseInt(port);
    }

    @Override
    public String toString() {
      return Apn.class.getSimpleName() +
          "{ mmsc: \"" + mmsc + "\"" +
          ", proxy: " + (proxy == null ? "none" : '"' + proxy + '"') +
          ", port: " + (port == null ? "none" : port) + " }";
    }
  }

}
