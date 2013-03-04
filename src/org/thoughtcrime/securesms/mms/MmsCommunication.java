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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.service.MmsDownloader;
import org.thoughtcrime.securesms.util.Conversions;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;

public class MmsCommunication {

  protected static MmsConnectionParameters getLocallyConfiguredMmsConnectionParameters(Context context)
      throws ApnUnavailableException
  {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

    if (preferences.getBoolean(ApplicationPreferencesActivity.USE_LOCAL_MMS_APNS_PREF, false)) {
      String mmsc = preferences.getString(ApplicationPreferencesActivity.MMSC_HOST_PREF, null);

      if (mmsc == null || !mmsc.startsWith("http"))
        throw new ApnUnavailableException("Malformed locally configured MMSC: " + mmsc);

      String proxy = preferences.getString(ApplicationPreferencesActivity.MMSC_PROXY_HOST_PREF, null);
      String port  = preferences.getString(ApplicationPreferencesActivity.MMSC_PROXY_PORT_PREF, null);

      return new MmsConnectionParameters(mmsc, proxy, port);
    }

    throw new ApnUnavailableException("No locally configured parameters available");
  }

  protected static MmsConnectionParameters getMmsConnectionParameters(Context context, String apn,
                                                                      boolean proxyIfPossible)
      throws ApnUnavailableException
  {
    Cursor cursor = null;

    try {
      cursor = DatabaseFactory.getMmsDatabase(context).getCarrierMmsInformation(apn);

      if (cursor == null || !cursor.moveToFirst())
        return getLocallyConfiguredMmsConnectionParameters(context);

      do {
        String mmsc  = cursor.getString(cursor.getColumnIndexOrThrow("mmsc"));
        String proxy = null;
        String port  = null;

        if (proxyIfPossible) {
          proxy = cursor.getString(cursor.getColumnIndexOrThrow("mmsproxy"));
          port  = cursor.getString(cursor.getColumnIndexOrThrow("mmsport"));
        }

        if (mmsc != null && !mmsc.equals(""))
          return new MmsConnectionParameters(mmsc, proxy, port);

      } while (cursor.moveToNext());

      return getLocallyConfiguredMmsConnectionParameters(context);
    } catch (SQLiteException sqe) {
      Log.w("MmsCommunication", sqe);
      return getLocallyConfiguredMmsConnectionParameters(context);
    } catch (SecurityException se) {
      Log.w("MmsCommunication", se);
      return getLocallyConfiguredMmsConnectionParameters(context);
    } catch (IllegalArgumentException iae) {
      Log.w("MmsCommunication", iae);
      return getLocallyConfiguredMmsConnectionParameters(context);
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  protected static void checkRouteToHost(Context context, MmsConnectionParameters parameters,
                                         String url, boolean usingMmsRadio)
    throws IOException
  {
    if (parameters == null || !parameters.hasProxy())
      checkRouteToHost(context, Uri.parse(url).getHost(), usingMmsRadio);
    else
      checkRouteToHost(context, parameters.getProxy(), usingMmsRadio);
  }

  private static void checkRouteToHost(Context context, String host, boolean usingMmsRadio)
      throws IOException
  {
    InetAddress inetAddress = InetAddress.getByName(host);

    if (!usingMmsRadio) {
      if (inetAddress.isSiteLocalAddress()) {
        throw new IOException("RFC1918 address in non-MMS radio situation!");
      }

      return;
    }

    byte[] ipAddressBytes       = inetAddress.getAddress();
    int ipAddress               = Conversions.byteArrayToIntLittleEndian(ipAddressBytes, 0);
    ConnectivityManager manager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

    if (!manager.requestRouteToHost(MmsDownloader.TYPE_MOBILE_MMS, ipAddress))
      throw new IOException("Connection manager could not obtain route to host.");
    //        if (!manager.requestRouteToHost(ConnectivityManager.TYPE_MOBILE, ipAddress))
    //        	throw new IOException("Connection manager could not obtain route to host.");

  }

  protected static AndroidHttpClient constructHttpClient(Context context, MmsConnectionParameters mmsConfig) {
    AndroidHttpClient client = AndroidHttpClient.newInstance("TextSecure/0.1", context);
    HttpParams params        = client.getParams();
    HttpProtocolParams.setContentCharset(params, "UTF-8");
    HttpConnectionParams.setSoTimeout(params, 20 * 1000);

    if (mmsConfig.hasProxy()) {
      ConnRouteParams.setDefaultProxy(params, new HttpHost(mmsConfig.getProxy(), mmsConfig.getPort()));
    }

    return client;
  }

  protected static byte[] parseResponse(HttpEntity entity) throws IOException {
    if (entity == null || entity.getContentLength() == 0)
      throw new IOException("Null response");

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
    private final String mmsc;
    private final String proxy;
    private final String port;

    public MmsConnectionParameters(String mmsc, String proxy, String port) {
      this.mmsc  = mmsc;
      this.proxy = proxy;
      this.port  = port;
    }

    public boolean hasProxy() {
      return proxy != null && proxy.trim().length() != 0;
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
      if (port == null || port.trim().length() == 0)
        return 80;

      return Integer.parseInt(port);
    }
  }

}
