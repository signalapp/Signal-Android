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
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.util.Conversions;
import org.whispersystems.textsecure.util.Util;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class MmsCommunication {

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
    Cursor cursor = null;

    try {
      cursor = DatabaseFactory.getMmsDatabase(context).getCarrierMmsInformation(apn);

      if (cursor == null || !cursor.moveToFirst())
        return getLocalMmsConnectionParameters(context);

      do {
        String mmsc  = cursor.getString(cursor.getColumnIndexOrThrow("mmsc"));
        String proxy = cursor.getString(cursor.getColumnIndexOrThrow("mmsproxy"));
        String port  = cursor.getString(cursor.getColumnIndexOrThrow("mmsport"));

        if (!Util.isEmpty(mmsc))
          return new MmsConnectionParameters(mmsc, proxy, port);

      } while (cursor.moveToNext());

    } catch (SQLiteException sqe) {
      Log.w("MmsCommunication", sqe);
    } catch (SecurityException se) {
      Log.i("MmsCommunication", "Couldn't write APN settings, expected. msg: " + se.getMessage());
    } catch (IllegalArgumentException iae) {
      Log.w("MmsCommunication", iae);
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

    Log.w("MmsCommunication", "Checking route to address: " + host + " , " + inetAddress.getHostAddress());

    byte[] ipAddressBytes = inetAddress.getAddress();

    if (ipAddressBytes != null && ipAddressBytes.length == 4) {
      int ipAddress               = Conversions.byteArrayToIntLittleEndian(ipAddressBytes, 0);
      ConnectivityManager manager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

      return manager.requestRouteToHost(MmsRadio.TYPE_MOBILE_MMS, ipAddress);
    }
    return true;
  }

  protected static AndroidHttpClient constructHttpClient(Context context, String proxy, int port) {
    AndroidHttpClient client = AndroidHttpClient.newInstance("Android-Mms/2.0", context);
    HttpParams params        = client.getParams();
    HttpProtocolParams.setContentCharset(params, "UTF-8");
    HttpConnectionParams.setSoTimeout(params, 20 * 1000);

    if (proxy != null) {
      ConnRouteParams.setDefaultProxy(params, new HttpHost(proxy, port));
    }

    return client;
  }

  protected static byte[] parseResponse(HttpEntity entity) throws IOException {
    if (entity == null || entity.getContentLength() == 0)
      return null;

    if (entity.getContentLength() < 0)
      throw new IOException("Unknown content length!");

    byte[] responseBytes            = new byte[(int)entity.getContentLength()];
    DataInputStream dataInputStream = new DataInputStream(entity.getContent());
    dataInputStream.readFully(responseBytes);
    dataInputStream.close();

    entity.consumeContent();
    return responseBytes;
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
