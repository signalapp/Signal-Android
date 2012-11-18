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
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import ws.com.google.android.mms.MmsException;

import java.io.IOException;

public class MmsDownloadHelper extends MmsCommunication {

  private static byte[] makeRequest(MmsConnectionParameters connectionParameters, String url)
      throws ClientProtocolException, IOException
  {
    HttpClient client = constructHttpClient(connectionParameters);
    HttpGet request   = new HttpGet(url);

    request.setParams(client.getParams());
    request.addHeader("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");

    HttpResponse response = client.execute(request);
    StatusLine status     = response.getStatusLine();

    if (status.getStatusCode() != 200)
      throw new IOException("Non-successful HTTP response: " + status.getReasonPhrase());

    return parseResponse(response.getEntity());
  }

  public static byte[] retrieveMms(Context context, String url, String apn) throws IOException {
    MmsConnectionParameters connectionParameters;

    try {
      connectionParameters = getMmsConnectionParameters(context, apn);
    } catch (MmsException me) {
      Log.w("MmsDownloadHelper", me);
      connectionParameters = new MmsConnectionParameters(null, null, null);
    }

    checkRouteToHost(context, connectionParameters, url);
    return makeRequest(connectionParameters, url);
  }
}
