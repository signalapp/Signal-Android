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
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.EncryptingPartOutputStream;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUnion;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MediaUtil.ThumbnailData;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VisibleForTesting;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduPart;

public class PartDatabase extends Database {
  private static final String TAG = PartDatabase.class.getSimpleName();

  private static final String TABLE_NAME          = "part";
  private static final String ROW_ID              = "_id";
  private static final String MMS_ID              = "mid";
  private static final String SEQUENCE            = "seq";
  private static final String CONTENT_TYPE        = "ct";
  private static final String NAME                = "name";
  private static final String CHARSET             = "chset";
  private static final String CONTENT_DISPOSITION = "cd";
  private static final String FILENAME            = "fn";
  private static final String CONTENT_ID          = "cid";
  private static final String CONTENT_LOCATION    = "cl";
  private static final String CONTENT_TYPE_START  = "ctt_s";
  private static final String CONTENT_TYPE_TYPE   = "ctt_t";
  private static final String ENCRYPTED           = "encrypted";
  private static final String DATA                = "_data";
  private static final String IN_PROGRESS         = "pending_push";
  private static final String SIZE                = "data_size";
  private static final String THUMBNAIL           = "thumbnail";
  private static final String ASPECT_RATIO        = "aspect_ratio";
  private static final String UNIQUE_ID           = "unique_id";

