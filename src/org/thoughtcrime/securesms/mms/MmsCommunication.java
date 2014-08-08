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
import android.util.Log;

import org.thoughtcrime.securesms.database.DatabaseFactory;
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
import java.util.ArrayList;
import java.util.List;

public class MmsCommunication {
  private static final String TAG = "MmsCommunication";

  protected static MmsConnectionParameters getLocallyConfiguredMmsConnectionParameters(Context context)
      throws ApnUnavailableException
  {
    if (TextSecurePreferences.isUseLocalApnsEnabled(context)) {
      String mmsc = TextSecurePreferences.getMmscUrl(context);

      if (mmsc == null)
        throw new ApnUnavailableException("Malformed locally configured MMSC.");

      if (!mmsc.startsWith("http"))
        mmsc = "http://" + mmsc;

      String proxy = TextSecurePreferences.getMmscProxy(context);
      String port  = TextSecurePreferences.getMmscProxyPort(context);

      return new MmsConnectionParameters(mmsc, proxy, port);
    }

    throw new ApnUnavailableException("No locally configured parameters available");
  }

  protected static MmsConnectionParameters getLocalMmsConnectionParameters(Context context)
      throws ApnUnavailableException
  {
    if (TextSecurePreferences.isUseLocalApnsEnabled(context)) {
      return getLocallyConfiguredMmsConnectionParameters(context);
    } else {
      MmsConnectionParameters params = ApnDefaults.getMmsConnectionParameters(context);

      if (params == null) {
        throw new ApnUnavailableException("No parameters available from ApnDefaults.");
      }

      return params;
    }
  }

  protected static MmsConnectionParameters getMmsConnectionParameters(Context context, String apn)
      throws ApnUnavailableException
  {
    Log.w(TAG, "Getting MMSC params for apn " + apn);
    Cursor cursor = null;

    try {
      cursor = DatabaseFactory.getMmsDatabase(context).getCarrierMmsInformation(apn);

      if (cursor == null || !cursor.moveToFirst()) {
        MmsConnectionParameters parameters = getLocalMmsConnectionParameters(context);
        Log.w(TAG, "Android didn't have a result, using MMSC parameters: " + parameters.get().get(0).getMmsc() + " // " + parameters.get().get(0).getProxy() + " // " + parameters.get().get(0).getPort());
        return parameters;
      }

      do {
        String mmsc  = cursor.getString(cursor.getColumnIndexOrThrow("mmsc"));
        String proxy = cursor.getString(cursor.getColumnIndexOrThrow("mmsproxy"));
        String port  = cursor.getString(cursor.getColumnIndexOrThrow("mmsport"));

        if (!Util.isEmpty(mmsc)) {
          Log.w(TAG, "Using Android-provided MMSC parameters: " + mmsc + " // " + proxy + " // " + port);
          return new MmsConnectionParameters(mmsc, proxy, port);
        }

      } while (cursor.moveToNext());

      MmsConnectionParameters parameters = getLocalMmsConnectionParameters(context);
      Log.w(TAG, "Android didn't have a result, using MMSC parameters: " + parameters.get().get(0).getMmsc() + " // " + parameters.get().get(0).getProxy() + " // " + parameters.get().get(0).getPort());
      return parameters;
    } catch (SQLiteException sqe) {
      Log.w(TAG, sqe);
    } catch (SecurityException se) {
      Log.w(TAG, "Android won't let us query the APN database.");
      return getLocalMmsConnectionParameters(context);
    } catch (IllegalArgumentException iae) {
      Log.w(TAG, iae);
    } finally {
      if (cursor != null)
        cursor.close();
    }
    return getLocalMmsConnectionParameters(context);
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

      return manager.requestRouteToHost(MmsRadio.TYPE_MOBILE_MMS, ipAddress);
    }
    return true;
  }

  protected static byte[] parseResponse(InputStream is) throws IOException {
    InputStream in = new BufferedInputStream(is);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Util.copy(in, baos);
    Log.w(TAG, "received full server response, " + baos.size() + " bytes");
    return baos.toByteArray();
  }

  protected static HttpURLConnection constructHttpClient(String urlString, String proxy, int port)
      throws IOException
  {
    HttpURLConnection urlConnection;
    URL url = new URL(urlString);
    if (proxy != null) {
      Log.w(TAG, "constructing http client using a proxy");
      Proxy proxyRoute = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy, port));
      urlConnection = (HttpURLConnection) url.openConnection(proxyRoute);
    } else {
      Log.w(TAG, "constructing http client without proxy");
      urlConnection = (HttpURLConnection) url.openConnection();
    }
    urlConnection.setConnectTimeout(20*1000);
    urlConnection.setReadTimeout(20*1000);
    urlConnection.setUseCaches(false);
    urlConnection.setRequestProperty("User-Agent", "Android-Mms/2.0");
    urlConnection.setRequestProperty("Accept-Charset", "UTF-8");
    return urlConnection;
  }

  protected static class MmsConnectionParameters {
    public class Apn {
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
        if (!hasProxy())
          return null;

        return proxy;
      }

      public int getPort() {
        if (Util.isEmpty(port))
          return 80;

        return Integer.parseInt(port);
      }
    }

    private List<Apn> apn = new ArrayList<Apn>();

    public MmsConnectionParameters(String mmsc, String proxy, String port) {
      apn.add(new Apn(mmsc, proxy, port));
    }

    public MmsConnectionParameters add(String mmsc, String proxy, String port) {
      apn.add(new Apn(mmsc, proxy, port));
      return this;
    }

    public List<Apn> get() {
      return apn;
    }
  }

}
