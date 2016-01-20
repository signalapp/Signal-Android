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
package org.privatechats.securesms.database;

import android.database.Cursor;
import android.util.Log;

import ws.com.google.android.mms.InvalidHeaderValueException;
import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduHeaders;

import java.io.UnsupportedEncodingException;

public class PduHeadersBuilder {

  private final PduHeaders headers;
  private final Cursor cursor;

  public PduHeadersBuilder(PduHeaders headers, Cursor cursor) {
    this.headers = headers;
    this.cursor  = cursor;
  }

  public PduHeaders getHeaders() {
    return headers;
  }

  public void addLong(String key, int headersKey) {
    int columnIndex = cursor.getColumnIndexOrThrow(key);

    if (!cursor.isNull(columnIndex))
      headers.setLongInteger(cursor.getLong(columnIndex), headersKey);
  }

  public void addOctet(String key, int headersKey) throws InvalidHeaderValueException {
    int columnIndex = cursor.getColumnIndexOrThrow(key);

    if (!cursor.isNull(columnIndex))
      headers.setOctet(cursor.getInt(columnIndex), headersKey);
  }

  public void addText(String key, int headersKey) {
    String value = cursor.getString(cursor.getColumnIndexOrThrow(key));
    if (value != null && value.trim().length() > 0)
      headers.setTextString(getBytes(value), headersKey);
  }
  public void add(String key, String charsetKey, int headersKey) {
    String value = cursor.getString(cursor.getColumnIndexOrThrow(key));

    if (value != null && value.trim().length() > 0) {
      int charsetValue = cursor.getInt(cursor.getColumnIndexOrThrow(charsetKey));
      EncodedStringValue encodedValue = new EncodedStringValue(charsetValue, getBytes(value));
      headers.setEncodedStringValue(encodedValue, headersKey);
    }
  }

  private byte[] getBytes(String data) {
    try {
      return data.getBytes(CharacterSets.MIMENAME_ISO_8859_1);
    } catch (UnsupportedEncodingException e) {
      Log.e("PduHeadersBuilder", "ISO_8859_1 must be supported!", e);
      return new byte[0];
    }
  }
}
