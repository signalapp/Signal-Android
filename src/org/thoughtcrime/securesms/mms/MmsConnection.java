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
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.ConnectivityManager;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.database.ApnDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.TelephonyUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.util.Conversions;
import org.whispersystems.textsecure.util.Util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public abstract class MmsConnection {
  private static final String TAG = "MmsCommunication";

  public static final int MAX_REDIRECTS = 10;

  protected final Context context;
  protected final Apn     apn;

  protected MmsConnection(Context context, Apn apn) {
    this.context = context;
    this.apn     = apn;
  }

  protected static Apn getLocallyConfiguredApn(Context context) throws ApnUnavailableException {
    if (TextSecurePreferences.isUseLocalApnsEnabled(context)) {
      String mmsc = TextSecurePreferences.getMmscUrl(context);

      if (mmsc == null)
        throw new ApnUnavailableException("Malformed locally configured MMSC.");

      if (!mmsc.startsWith("http"))
        mmsc = "http://" + mmsc;

      String proxy = TextSecurePreferences.getMmscProxy(context);
      String port  = TextSecurePreferences.getMmscProxyPort(context);

      return new Apn(mmsc, proxy, port);
    }

    throw new ApnUnavailableException("No locally configured parameters available");
  }

  protected static Apn getLocalApn(Context context) throws ApnUnavailableException {
    if (TextSecurePreferences.isUseLocalApnsEnabled(context)) {
      return getLocallyConfiguredApn(context);
    } else {
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
  }

  public static Apn getApn(Context context, String apnName) throws ApnUnavailableException {
    Log.w(TAG, "Getting MMSC params for apn " + apnName);
    Cursor cursor = null;

    try {
      cursor = DatabaseFactory.getMmsDatabase(context).getCarrierMmsInformation(apnName);

      if (cursor == null || !cursor.moveToFirst()) {
        Log.w(TAG, "Android didn't have a result, querying local parameters.");
        return getLocalApn(context);
      }

      do {
        String mmsc  = cursor.getString(cursor.getColumnIndexOrThrow("mmsc"));
        String proxy = cursor.getString(cursor.getColumnIndexOrThrow("mmsproxy"));
        String port  = cursor.getString(cursor.getColumnIndexOrThrow("mmsport"));

        if (!Util.isEmpty(mmsc)) {
          Log.w(TAG, "Using Android-provided MMSC parameters.");
          return new Apn(mmsc, proxy, port);
        }

      } while (cursor.moveToNext());

      Log.w(TAG, "Android provided results were empty, querying local parameters.");
      return getLocalApn(context);
    } catch (SQLiteException sqe) {
      Log.w(TAG, sqe);
    } catch (SecurityException se) {
      Log.w(TAG, "Android won't let us query the APN database.");
      return getLocalApn(context);
    } catch (IllegalArgumentException iae) {
      Log.w(TAG, iae);
    } finally {
      if (cursor != null)
        cursor.close();
    }
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

  protected HttpURLConnection constructHttpClient(boolean useProxy)
      throws IOException
  {
    HttpURLConnection urlConnection;
    URL url = new URL(apn.getMmsc());

    if (apn.hasProxy() && useProxy) {
      Log.w(TAG, String.format("Constructing http client using a proxy: (%s:%d)", apn.getProxy(), apn.getPort()));
      Proxy proxyRoute = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(apn.getProxy(), apn.getPort()));
      Properties systemProperties = System.getProperties();
      systemProperties.setProperty("http.proxyHost",apn.getProxy());
      systemProperties.setProperty("http.proxyPort",Integer.toString(apn.getPort()));
      systemProperties.setProperty("https.proxyHost",apn.getProxy());
      systemProperties.setProperty("https.proxyPort",Integer.toString(apn.getPort()));
      urlConnection = (HttpURLConnection) url.openConnection();
    } else {
      Log.w(TAG, "Constructing http client without proxy");
      urlConnection = (HttpURLConnection) url.openConnection();
    }

    urlConnection.setInstanceFollowRedirects(false);
    urlConnection.setConnectTimeout(20*1000);
    urlConnection.setReadTimeout(20*1000);
    urlConnection.setUseCaches(false);
    urlConnection.setRequestProperty("User-Agent", "Android-Mms/2.0");
    urlConnection.setRequestProperty("Accept-Charset", "UTF-8");
    return urlConnection;
  }

  protected byte[] makeRequest(boolean useProxy) throws IOException {
    HttpURLConnection client = null;

    int redirects = MAX_REDIRECTS;
    final Set<String> previousUrls = new HashSet<String>();
    String currentUrl = apn.getMmsc();
    while (redirects-- > 0) {
      try {
        client = constructHttpClient(useProxy);
        Log.w(TAG, "connecting to " + currentUrl);
        client.connect();

        transact(client);

        Log.w(TAG, "* response code: " + client.getResponseCode() + "/" + client.getResponseMessage());
        switch (client.getResponseCode()) {
        case 200:
          return parseResponse(client.getInputStream());
        case 301:
        case 302:
          final String redirectUrl = client.getHeaderField("Location");
          Log.w(TAG, "* Location: " + redirectUrl);

          if (TextUtils.isEmpty(redirectUrl) || !(redirectUrl.startsWith("http://") || redirectUrl.startsWith("https://"))) {
            throw new IOException("Redirect location can't be handled");
          }

          previousUrls.add(currentUrl);
          if (previousUrls.contains(redirectUrl)) {
            throw new IOException("redirect loop detected");
          }
          currentUrl = redirectUrl;
          break;
        default:
          throw new IOException("unhandled response code");
        }
      } finally {
        if (client != null) client.disconnect();
      }
    }
    throw new IOException("max redirects hit");
  }

  protected void transact(HttpURLConnection client) throws IOException { }

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
      return !Util.isEmpty(proxy);
    }

    public String getMmsc() {
      return mmsc;
    }

    public String getProxy() {
      return hasProxy() ? proxy : null;
    }

    public int getPort() {
      return Util.isEmpty(port) ? 80 : Integer.parseInt(port);
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
