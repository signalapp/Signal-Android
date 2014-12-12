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

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.EncryptingPartOutputStream;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduPart;

public class PartDatabase extends Database {
  private static final String TAG = PartDatabase.class.getSimpleName();

  private static final String TABLE_NAME              = "part";
  private static final String ID                      = "_id";
  private static final String MMS_ID                  = "mid";
  private static final String SEQUENCE                = "seq";
  private static final String CONTENT_TYPE            = "ct";
  private static final String NAME                    = "name";
  private static final String CHARSET                 = "chset";
  private static final String CONTENT_DISPOSITION     = "cd";
  private static final String FILENAME                = "fn";
  private static final String CONTENT_ID              = "cid";
  private static final String CONTENT_LOCATION        = "cl";
  private static final String CONTENT_TYPE_START      = "ctt_s";
  private static final String CONTENT_TYPE_TYPE       = "ctt_t";
  private static final String ENCRYPTED               = "encrypted";
  private static final String DATA                    = "_data";
  private static final String PENDING_PUSH_ATTACHMENT = "pending_push";
  private static final String SIZE                    = "data_size";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
    MMS_ID + " INTEGER, " + SEQUENCE + " INTEGER DEFAULT 0, "                        +
    CONTENT_TYPE + " TEXT, " + NAME + " TEXT, " + CHARSET + " INTEGER, "             +
    CONTENT_DISPOSITION + " TEXT, " + FILENAME + " TEXT, " + CONTENT_ID + " TEXT, "  +
    CONTENT_LOCATION + " TEXT, " + CONTENT_TYPE_START + " INTEGER, "                 +
    CONTENT_TYPE_TYPE + " TEXT, " + ENCRYPTED + " INTEGER, "                         +
    PENDING_PUSH_ATTACHMENT + " INTEGER, "+ DATA + " TEXT, " + SIZE + " INTEGER);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS part_mms_id_index ON " + TABLE_NAME + " (" + MMS_ID + ");",
    "CREATE INDEX IF NOT EXISTS pending_push_index ON " + TABLE_NAME + " (" + PENDING_PUSH_ATTACHMENT + ");",
  };

  public PartDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public InputStream getPartStream(MasterSecret masterSecret, long partId)
      throws FileNotFoundException
  {
    return getDataStream(masterSecret, partId, DATA);
  }

  public void updateFailedDownloadedPart(long messageId, long partId, PduPart part)
      throws MmsException
  {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    part.setContentDisposition(new byte[0]);
    part.setPendingPush(false);

    ContentValues values = getContentValuesForPart(part);

    values.put(DATA, (String)null);

    database.update(TABLE_NAME, values, ID_WHERE, new String[] {partId+""});
    notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId));
  }

  public PduPart getPart(long partId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, null, ID_WHERE, new String[] {partId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) return getPart(cursor);
      else                                        return null;

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public List<Pair<Long, PduPart>> getParts(long mmsId) {
    SQLiteDatabase            database = databaseHelper.getReadableDatabase();
    List<Pair<Long, PduPart>> results  = new LinkedList<>();
    Cursor                    cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, MMS_ID + " = ?", new String[] {mmsId+""},
                              null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        PduPart part = getPart(cursor);
        results.add(new Pair<>(cursor.getLong(cursor.getColumnIndexOrThrow(ID)),
                                            part));
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void deleteParts(long mmsId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {DATA}, MMS_ID + " = ?",
                              new String[] {mmsId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        String data      = cursor.getString(0);

        if (!TextUtils.isEmpty(data)) {
          new File(data).delete();
        }
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    database.delete(TABLE_NAME, MMS_ID + " = ?", new String[] {mmsId+""});
  }

  public void deleteAllParts() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);

    File   partsDirectory = context.getDir("parts", Context.MODE_PRIVATE);
    File[] parts          = partsDirectory.listFiles();

    for (File part : parts) {
      part.delete();
    }
  }

  void insertParts(MasterSecret masterSecret, long mmsId, PduBody body) throws MmsException {
    for (int i=0;i<body.getPartsNum();i++) {
      long partId = insertPart(masterSecret, body.getPart(i), mmsId);
      Log.w(TAG, "Inserted part at ID: " + partId);
    }
  }

  private void getPartValues(PduPart part, Cursor cursor) {
    int charsetColumn = cursor.getColumnIndexOrThrow(CHARSET);

    if (!cursor.isNull(charsetColumn))
      part.setCharset(cursor.getInt(charsetColumn));

    int contentTypeColumn = cursor.getColumnIndexOrThrow(CONTENT_TYPE);

    if (!cursor.isNull(contentTypeColumn))
      part.setContentType(Util.toIsoBytes(cursor.getString(contentTypeColumn)));

    int nameColumn = cursor.getColumnIndexOrThrow(NAME);

    if (!cursor.isNull(nameColumn))
      part.setName(Util.toIsoBytes(cursor.getString(nameColumn)));

    int fileNameColumn = cursor.getColumnIndexOrThrow(FILENAME);

    if (!cursor.isNull(fileNameColumn))
      part.setFilename(Util.toIsoBytes(cursor.getString(fileNameColumn)));

    int contentDispositionColumn = cursor.getColumnIndexOrThrow(CONTENT_DISPOSITION);

    if (!cursor.isNull(contentDispositionColumn))
      part.setContentDisposition(Util.toIsoBytes(cursor.getString(contentDispositionColumn)));

    int contentIdColumn = cursor.getColumnIndexOrThrow(CONTENT_ID);

    if (!cursor.isNull(contentIdColumn))
      part.setContentId(Util.toIsoBytes(cursor.getString(contentIdColumn)));

    int contentLocationColumn = cursor.getColumnIndexOrThrow(CONTENT_LOCATION);

    if (!cursor.isNull(contentLocationColumn))
      part.setContentLocation(Util.toIsoBytes(cursor.getString(contentLocationColumn)));

    int encryptedColumn = cursor.getColumnIndexOrThrow(ENCRYPTED);

    if (!cursor.isNull(encryptedColumn))
      part.setEncrypted(cursor.getInt(encryptedColumn) == 1);

    int pendingPushColumn = cursor.getColumnIndexOrThrow(PENDING_PUSH_ATTACHMENT);

    if (!cursor.isNull(pendingPushColumn))
      part.setPendingPush(cursor.getInt(pendingPushColumn) == 1);

    int sizeColumn = cursor.getColumnIndexOrThrow(SIZE);

    if (!cursor.isNull(sizeColumn))
      part.setDataSize(cursor.getLong(cursor.getColumnIndexOrThrow(SIZE)));
  }

  private ContentValues getContentValuesForPart(PduPart part) throws MmsException {
    ContentValues contentValues = new ContentValues();

    if (part.getCharset() != 0 ) {
      contentValues.put(CHARSET, part.getCharset());
    }

    if (part.getContentType() != null) {
      contentValues.put(CONTENT_TYPE, Util.toIsoString(part.getContentType()));

      if (Util.toIsoString(part.getContentType()).equals(ContentType.APP_SMIL)) {
        contentValues.put(SEQUENCE, -1);
      }
    } else {
      throw new MmsException("There is no content type for this part.");
    }

    if (part.getName() != null) {
      contentValues.put(NAME, new String(part.getName()));
    }

    if (part.getFilename() != null) {
      contentValues.put(FILENAME, new String(part.getFilename()));
    }

    if (part.getContentDisposition() != null) {
      contentValues.put(CONTENT_DISPOSITION, Util.toIsoString(part.getContentDisposition()));
    }

    if (part.getContentId() != null) {
      contentValues.put(CONTENT_ID, Util.toIsoString(part.getContentId()));
    }

    if (part.getContentLocation() != null) {
      contentValues.put(CONTENT_LOCATION, Util.toIsoString(part.getContentLocation()));
    }

    contentValues.put(ENCRYPTED, part.getEncrypted() ? 1 : 0);
    contentValues.put(PENDING_PUSH_ATTACHMENT, part.isPendingPush() ? 1 : 0);

    return contentValues;
  }

  private InputStream getPartInputStream(MasterSecret masterSecret, File path)
      throws FileNotFoundException
  {
    Log.w(TAG, "Getting part at: " + path.getAbsolutePath());
    return new DecryptingPartInputStream(path, masterSecret);
  }

  protected OutputStream getPartOutputStream(MasterSecret masterSecret, File path, PduPart part)
      throws FileNotFoundException
  {
    Log.w(TAG, "Writing part to: " + path.getAbsolutePath());
    part.setEncrypted(true);
    return new EncryptingPartOutputStream(path, masterSecret);
  }

  private InputStream getDataStream(MasterSecret masterSecret, long partId, String dataType)
      throws FileNotFoundException
  {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, new String[]{dataType}, ID_WHERE,
                              new String[] {partId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        if (cursor.isNull(0)) {
          throw new FileNotFoundException("No part data for id: " + partId);
        }

        return getPartInputStream(masterSecret, new File(cursor.getString(0)));
      } else {
        throw new FileNotFoundException("No part for id: " + partId);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private Pair<File, Long> writePartData(MasterSecret masterSecret, PduPart part, InputStream in)
      throws MmsException
  {
    try {
      File         partsDirectory  = context.getDir("parts", Context.MODE_PRIVATE);
      File         dataFile        = File.createTempFile("part", ".mms", partsDirectory);
      OutputStream out             = getPartOutputStream(masterSecret, dataFile, part);
      long         plaintextLength = Util.copy(in, out);

      return new Pair<>(dataFile, plaintextLength);
    } catch (IOException e) {
      throw new MmsException(e);
    }
  }

  private Pair<File, Long> writePartData(MasterSecret masterSecret, PduPart part)
      throws MmsException
  {
    try {
      if (part.getData() != null) {
        Log.w(TAG, "Writing part data from buffer");
        return writePartData(masterSecret, part, new ByteArrayInputStream(part.getData()));
      } else if (part.getDataUri() != null) {
        Log.w(TAG, "Writing part data from URI");
        InputStream in = context.getContentResolver().openInputStream(part.getDataUri());
        return writePartData(masterSecret, part, in);
      } else {
        throw new MmsException("Part is empty!");
      }
    } catch (FileNotFoundException e) {
      throw new MmsException(e);
    }
  }

  private PduPart getPart(Cursor cursor) {
    PduPart part        = new PduPart();
    String dataLocation = cursor.getString(cursor.getColumnIndexOrThrow(DATA));
    long partId         = cursor.getLong(cursor.getColumnIndexOrThrow(ID));

    getPartValues(part, cursor);

    part.setDataUri(ContentUris.withAppendedId(PartAuthority.PART_CONTENT_URI, partId));

    return part;
  }

  private long insertPart(MasterSecret masterSecret, PduPart part, long mmsId) throws MmsException {
    Log.w(TAG, "inserting part to mms " + mmsId);
    SQLiteDatabase   database = databaseHelper.getWritableDatabase();
    Pair<File, Long> partData = null;

    if (!part.isPendingPush()) {
      partData = writePartData(masterSecret, part);
      Log.w(TAG, "Wrote part to file: " + partData.first.getAbsolutePath());
    }

    ContentValues contentValues = getContentValuesForPart(part);
    contentValues.put(MMS_ID, mmsId);

    if (partData != null) {
      contentValues.put(DATA, partData.first.getAbsolutePath());
      contentValues.put(SIZE, partData.second);
    }

    return database.insert(TABLE_NAME, null, contentValues);
  }

  public void updateDownloadedPart(MasterSecret masterSecret, long messageId,
                                   long partId, PduPart part, InputStream data)
      throws MmsException
  {
    SQLiteDatabase   database = databaseHelper.getWritableDatabase();
    Pair<File, Long> partData = writePartData(masterSecret, part, data);

    part.setContentDisposition(new byte[0]);
    part.setPendingPush(false);

    ContentValues values = getContentValuesForPart(part);

    if (partData != null) {
      values.put(DATA, partData.first.getAbsolutePath());
      values.put(SIZE, partData.second);
    }

    database.update(TABLE_NAME, values, ID_WHERE, new String[] {partId+""});

    notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId));
  }
}
