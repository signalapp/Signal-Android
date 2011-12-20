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
package org.thoughtcrime.securesms.database;

import java.io.UnsupportedEncodingException;

import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import android.content.ContentValues;
import android.util.Log;

public class ContentValuesBuilder {

  private final ContentValues contentValues;
	
  public ContentValuesBuilder(ContentValues contentValues) {
    this.contentValues = contentValues;
  }
	
  public void add(String key, String charsetKey, EncodedStringValue value) {
    if (value != null) {
      contentValues.put(key, toIsoString(value.getTextString()));
      contentValues.put(charsetKey, value.getCharacterSet());
    }
  }
	
  public void add(String contentKey, byte[] value) {
    if (value != null) {
      contentValues.put(contentKey, toIsoString(value));
    }
  }
	
  public void add(String contentKey, int b) {
    if (b != 0)
      contentValues.put(contentKey, b);
  }

  public void add(String contentKey, long value) {
    if (value != -1L)
      contentValues.put(contentKey, value);
  }
	
  public ContentValues getContentValues() {
    return contentValues;
  }
	
  private String toIsoString(byte[] bytes) {
    try {
      return new String(bytes, CharacterSets.MIMENAME_ISO_8859_1);
    } catch (UnsupportedEncodingException e) {
      Log.e("MmsDatabase", "ISO_8859_1 must be supported!", e);
      return "";
    }
  }

}
