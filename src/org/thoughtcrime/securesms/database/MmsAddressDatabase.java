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
import android.support.annotation.NonNull;

import com.google.android.mms.pdu_alt.PduHeaders;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;

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

  private void insertAddress(long messageId, int type, @NonNull String value) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(MMS_ID, messageId);
    contentValues.put(TYPE, type);
    contentValues.put(ADDRESS, value);
    contentValues.put(ADDRESS_CHARSET, "UTF-8");
    database.insert(TABLE_NAME, null, contentValues);
  }

  private void insertAddress(long messageId, int type, @NonNull List<String> addresses) {
    for (String address : addresses) {
      insertAddress(messageId, type, address);
    }
  }

  public void insertAddressesForId(long messageId, MmsAddresses addresses) {
    if (addresses.getFrom() != null) {
      insertAddress(messageId, PduHeaders.FROM, addresses.getFrom());
    }

    insertAddress(messageId, PduHeaders.TO, addresses.getTo());
    insertAddress(messageId, PduHeaders.CC, addresses.getCc());
    insertAddress(messageId, PduHeaders.BCC, addresses.getBcc());
  }

  public MmsAddresses getAddressesForId(long messageId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;
    String         from     = null;
    List<String>   to       = new LinkedList<>();
    List<String>   cc       = new LinkedList<>();
    List<String>   bcc      = new LinkedList<>();

    try {
      cursor = database.query(TABLE_NAME, null, MMS_ID + " = ?", new String[] {messageId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        long   type    = cursor.getLong(cursor.getColumnIndexOrThrow(TYPE));
        String address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));

        if (type == PduHeaders.FROM) from = address;
        if (type == PduHeaders.TO)   to.add(address);
        if (type == PduHeaders.CC)   cc.add(address);
        if (type == PduHeaders.BCC)  bcc.add(address);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return new MmsAddresses(from, to, cc, bcc);
  }

  public List<String> getAddressesListForId(long messageId) {
    List<String> results   = new LinkedList<>();
    MmsAddresses addresses = getAddressesForId(messageId);

    if (addresses.getFrom() != null) {
      results.add(addresses.getFrom());
    }

    results.addAll(addresses.getTo());
    results.addAll(addresses.getCc());
    results.addAll(addresses.getBcc());

    return results;
  }

  public Recipients getRecipientsForId(long messageId) {
    List<String>    numbers = getAddressesListForId(messageId);
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
}