  private static final String PART_ID_WHERE = ROW_ID + " = ? AND " + UNIQUE_ID + " = ?";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ROW_ID + " INTEGER PRIMARY KEY, " +
    MMS_ID + " INTEGER, " + SEQUENCE + " INTEGER DEFAULT 0, "                        +
    CONTENT_TYPE + " TEXT, " + NAME + " TEXT, " + CHARSET + " INTEGER, "             +
    CONTENT_DISPOSITION + " TEXT, " + FILENAME + " TEXT, " + CONTENT_ID + " TEXT, "  +
    CONTENT_LOCATION + " TEXT, " + CONTENT_TYPE_START + " INTEGER, "                 +
    CONTENT_TYPE_TYPE + " TEXT, " + ENCRYPTED + " INTEGER, "                         +
    IN_PROGRESS + " INTEGER, "+ DATA + " TEXT, " + SIZE + " INTEGER, "   +
    THUMBNAIL + " TEXT, " + ASPECT_RATIO + " REAL, " + UNIQUE_ID + " INTEGER NOT NULL);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS part_mms_id_index ON " + TABLE_NAME + " (" + MMS_ID + ");",
    "CREATE INDEX IF NOT EXISTS pending_push_index ON " + TABLE_NAME + " (" + IN_PROGRESS + ");",
  };

  private final static String IMAGES_QUERY = "SELECT " + TABLE_NAME + "." + ROW_ID + ", "
                                                       + TABLE_NAME + "." + CONTENT_TYPE + ", "
                                                       + TABLE_NAME + "." + ASPECT_RATIO + ", "
                                                       + TABLE_NAME + "." + UNIQUE_ID + ", "
                                                       + MmsDatabase.TABLE_NAME + "." + MmsDatabase.NORMALIZED_DATE_RECEIVED + ", "
                                                       + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ADDRESS + " "
                                           + "FROM " + TABLE_NAME + " LEFT JOIN " + MmsDatabase.TABLE_NAME
                                                                  + " ON " + TABLE_NAME + "." + MMS_ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " "
                                           + "WHERE " + MMS_ID + " IN (SELECT " + MmsSmsColumns.ID
                                                                   + " FROM " + MmsDatabase.TABLE_NAME
                                                                   + " WHERE " + MmsDatabase.THREAD_ID + " = ?) AND "
                                                      + CONTENT_TYPE + " LIKE 'image/%' "
                                           + "ORDER BY " + TABLE_NAME + "." + ROW_ID + " DESC";


  private final ExecutorService thumbnailExecutor = Util.newSingleThreadedLifoExecutor();

  public PartDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public InputStream getPartStream(MasterSecret masterSecret, PartId partId)
      throws FileNotFoundException
  {
    return getDataStream(masterSecret, partId, DATA);
  }

  public void updateFailedDownloadedPart(long messageId, PartId partId, PduPart part)
      throws MmsException
  {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    part.setContentDisposition(new byte[0]);
    part.setInProgress(false);

    ContentValues values = getContentValuesForPart(part);

    values.put(DATA, (String)null);

    database.update(TABLE_NAME, values, PART_ID_WHERE, partId.toStrings());
    notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId));
  }

  public PduPart getPart(PartId partId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, null, PART_ID_WHERE, partId.toStrings(), null, null, null);

      if (cursor != null && cursor.moveToFirst()) return getPart(cursor);
      else                                        return null;

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public Cursor getImagesForThread(long threadId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor = database.rawQuery(IMAGES_QUERY, new String[]{threadId+""});
    setNotifyConverationListeners(cursor, threadId);
    return cursor;
  }

  public List<PduPart> getParts(long mmsId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    List<PduPart>  results  = new LinkedList<>();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, MMS_ID + " = ?", new String[] {mmsId+""},
                              null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        results.add(getPart(cursor));
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void deleteParts(long mmsId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {DATA, THUMBNAIL}, MMS_ID + " = ?",
                              new String[] {mmsId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        String data      = cursor.getString(0);
        String thumbnail = cursor.getString(1);

        if (!TextUtils.isEmpty(data)) {
          new File(data).delete();
        }

        if (!TextUtils.isEmpty(thumbnail)) {
          new File(thumbnail).delete();
        }
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    database.delete(TABLE_NAME, MMS_ID + " = ?", new String[] {mmsId + ""});
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void deleteAllParts() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);

    File   partsDirectory = context.getDir("parts", Context.MODE_PRIVATE);
    File[] parts          = partsDirectory.listFiles();

    for (File part : parts) {
      part.delete();
    }
  }

  void insertParts(MasterSecretUnion masterSecret, long mmsId, PduBody body) throws MmsException {
    for (int i=0;i<body.getPartsNum();i++) {
      PduPart part = body.getPart(i);
      PartId partId = insertPart(masterSecret, part, mmsId, part.getThumbnail());
      Log.w(TAG, "Inserted part at ID: " + partId);
    }
  }

  private void getPartValues(PduPart part, Cursor cursor) {

    part.setRowId(cursor.getLong(cursor.getColumnIndexOrThrow(ROW_ID)));
    part.setUniqueId(cursor.getLong(cursor.getColumnIndexOrThrow(UNIQUE_ID)));

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

    int inProgressColumn = cursor.getColumnIndexOrThrow(IN_PROGRESS);

    if (!cursor.isNull(inProgressColumn))
      part.setInProgress(cursor.getInt(inProgressColumn) == 1);

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
    contentValues.put(IN_PROGRESS, part.isInProgress() ? 1 : 0);
    contentValues.put(UNIQUE_ID, part.getUniqueId());

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

  @VisibleForTesting InputStream getDataStream(MasterSecret masterSecret, PartId partId, String dataType)
      throws FileNotFoundException
  {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, new String[]{dataType}, PART_ID_WHERE, partId.toStrings(),
                              null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        if (cursor.isNull(0)) {
          return null;
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
        InputStream in = PartAuthority.getPartStream(context, masterSecret, part.getDataUri());
        return writePartData(masterSecret, part, in);
      } else {
        throw new MmsException("Part is empty!");
      }
    } catch (IOException e) {
      throw new MmsException(e);
    }
  }

  public InputStream getThumbnailStream(MasterSecret masterSecret, PartId partId) throws IOException {
    Log.w(TAG, "getThumbnailStream(" + partId + ")");
    final InputStream dataStream = getDataStream(masterSecret, partId, THUMBNAIL);
    if (dataStream != null) {
      return dataStream;
    }

    try {
      return thumbnailExecutor.submit(new ThumbnailFetchCallable(masterSecret, partId)).get();
    } catch (InterruptedException ie) {
      throw new AssertionError("interrupted");
    } catch (ExecutionException ee) {
      Log.w(TAG, ee);
      throw new IOException(ee);
    }
  }

  private PduPart getPart(Cursor cursor) {
    PduPart part   = new PduPart();

    getPartValues(part, cursor);

    part.setDataUri(PartAuthority.getPartUri(part.getPartId()));

    return part;
  }

  private PartId insertPart(MasterSecretUnion masterSecret, PduPart part, long mmsId, Bitmap thumbnail) throws MmsException {
    Log.w(TAG, "inserting part to mms " + mmsId);
    SQLiteDatabase   database = databaseHelper.getWritableDatabase();
    Pair<File, Long> partData = null;

    if ((part.getData() != null || part.getDataUri() != null) && masterSecret.getMasterSecret().isPresent()) {
      partData = writePartData(masterSecret.getMasterSecret().get(), part);
      Log.w(TAG, "Wrote part to file: " + partData.first.getAbsolutePath());
    }

    ContentValues contentValues = getContentValuesForPart(part);
    contentValues.put(MMS_ID, mmsId);

    if (partData != null) {
      contentValues.put(DATA, partData.first.getAbsolutePath());
      contentValues.put(SIZE, partData.second);
    }

    long   partRowId = database.insert(TABLE_NAME, null, contentValues);
    PartId partId    = new PartId(partRowId, part.getUniqueId());

    if (thumbnail != null && masterSecret.getMasterSecret().isPresent()) {
      Log.w(TAG, "inserting pre-generated thumbnail");
      ThumbnailData data = new ThumbnailData(thumbnail);
      updatePartThumbnail(masterSecret.getMasterSecret().get(), partId, part, data.toDataStream(), data.getAspectRatio());
    } else if (!part.isInProgress()) {
      thumbnailExecutor.submit(new ThumbnailFetchCallable(masterSecret.getMasterSecret().get(), partId));
    }

    return partId;
  }

  public void updateDownloadedPart(MasterSecret masterSecret, long messageId,
                                   PartId partId, PduPart part, InputStream data)
      throws MmsException
  {
    SQLiteDatabase   database = databaseHelper.getWritableDatabase();
    Pair<File, Long> partData = writePartData(masterSecret, part, data);

    part.setContentDisposition(new byte[0]);
    part.setInProgress(false);

    ContentValues values = getContentValuesForPart(part);

    if (partData != null) {
      values.put(DATA, partData.first.getAbsolutePath());
      values.put(SIZE, partData.second);
    }

    database.update(TABLE_NAME, values, PART_ID_WHERE, partId.toStrings());

    thumbnailExecutor.submit(new ThumbnailFetchCallable(masterSecret, partId));

    notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId));
  }

  public void markPartUploaded(long messageId, PduPart part) {
    ContentValues  values   = new ContentValues(1);
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    part.setInProgress(false);
    values.put(IN_PROGRESS, false);
    database.update(TABLE_NAME, values, PART_ID_WHERE, part.getPartId().toStrings());

    notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId));
  }

  public void updatePartData(MasterSecret masterSecret, PduPart part, InputStream data)
      throws MmsException
  {
    SQLiteDatabase   database = databaseHelper.getWritableDatabase();
    Pair<File, Long> partData = writePartData(masterSecret, part, data);

    if (partData == null) throw new MmsException("couldn't update part data");

    Cursor cursor = null;
    try {
      cursor = database.query(TABLE_NAME, new String[]{DATA}, PART_ID_WHERE,
                              part.getPartId().toStrings(), null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        int dataColumn = cursor.getColumnIndexOrThrow(DATA);
        if (!cursor.isNull(dataColumn) && !new File(cursor.getString(dataColumn)).delete()) {
            Log.w(TAG, "Couldn't delete old part file");
        }
      }
    } finally {
      if (cursor != null) cursor.close();
    }
    ContentValues values = new ContentValues(2);
    values.put(DATA, partData.first.getAbsolutePath());
    values.put(SIZE, partData.second);

    part.setDataSize(partData.second);

    database.update(TABLE_NAME, values, PART_ID_WHERE, part.getPartId().toStrings());
    Log.w(TAG, "updated data for part #" + part.getPartId());
  }

  public void updatePartThumbnail(MasterSecret masterSecret, PartId partId, PduPart part, InputStream in, float aspectRatio)
      throws MmsException
  {
    Log.w(TAG, "updating part thumbnail for #" + partId);

    Pair<File, Long> thumbnailFile = writePartData(masterSecret, part, in);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues(2);

    values.put(THUMBNAIL, thumbnailFile.first.getAbsolutePath());
    values.put(ASPECT_RATIO, aspectRatio);

    database.update(TABLE_NAME, values, PART_ID_WHERE, partId.toStrings());
  }

  public static class ImageRecord {
    private PartId partId;
    private String contentType;
    private String address;
    private long   date;

    private ImageRecord(PartId partId, String contentType, String address, long date) {
      this.partId      = partId;
      this.contentType = contentType;
      this.address     = address;
      this.date        = date;
    }

    public static ImageRecord from(Cursor cursor) {
      PartId partId = new PartId(cursor.getLong(cursor.getColumnIndexOrThrow(ROW_ID)),
                                 cursor.getLong(cursor.getColumnIndexOrThrow(UNIQUE_ID)));

      return new ImageRecord(partId,
                             cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_TYPE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS)),
                             cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_RECEIVED)) * 1000);
    }

    public PartId getPartId() {
      return partId;
    }

    public String getContentType() {
      return contentType;
    }

    public String getAddress() {
      return address;
    }

    public long getDate() {
      return date;
    }

    public Uri getUri() {
      return PartAuthority.getPartUri(partId);
    }
  }

  @VisibleForTesting class ThumbnailFetchCallable implements Callable<InputStream> {
    private final MasterSecret masterSecret;
    private final PartId       partId;

    public ThumbnailFetchCallable(MasterSecret masterSecret, PartId partId) {
      this.masterSecret = masterSecret;
      this.partId       = partId;
    }

    @Override
    public InputStream call() throws Exception {
      final InputStream stream = getDataStream(masterSecret, partId, THUMBNAIL);
      if (stream != null) {
        return stream;
      }

      PduPart part = getPart(partId);
      ThumbnailData data = MediaUtil.generateThumbnail(context, masterSecret, part.getDataUri(), Util.toIsoString(part.getContentType()));
      if (data == null) {
        return null;
      }
      updatePartThumbnail(masterSecret, partId, part, data.toDataStream(), data.getAspectRatio());

      return getDataStream(masterSecret, partId, THUMBNAIL);
    }
  }

  public static class PartId {

    private final long rowId;
    private final long uniqueId;

    public PartId(long rowId, long uniqueId) {
      this.rowId    = rowId;
      this.uniqueId = uniqueId;
    }

    public long getRowId() {
      return rowId;
    }

    public long getUniqueId() {
      return uniqueId;
    }

    public String[] toStrings() {
      return new String[] {String.valueOf(rowId), String.valueOf(uniqueId)};
    }

    public String toString() {
      return "(row id: " + rowId + ", unique ID: " + uniqueId + ")";
    }

    public boolean isValid() {
      return rowId >= 0 && uniqueId >= 0;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PartId partId = (PartId)o;

      if (rowId != partId.rowId) return false;
      return uniqueId == partId.uniqueId;
    }

    @Override public int hashCode() {
      return Util.hashCode(rowId, uniqueId);
    }
  }
}
