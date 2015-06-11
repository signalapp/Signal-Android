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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;

import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduHeaders;

import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;

public class MmsAddressDatabase extends Database {

  private static final String TAG = MmsAddressDatabase.class.getSimpleName();

  private static final String TABLE_NAME      = "mms_addresses";
  private static final String ID              = "_id";
  private static final String MMS_ID          = "mms_id";
  private static final String TYPE            = "type";
  private static final String ADDRESS         = "address";
  private static final String ADDRESS_CHARSET = "address_charset";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
    MMS_ID + " INTEGER, " +  TYPE + " INTEGER, " + ADDRESS + " TEXT, " +
    ADDRESS_CHARSET + " INTEGER);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS mms_addresses_mms_id_index ON " + TABLE_NAME + " (" + MMS_ID + ");",
  };

  public MmsAddressDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  private void insertAddress(long messageId, int type, EncodedStringValue address) {
    if (address != null) {
      SQLiteDatabase database = databaseHelper.getWritableDatabase();
      ContentValues contentValues = new ContentValues();
      contentValues.put(MMS_ID, messageId);
      contentValues.put(TYPE, type);
      contentValues.put(ADDRESS, toIsoString(address.getTextString()));
      contentValues.put(ADDRESS_CHARSET, address.getCharacterSet());
      database.insert(TABLE_NAME, null, contentValues);
    }
  }

  private void insertAddress(long messageId, int type, EncodedStringValue[] addresses) {
    if (addresses != null) {
      for (int i=0;i<addresses.length;i++) {
        insertAddress(messageId, type, addresses[i]);
      }
    }
  }

  private void addAddress(Cursor cursor, PduHeaders headers) {
    long type      = cursor.getLong(cursor.getColumnIndexOrThrow(TYPE));
    String address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
    long charset   = cursor.getLong(cursor.getColumnIndexOrThrow(ADDRESS_CHARSET));

    EncodedStringValue encodedAddress = new EncodedStringValue((int)charset, getBytes(address));

    if (type == PduHeaders.FROM)
      headers.setEncodedStringValue(encodedAddress, PduHeaders.FROM);
    else
      headers.appendEncodedStringValue(encodedAddress, (int)type);
  }

  public void insertAddressesForId(long messageId, PduHeaders headers) {
    insertAddress(messageId, PduHeaders.FROM, headers.getEncodedStringValue(PduHeaders.FROM));
    insertAddress(messageId, PduHeaders.TO, headers.getEncodedStringValues(PduHeaders.TO));
    insertAddress(messageId, PduHeaders.CC, headers.getEncodedStringValues(PduHeaders.CC));
    insertAddress(messageId, PduHeaders.BCC, headers.getEncodedStringValues(PduHeaders.BCC));
  }

  public void getAddressesForId(long messageId, PduHeaders headers) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, null, MMS_ID + " = ?", new String[] {messageId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        addAddress(cursor, headers);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public List<String> getAddressesForId(long messageId) {
    List<String>   results  = new LinkedList<String>();
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, MMS_ID + " = ?", new String[] {messageId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        results.add(cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS)));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return results;
  }

  public Recipients getRecipientsForId(long messageId) {
    List<String>    numbers = getAddressesForId(messageId);
    List<Recipient> results = new LinkedList<>();

    for (String number : numbers) {
      if (!PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR.equals(number)) {
        results.add(RecipientFactory.getRecipientsFromString(context, number, false)
                                    .getPrimaryRecipient());
      }
    }

    return RecipientFactory.getRecipientsFor(context, results, false);
  }


  public void deleteAddressesForId(long messageId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, MMS_ID + " = ?", new String[] {messageId+""});
  }

  public void deleteAllAddresses() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);
  }

  private byte[] getBytes(String data) {
    try {
      return data.getBytes(CharacterSets.MIMENAME_ISO_8859_1);
    } catch (UnsupportedEncodingException e) {
      Log.e("PduHeadersBuilder", "ISO_8859_1 must be supported!", e);
      return new byte[0];
    }
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
