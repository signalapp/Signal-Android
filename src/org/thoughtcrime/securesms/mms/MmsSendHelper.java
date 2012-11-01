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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;

import ws.com.google.android.mms.MmsException;

import java.io.IOException;

public class MmsSendHelper extends MmsCommunication {

  private static byte[] makePost(MmsConnectionParameters parameters, byte[] mms) throws ClientProtocolException, IOException {
    Log.w("MmsSender", "Sending MMS1 of length: " + mms.length);
    HttpClient client      = constructHttpClient(parameters);
    HttpPost request       = new HttpPost(parameters.getMmsc());
    ByteArrayEntity entity = new ByteArrayEntity(mms);

    entity.setContentType("application/vnd.wap.mms-message");

    request.setEntity(entity);
    request.setParams(client.getParams());
    request.addHeader("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic");

    HttpResponse response = client.execute(request);
    StatusLine status     = response.getStatusLine();

    if (status.getStatusCode() != 200)
      throw new IOException("Non-successful HTTP response: " + status.getReasonPhrase());

    return parseResponse(response.getEntity());
  }

  public static byte[] sendMms(Context context, byte[] mms, String apn) throws IOException {
    Log.w("MmsSender", "Sending MMS of length: " + mms.length);
    try {
      MmsConnectionParameters parameters = getMmsConnectionParameters(context, apn);
      checkRouteToHost(context, parameters, parameters.getMmsc());
      return makePost(parameters, mms);
    } catch (MmsException me) {
      Log.w("MmsSender", me);
      throw new IOException("Failed to get MMSC information...");
    }
  }
}
