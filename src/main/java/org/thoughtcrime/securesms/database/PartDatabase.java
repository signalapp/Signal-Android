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

import org.thoughtcrime.securesms.providers.PartProvider;
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduPart;

public class PartDatabase extends Database {

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

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, "              +
    MMS_ID + " INTEGER, " + SEQUENCE + " INTEGER DEFAULT 0, "                       +
    CONTENT_TYPE + " TEXT, " + NAME + " TEXT, " + CHARSET + " INTEGER, "            +
    CONTENT_DISPOSITION + " TEXT, " + FILENAME + " TEXT, " + CONTENT_ID + " TEXT, " +
    CONTENT_LOCATION + " TEXT, " + CONTENT_TYPE_START + " INTEGER, "                +
    CONTENT_TYPE_TYPE + " TEXT, " + ENCRYPTED + " INTEGER, " +
    PENDING_PUSH_ATTACHMENT + " INTEGER, "+ DATA + " TEXT);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS part_mms_id_index ON " + TABLE_NAME + " (" + MMS_ID + ");",
    "CREATE INDEX IF NOT EXISTS pending_push_index ON " + TABLE_NAME + " (" + PENDING_PUSH_ATTACHMENT + ");",
  };

  public PartDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
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

  protected FileInputStream getPartInputStream(File file, PduPart part) throws FileNotFoundException {
    Log.w("PartDatabase", "Reading non-encrypted part from: " + file.getAbsolutePath());
    return new FileInputStream(file);
  }

  protected FileOutputStream getPartOutputStream(File file, PduPart part) throws FileNotFoundException {
    Log.w("PartDatabase", "Writing non-encrypted part to: " + file.getAbsolutePath());
    return new FileOutputStream(file);
  }

  private void readPartData(PduPart part, String filename) {
    try {
      File dataFile              = new File(filename);
      FileInputStream fin        = getPartInputStream(dataFile, part);
      ByteArrayOutputStream baos = new ByteArrayOutputStream((int)dataFile.length());
      byte[] buffer              = new byte[512];
      int read;

      while ((read = fin.read(buffer)) != -1)
        baos.write(buffer, 0, read);

      part.setData(baos.toByteArray());
      fin.close();
    } catch (IOException ioe) {
      Log.w("PartDatabase", ioe);
      part.setData(null);
    }
  }

  private File writePartData(PduPart part, InputStream in) throws MmsException {
    try {
      File partsDirectory   = context.getDir("parts", Context.MODE_PRIVATE);
      File dataFile         = File.createTempFile("part", ".mms", partsDirectory);
      FileOutputStream fout = getPartOutputStream(dataFile, part);

      byte[] buf = new byte[512];
      int read;

      while ((read = in.read(buf)) != -1) {
        fout.write(buf, 0, read);
      }

      fout.close();
      in.close();

      return dataFile;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private File writePartData(PduPart part) throws MmsException {
    try {
      if (part.getData() != null) {
        Log.w("PartDatabase", "Writing part data from buffer");
        return writePartData(part, new ByteArrayInputStream(part.getData()));
      } else if (part.getDataUri() != null) {
        Log.w("PartDatabase", "Writing part dat from URI");
        InputStream in = context.getContentResolver().openInputStream(part.getDataUri());
        return writePartData(part, in);
      } else {
        throw new MmsException("Part is empty!");
      }
    } catch (FileNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  private PduPart getPart(Cursor cursor, boolean includeData) {
    PduPart part        = new PduPart();
    String dataLocation = cursor.getString(cursor.getColumnIndexOrThrow(DATA));
    long partId         = cursor.getLong(cursor.getColumnIndexOrThrow(ID));

    getPartValues(part, cursor);
    if (includeData && !part.isPendingPush())
      readPartData(part, dataLocation);
    part.setDataUri(ContentUris.withAppendedId(PartProvider.CONTENT_URI, partId));

    return part;
  }

  private long insertPart(PduPart part, long mmsId) throws MmsException {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    File           dataFile = null;

    if (!part.isPendingPush()) {
      dataFile = writePartData(part);
      Log.w("PartDatabase", "Wrote part to file: " + dataFile.getAbsolutePath());
    }

    ContentValues contentValues = getContentValuesForPart(part);
    contentValues.put(MMS_ID, mmsId);

    if (dataFile != null) {
      contentValues.put(DATA, dataFile.getAbsolutePath());
    }

    return database.insert(TABLE_NAME, null, contentValues);
  }

  public InputStream getPartStream(long partId) throws FileNotFoundException {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor           = null;

    Log.w("PartDatabase", "Getting part at ID: " + partId);
    try {
      cursor = database.query(TABLE_NAME, new String[]{DATA, ENCRYPTED}, ID_WHERE, new String[] {partId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        PduPart part = new PduPart();
        part.setEncrypted(cursor.getInt(1) == 1);

        if (cursor.isNull(0)) {
          throw new FileNotFoundException("No part data for id: " + partId);
        }

        return getPartInputStream(new File(cursor.getString(0)), part);
      } else {
        throw new FileNotFoundException("No part for id: " + partId);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  void insertParts(long mmsId, PduBody body) throws MmsException {
    for (int i=0;i<body.getPartsNum();i++) {
      long partId = insertPart(body.getPart(i), mmsId);
      Log.w("PartDatabase", "Inserted part at ID: " + partId);
    }
  }

  public void updateDownloadedPart(long messageId, long partId, PduPart part, InputStream data)
      throws MmsException
  {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    File           partData = writePartData(part, data);

    part.setContentDisposition(new byte[0]);
    part.setPendingPush(false);

    ContentValues values = getContentValuesForPart(part);

    if (partData != null) {
      values.put(DATA, partData.getAbsolutePath());
    }

    database.update(TABLE_NAME, values, ID_WHERE, new String[] {partId+""});
    notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId));
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

  public PduPart getPart(long partId, boolean includeData) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, null, ID_WHERE, new String[] {partId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst())
        return getPart(cursor, includeData);
      else
        return null;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public List<Pair<Long, PduPart>> getParts(long mmsId, boolean includeData) {
    SQLiteDatabase            database = databaseHelper.getReadableDatabase();
    List<Pair<Long, PduPart>> results  = new LinkedList<Pair<Long, PduPart>>();
    Cursor                    cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, MMS_ID + " = ?", new String[] {mmsId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        PduPart part = getPart(cursor, includeData);
        results.add(new Pair<Long, PduPart>(cursor.getLong(cursor.getColumnIndexOrThrow(ID)),
                                            part));
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public List<Pair<Long, Pair<Long, PduPart>>> getPushPendingParts() {
    SQLiteDatabase                        database = databaseHelper.getReadableDatabase();
    List<Pair<Long, Pair<Long, PduPart>>> results  = new LinkedList<Pair<Long, Pair<Long, PduPart>>>();
    Cursor                                cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, PENDING_PUSH_ATTACHMENT + " = ?", new String[] {"1"}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        PduPart part = getPart(cursor, false);
        results.add(new Pair<Long, Pair<Long, PduPart>>(cursor.getLong(cursor.getColumnIndexOrThrow(MMS_ID)),
                                                        new Pair<Long, PduPart>(cursor.getLong(cursor.getColumnIndexOrThrow(ID)),
                                                                                part)));
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
      cursor = database.query(TABLE_NAME, new String[] {DATA}, MMS_ID + " = ?", new String[] {mmsId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        String data = cursor.getString(0);
        if (!TextUtils.isEmpty(data)) {
          new File(cursor.getString(0)).delete();
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

    File partsDirectory = context.getDir("parts", Context.MODE_PRIVATE);
    File[] parts        = partsDirectory.listFiles();

    for (int i=0;i<parts.length;i++) {
      parts[i].delete();
    }
  }
}
