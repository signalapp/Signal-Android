/*
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
import android.graphics.Bitmap;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.bumptech.glide.Glide;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.MediaStream;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.MediaMetadataRetrieverUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MediaUtil.ThumbnailData;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.video.EncryptedMediaDataSource;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class AttachmentDatabase extends Database {
  
  private static final String TAG = AttachmentDatabase.class.getSimpleName();

  public  static final String TABLE_NAME             = "part";
  public  static final String ROW_ID                 = "_id";
          static final String ATTACHMENT_JSON_ALIAS  = "attachment_json";
  public  static final String MMS_ID                 = "mid";
          static final String CONTENT_TYPE           = "ct";
          static final String NAME                   = "name";
          static final String CONTENT_DISPOSITION    = "cd";
          static final String CONTENT_LOCATION       = "cl";
  public  static final String DATA                   = "_data";
          static final String TRANSFER_STATE         = "pending_push";
  public  static final String SIZE                   = "data_size";
          static final String FILE_NAME              = "file_name";
  public  static final String THUMBNAIL              = "thumbnail";
          static final String THUMBNAIL_ASPECT_RATIO = "aspect_ratio";
  public  static final String UNIQUE_ID              = "unique_id";
          static final String DIGEST                 = "digest";
          static final String VOICE_NOTE             = "voice_note";
          static final String QUOTE                  = "quote";
  public  static final String STICKER_PACK_ID        = "sticker_pack_id";
  public  static final String STICKER_PACK_KEY       = "sticker_pack_key";
          static final String STICKER_ID             = "sticker_id";
          static final String FAST_PREFLIGHT_ID      = "fast_preflight_id";
  public  static final String DATA_RANDOM            = "data_random";
  private static final String THUMBNAIL_RANDOM       = "thumbnail_random";
          static final String WIDTH                  = "width";
          static final String HEIGHT                 = "height";
          static final String CAPTION                = "caption";
  private static final String DATA_HASH              = "data_hash";
          static final String BLUR_HASH              = "blur_hash";
          static final String TRANSFORM_PROPERTIES   = "transform_properties";

  public  static final String DIRECTORY              = "parts";

  public static final int TRANSFER_PROGRESS_DONE    = 0;
  public static final int TRANSFER_PROGRESS_STARTED = 1;
  public static final int TRANSFER_PROGRESS_PENDING = 2;
  public static final int TRANSFER_PROGRESS_FAILED  = 3;

  private static final String PART_ID_WHERE     = ROW_ID + " = ? AND " + UNIQUE_ID + " = ?";
  private static final String PART_ID_WHERE_NOT = ROW_ID + " != ? AND " + UNIQUE_ID + " != ?";

  private static final String[] PROJECTION = new String[] {ROW_ID,
                                                           MMS_ID, CONTENT_TYPE, NAME, CONTENT_DISPOSITION,
                                                           CONTENT_LOCATION, DATA, THUMBNAIL, TRANSFER_STATE,
                                                           SIZE, FILE_NAME, THUMBNAIL, THUMBNAIL_ASPECT_RATIO,
                                                           UNIQUE_ID, DIGEST, FAST_PREFLIGHT_ID, VOICE_NOTE,
                                                           QUOTE, DATA_RANDOM, THUMBNAIL_RANDOM, WIDTH, HEIGHT,
                                                           CAPTION, STICKER_PACK_ID, STICKER_PACK_KEY, STICKER_ID,
                                                           DATA_HASH, BLUR_HASH, TRANSFORM_PROPERTIES};

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ROW_ID + " INTEGER PRIMARY KEY, " +
    MMS_ID + " INTEGER, " + "seq" + " INTEGER DEFAULT 0, "                        +
    CONTENT_TYPE + " TEXT, " + NAME + " TEXT, " + "chset" + " INTEGER, "             +
    CONTENT_DISPOSITION + " TEXT, " + "fn" + " TEXT, " + "cid" + " TEXT, "  +
    CONTENT_LOCATION + " TEXT, " + "ctt_s" + " INTEGER, "                 +
    "ctt_t" + " TEXT, " + "encrypted" + " INTEGER, "                         +
    TRANSFER_STATE + " INTEGER, "+ DATA + " TEXT, " + SIZE + " INTEGER, "   +
    FILE_NAME + " TEXT, " + THUMBNAIL + " TEXT, " + THUMBNAIL_ASPECT_RATIO + " REAL, " +
    UNIQUE_ID + " INTEGER NOT NULL, " + DIGEST + " BLOB, " + FAST_PREFLIGHT_ID + " TEXT, " +
    VOICE_NOTE + " INTEGER DEFAULT 0, " + DATA_RANDOM + " BLOB, " + THUMBNAIL_RANDOM + " BLOB, " +
    QUOTE + " INTEGER DEFAULT 0, " + WIDTH + " INTEGER DEFAULT 0, " + HEIGHT + " INTEGER DEFAULT 0, " +
    CAPTION + " TEXT DEFAULT NULL, " + STICKER_PACK_ID + " TEXT DEFAULT NULL, " +
    STICKER_PACK_KEY + " DEFAULT NULL, " + STICKER_ID + " INTEGER DEFAULT -1, " +
    DATA_HASH + " TEXT DEFAULT NULL, " + BLUR_HASH + " TEXT DEFAULT NULL, " +
    TRANSFORM_PROPERTIES + " TEXT DEFAULT NULL);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS part_mms_id_index ON " + TABLE_NAME + " (" + MMS_ID + ");",
    "CREATE INDEX IF NOT EXISTS pending_push_index ON " + TABLE_NAME + " (" + TRANSFER_STATE + ");",
    "CREATE INDEX IF NOT EXISTS part_sticker_pack_id_index ON " + TABLE_NAME + " (" + STICKER_PACK_ID + ");",
    "CREATE INDEX IF NOT EXISTS part_data_hash_index ON " + TABLE_NAME + " (" + DATA_HASH + ");"
  };

  private final ExecutorService thumbnailExecutor = Util.newSingleThreadedLifoExecutor();

  private final AttachmentSecret attachmentSecret;

  public AttachmentDatabase(Context context, SQLCipherOpenHelper databaseHelper, AttachmentSecret attachmentSecret) {
    super(context, databaseHelper);
    this.attachmentSecret = attachmentSecret;
  }

  public @NonNull InputStream getAttachmentStream(AttachmentId attachmentId, long offset)
      throws IOException
  {
    InputStream dataStream = getDataStream(attachmentId, DATA, offset);

    if (dataStream == null) throw new IOException("No stream for: " + attachmentId);
    else                    return dataStream;
  }

  public @NonNull InputStream getThumbnailStream(@NonNull AttachmentId attachmentId)
      throws IOException
  {
    Log.d(TAG, "getThumbnailStream(" + attachmentId + ")");
    InputStream dataStream = getDataStream(attachmentId, THUMBNAIL, 0);

    if (dataStream != null) {
      return dataStream;
    }

    try {
      InputStream generatedStream = thumbnailExecutor.submit(new ThumbnailFetchCallable(attachmentId)).get();

      if (generatedStream == null) throw new FileNotFoundException("No thumbnail stream available: " + attachmentId);
      else                         return generatedStream;
    } catch (InterruptedException ie) {
      throw new AssertionError("interrupted");
    } catch (ExecutionException ee) {
      Log.w(TAG, ee);
      throw new IOException(ee);
    }
  }

  public boolean containsStickerPackId(@NonNull String stickerPackId) {
    String   selection = STICKER_PACK_ID + " = ?";
    String[] args      = new String[] { stickerPackId };

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, selection, args, null, null, null, "1")) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  public void setTransferProgressFailed(AttachmentId attachmentId, long mmsId)
      throws MmsException
  {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues();
    values.put(TRANSFER_STATE, TRANSFER_PROGRESS_FAILED);

    database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings());
    notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(mmsId));
  }

  public @Nullable DatabaseAttachment getAttachment(@NonNull AttachmentId attachmentId)
  {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, PROJECTION, PART_ID_WHERE, attachmentId.toStrings(), null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        List<DatabaseAttachment> list = getAttachment(cursor);

        if (list != null && list.size() > 0) {
          return list.get(0);
        }
      }

      return null;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public @NonNull List<DatabaseAttachment> getAttachmentsForMessage(long mmsId) {
    SQLiteDatabase           database = databaseHelper.getReadableDatabase();
    List<DatabaseAttachment> results  = new LinkedList<>();
    Cursor                   cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, PROJECTION, MMS_ID + " = ?", new String[] {mmsId+""},
                              null, null, UNIQUE_ID + " ASC, " + ROW_ID + " ASC");

      while (cursor != null && cursor.moveToNext()) {
        results.addAll(getAttachment(cursor));
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public boolean hasAttachmentFilesForMessage(long mmsId) {
    String   selection = MMS_ID + " = ? AND (" + DATA + " NOT NULL OR " + TRANSFER_STATE + " != ?)";
    String[] args      = new String[] { String.valueOf(mmsId), String.valueOf(TRANSFER_PROGRESS_DONE) };

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, selection, args, null, null, "1")) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  public @NonNull List<DatabaseAttachment> getPendingAttachments() {
    final SQLiteDatabase           database    = databaseHelper.getReadableDatabase();
    final List<DatabaseAttachment> attachments = new LinkedList<>();

    Cursor cursor = null;
    try {
      cursor = database.query(TABLE_NAME, PROJECTION, TRANSFER_STATE + " = ?", new String[] {String.valueOf(TRANSFER_PROGRESS_STARTED)}, null, null, null);
      while (cursor != null && cursor.moveToNext()) {
        attachments.addAll(getAttachment(cursor));
      }
    } finally {
      if (cursor != null) cursor.close();
    }

    return attachments;
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void deleteAttachmentsForMessage(long mmsId) {
    Log.d(TAG, "[deleteAttachmentsForMessage] mmsId: " + mmsId);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {DATA, THUMBNAIL, CONTENT_TYPE, ROW_ID, UNIQUE_ID}, MMS_ID + " = ?",
                              new String[] {mmsId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        deleteAttachmentOnDisk(cursor.getString(cursor.getColumnIndex(DATA)),
                               cursor.getString(cursor.getColumnIndex(THUMBNAIL)),
                               cursor.getString(cursor.getColumnIndex(CONTENT_TYPE)),
                               new AttachmentId(cursor.getLong(cursor.getColumnIndex(ROW_ID)),
                                                cursor.getLong(cursor.getColumnIndex(UNIQUE_ID))));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    database.delete(TABLE_NAME, MMS_ID + " = ?", new String[] {mmsId + ""});
    notifyAttachmentListeners();
  }

  public void deleteAttachmentFilesForMessage(long mmsId) {
    Log.d(TAG, "[deleteAttachmentFilesForMessage] mmsId: " + mmsId);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {DATA, THUMBNAIL, CONTENT_TYPE, ROW_ID, UNIQUE_ID}, MMS_ID + " = ?",
          new String[] {mmsId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        deleteAttachmentOnDisk(cursor.getString(cursor.getColumnIndex(DATA)),
                               cursor.getString(cursor.getColumnIndex(THUMBNAIL)),
                               cursor.getString(cursor.getColumnIndex(CONTENT_TYPE)),
                               new AttachmentId(cursor.getLong(cursor.getColumnIndex(ROW_ID)),
                                                cursor.getLong(cursor.getColumnIndex(UNIQUE_ID))));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    ContentValues values = new ContentValues();
    values.put(DATA, (String) null);
    values.put(DATA_RANDOM, (byte[]) null);
    values.put(DATA_HASH, (String) null);
    values.put(THUMBNAIL, (String) null);
    values.put(THUMBNAIL_RANDOM, (byte[]) null);
    values.put(FILE_NAME, (String) null);
    values.put(CAPTION, (String) null);
    values.put(SIZE, 0);
    values.put(WIDTH, 0);
    values.put(HEIGHT, 0);
    values.put(TRANSFER_STATE, TRANSFER_PROGRESS_DONE);
    values.put(BLUR_HASH, (String) null);

    database.update(TABLE_NAME, values, MMS_ID + " = ?", new String[] {mmsId + ""});
    notifyAttachmentListeners();

    long threadId = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(mmsId);
    if (threadId > 0) {
      notifyConversationListeners(threadId);
    }
  }


  public void deleteAttachment(@NonNull AttachmentId id) {
    Log.d(TAG, "[deleteAttachment] attachmentId: " + id);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    try (Cursor cursor = database.query(TABLE_NAME,
                                        new String[]{DATA, THUMBNAIL, CONTENT_TYPE},
                                        PART_ID_WHERE,
                                        id.toStrings(),
                                        null,
                                        null,
                                        null))
    {
      if (cursor == null || !cursor.moveToNext()) {
        Log.w(TAG, "Tried to delete an attachment, but it didn't exist.");
        return;
      }
      String data        = cursor.getString(cursor.getColumnIndex(DATA));
      String thumbnail   = cursor.getString(cursor.getColumnIndex(THUMBNAIL));
      String contentType = cursor.getString(cursor.getColumnIndex(CONTENT_TYPE));

      database.delete(TABLE_NAME, PART_ID_WHERE, id.toStrings());
      deleteAttachmentOnDisk(data, thumbnail, contentType, id);
      notifyAttachmentListeners();
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  void deleteAllAttachments() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);

    File   attachmentsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
    File[] attachments          = attachmentsDirectory.listFiles();

    for (File attachment : attachments) {
      attachment.delete();
    }

    notifyAttachmentListeners();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void deleteAttachmentOnDisk(@Nullable String data,
                                      @Nullable String thumbnail,
                                      @Nullable String contentType,
                                      @NonNull AttachmentId attachmentId)
  {
    boolean dataInUse = isDataUsedByAnotherAttachment(data, attachmentId);

    if (dataInUse) {
      Log.i(TAG, "[deleteAttachmentOnDisk] Attachment in use. Skipping deletion. " + data + " " + attachmentId);
    } else {
      Log.i(TAG, "[deleteAttachmentOnDisk] No other users of this attachment. Safe to delete. " + data + " " + attachmentId);
    }

    if (!TextUtils.isEmpty(data) && !dataInUse) {
      new File(data).delete();
    }

    if (!TextUtils.isEmpty(thumbnail)) {
      new File(thumbnail).delete();
    }

    if (MediaUtil.isImageType(contentType) || thumbnail != null) {
      Glide.get(context).clearDiskCache();
    }
  }

  private boolean isDataUsedByAnotherAttachment(@Nullable String data, @NonNull AttachmentId attachmentId) {
    if (data == null) return false;

    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    long           matches  = DatabaseUtils.longForQuery(database,
                                                         "SELECT count(*) FROM " + TABLE_NAME + " WHERE " + DATA + " = ? AND " + UNIQUE_ID + " != ? AND " + ROW_ID + " != ?;",
                                                         new String[]{data,
                                                                      Long.toString(attachmentId.getUniqueId()),
                                                                      Long.toString(attachmentId.getRowId())});

    return matches != 0;
  }

  public void insertAttachmentsForPlaceholder(long mmsId, @NonNull AttachmentId attachmentId, @NonNull InputStream inputStream)
      throws MmsException
  {
    DatabaseAttachment placeholder = getAttachment(attachmentId);
    SQLiteDatabase     database    = databaseHelper.getWritableDatabase();
    ContentValues      values      = new ContentValues();
    DataInfo           oldInfo     = getAttachmentDataFileInfo(attachmentId, DATA);
    DataInfo           dataInfo    = setAttachmentData(inputStream, false, attachmentId);

    if (oldInfo != null) {
      updateAttachmentDataHash(database, oldInfo.hash, dataInfo);
    }

    if (placeholder != null && placeholder.isQuote() && !placeholder.getContentType().startsWith("image")) {
      values.put(THUMBNAIL, dataInfo.file.getAbsolutePath());
      values.put(THUMBNAIL_RANDOM, dataInfo.random);
    } else {
      values.put(DATA, dataInfo.file.getAbsolutePath());
      values.put(SIZE, dataInfo.length);
      values.put(DATA_RANDOM, dataInfo.random);
      values.put(DATA_HASH, dataInfo.hash);
    }

    if (placeholder != null && placeholder.getBlurHash() != null) {
      values.put(BLUR_HASH, placeholder.getBlurHash().getHash());
    }

    values.put(TRANSFER_STATE, TRANSFER_PROGRESS_DONE);
    values.put(CONTENT_LOCATION, (String)null);
    values.put(CONTENT_DISPOSITION, (String)null);
    values.put(DIGEST, (byte[])null);
    values.put(NAME, (String) null);
    values.put(FAST_PREFLIGHT_ID, (String)null);

    if (database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings()) == 0) {
      //noinspection ResultOfMethodCallIgnored
      dataInfo.file.delete();
    } else {
      notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(mmsId));
      notifyConversationListListeners();
    }

    thumbnailExecutor.submit(new ThumbnailFetchCallable(attachmentId));
  }

  private static @Nullable String getBlurHashStringOrNull(@Nullable BlurHash blurHash) {
    if (blurHash == null) return null;
    return blurHash.getHash();
  }

  public void copyAttachmentData(@NonNull AttachmentId sourceId, @NonNull AttachmentId destinationId)
      throws MmsException
  {
    DatabaseAttachment sourceAttachment = getAttachment(sourceId);

    if (sourceAttachment == null) {
      throw new MmsException("Cannot find attachment for source!");
    }

    SQLiteDatabase database       = databaseHelper.getWritableDatabase();
    DataInfo       sourceDataInfo = getAttachmentDataFileInfo(sourceId, DATA);

    if (sourceDataInfo == null) {
      throw new MmsException("No attachment data found for source!");
    }

    ContentValues contentValues = new ContentValues();

    contentValues.put(DATA, sourceDataInfo.file.getAbsolutePath());
    contentValues.put(DATA_HASH, sourceDataInfo.hash);
    contentValues.put(SIZE, sourceDataInfo.length);
    contentValues.put(DATA_RANDOM, sourceDataInfo.random);

    contentValues.put(TRANSFER_STATE, sourceAttachment.getTransferState());
    contentValues.put(CONTENT_LOCATION, sourceAttachment.getLocation());
    contentValues.put(DIGEST, sourceAttachment.getDigest());
    contentValues.put(CONTENT_DISPOSITION, sourceAttachment.getKey());
    contentValues.put(NAME, sourceAttachment.getRelay());
    contentValues.put(SIZE, sourceAttachment.getSize());
    contentValues.put(FAST_PREFLIGHT_ID, sourceAttachment.getFastPreflightId());
    contentValues.put(WIDTH, sourceAttachment.getWidth());
    contentValues.put(HEIGHT, sourceAttachment.getHeight());
    contentValues.put(CONTENT_TYPE, sourceAttachment.getContentType());
    contentValues.put(BLUR_HASH, getBlurHashStringOrNull(sourceAttachment.getBlurHash()));

    database.update(TABLE_NAME, contentValues, PART_ID_WHERE, destinationId.toStrings());
  }

  public void updateAttachmentAfterUpload(@NonNull AttachmentId id, @NonNull Attachment attachment) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues();

    values.put(TRANSFER_STATE, TRANSFER_PROGRESS_DONE);
    values.put(CONTENT_LOCATION, attachment.getLocation());
    values.put(DIGEST, attachment.getDigest());
    values.put(CONTENT_DISPOSITION, attachment.getKey());
    values.put(NAME, attachment.getRelay());
    values.put(SIZE, attachment.getSize());
    values.put(FAST_PREFLIGHT_ID, attachment.getFastPreflightId());
    values.put(BLUR_HASH, getBlurHashStringOrNull(attachment.getBlurHash()));

    database.update(TABLE_NAME, values, PART_ID_WHERE, id.toStrings());
  }

  @NonNull Map<Attachment, AttachmentId> insertAttachmentsForMessage(long mmsId, @NonNull List<Attachment> attachments, @NonNull List<Attachment> quoteAttachment)
      throws MmsException
  {
    Log.d(TAG, "insertParts(" + attachments.size() + ")");

    Map<Attachment, AttachmentId> insertedAttachments = new HashMap<>();

    for (Attachment attachment : attachments) {
      AttachmentId attachmentId = insertAttachment(mmsId, attachment, attachment.isQuote());
      insertedAttachments.put(attachment, attachmentId);
      Log.i(TAG, "Inserted attachment at ID: " + attachmentId);
    }

    for (Attachment attachment : quoteAttachment) {
      AttachmentId attachmentId = insertAttachment(mmsId, attachment, true);
      insertedAttachments.put(attachment, attachmentId);
      Log.i(TAG, "Inserted quoted attachment at ID: " + attachmentId);
    }

    return insertedAttachments;
  }

  public void updateAttachmentData(@NonNull DatabaseAttachment databaseAttachment,
                                   @NonNull MediaStream mediaStream)
      throws MmsException
  {
    SQLiteDatabase database    = databaseHelper.getWritableDatabase();
    DataInfo       oldDataInfo = getAttachmentDataFileInfo(databaseAttachment.getAttachmentId(), DATA);

    if (oldDataInfo == null) {
      throw new MmsException("No attachment data found!");
    }

    DataInfo dataInfo = setAttachmentData(oldDataInfo.file,
                                          mediaStream.getStream(),
                                          false,
                                          databaseAttachment.getAttachmentId());

    ContentValues contentValues = new ContentValues();
    contentValues.put(SIZE, dataInfo.length);
    contentValues.put(CONTENT_TYPE, mediaStream.getMimeType());
    contentValues.put(WIDTH, mediaStream.getWidth());
    contentValues.put(HEIGHT, mediaStream.getHeight());
    contentValues.put(DATA, dataInfo.file.getAbsolutePath());
    contentValues.put(DATA_RANDOM, dataInfo.random);
    contentValues.put(DATA_HASH, dataInfo.hash);

    int updateCount = updateAttachmentAndMatchingHashes(database, databaseAttachment.getAttachmentId(), oldDataInfo.hash, contentValues);
    Log.i(TAG, "[updateAttachmentData] Updated " + updateCount + " rows.");
  }

  public void markAttachmentAsTransformed(@NonNull AttachmentId attachmentId) {
    DataInfo dataInfo = getAttachmentDataFileInfo(attachmentId, DATA);

    if (dataInfo == null) {
      Log.w(TAG, "[markAttachmentAsTransformed] No data info found!");
      return;
    }

    ContentValues contentValues = new ContentValues();
    contentValues.put(TRANSFORM_PROPERTIES, TransformProperties.forSkipTransform().serialize());

    int updateCount = updateAttachmentAndMatchingHashes(databaseHelper.getWritableDatabase(), attachmentId, dataInfo.hash, contentValues);
    Log.i(TAG, "[markAttachmentAsTransformed] Updated " + updateCount + " rows.");
  }

  private static int updateAttachmentAndMatchingHashes(@NonNull SQLiteDatabase database,
                                                       @NonNull AttachmentId attachmentId,
                                                       @Nullable String dataHash,
                                                       @NonNull ContentValues contentValues)
  {
    String   selection = "(" + ROW_ID + " = ? AND " + UNIQUE_ID + " = ?) OR " +
                         "(" + DATA_HASH + " NOT NULL AND " + DATA_HASH + " = ?)";
    String[] args      = new String[]{String.valueOf(attachmentId.getRowId()),
                                      String.valueOf(attachmentId.getUniqueId()),
                                      String.valueOf(dataHash)};

    return database.update(TABLE_NAME, contentValues, selection, args);
  }

  private static void updateAttachmentDataHash(@NonNull SQLiteDatabase database,
                                               @NonNull String oldHash,
                                               @NonNull DataInfo newData)
  {
    if (oldHash == null) return;

    ContentValues contentValues = new ContentValues();
    contentValues.put(DATA, newData.file.getAbsolutePath());
    contentValues.put(DATA_RANDOM, newData.random);
    contentValues.put(DATA_HASH, newData.hash);
    database.update(TABLE_NAME,
                    contentValues,
                    DATA_HASH + " = ?",
                    new String[]{oldHash});
  }

  public void updateAttachmentFileName(@NonNull AttachmentId attachmentId,
                                       @Nullable String fileName)
  {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(FILE_NAME, StorageUtil.getCleanFileName(fileName));

    database.update(TABLE_NAME, contentValues, PART_ID_WHERE, attachmentId.toStrings());
  }

  public void markAttachmentUploaded(long messageId, Attachment attachment) {
    ContentValues  values   = new ContentValues(1);
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    values.put(TRANSFER_STATE, TRANSFER_PROGRESS_DONE);
    database.update(TABLE_NAME, values, PART_ID_WHERE, ((DatabaseAttachment)attachment).getAttachmentId().toStrings());

    notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId));
  }

  public void setTransferState(long messageId, @NonNull Attachment attachment, int transferState) {
    if (!(attachment instanceof DatabaseAttachment)) {
      throw new AssertionError("Attempt to update attachment that doesn't belong to DB!");
    }

    setTransferState(messageId, ((DatabaseAttachment) attachment).getAttachmentId(), transferState);
  }

  public void setTransferState(long messageId, @NonNull AttachmentId attachmentId, int transferState) {
    final ContentValues  values   = new ContentValues(1);
    final SQLiteDatabase database = databaseHelper.getWritableDatabase();

    values.put(TRANSFER_STATE, transferState);
    database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings());
    notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId));
  }

  /**
   * Returns (pack_id, pack_key) pairs that are referenced in attachments but not in the stickers
   * database.
   */
  public @Nullable Cursor getUnavailableStickerPacks() {
    String query = "SELECT DISTINCT " + STICKER_PACK_ID + ", " + STICKER_PACK_KEY +
                   " FROM " + TABLE_NAME +
                   " WHERE " +
                     STICKER_PACK_ID  + " NOT NULL AND " +
                     STICKER_PACK_KEY + " NOT NULL AND " +
                     STICKER_PACK_ID  + " NOT IN (" +
                       "SELECT DISTINCT " + StickerDatabase.PACK_ID + " FROM " + StickerDatabase.TABLE_NAME +
                     ")";

    return databaseHelper.getReadableDatabase().rawQuery(query, null);
  }

  public boolean hasStickerAttachments() {
    String selection = STICKER_PACK_ID + " NOT NULL";

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, selection, null, null, null, null, "1")) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  @SuppressWarnings("WeakerAccess")
  @VisibleForTesting
  protected @Nullable InputStream getDataStream(AttachmentId attachmentId, String dataType, long offset)
  {
    DataInfo dataInfo = getAttachmentDataFileInfo(attachmentId, dataType);

    if (dataInfo == null) {
      return null;
    }

    try {
      if (dataInfo.random != null && dataInfo.random.length == 32) {
        return ModernDecryptingPartInputStream.createFor(attachmentSecret, dataInfo.random, dataInfo.file, offset);
      } else {
        InputStream stream  = ClassicDecryptingPartInputStream.createFor(attachmentSecret, dataInfo.file);
        long        skipped = stream.skip(offset);

        if (skipped != offset) {
          Log.w(TAG, "Skip failed: " + skipped + " vs " + offset);
          return null;
        }

        return stream;
      }
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private @Nullable DataInfo getAttachmentDataFileInfo(@NonNull AttachmentId attachmentId, @NonNull String dataType)
  {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    String randomColumn;

    switch (dataType) {
      case DATA:      randomColumn = DATA_RANDOM;      break;
      case THUMBNAIL: randomColumn = THUMBNAIL_RANDOM; break;
      default:throw   new AssertionError("Unknown data type: " + dataType);
    }

    try {
      cursor = database.query(TABLE_NAME, new String[]{dataType, SIZE, randomColumn, DATA_HASH}, PART_ID_WHERE, attachmentId.toStrings(),
                              null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        if (cursor.isNull(cursor.getColumnIndexOrThrow(dataType))) {
          return null;
        }

        return new DataInfo(new File(cursor.getString(cursor.getColumnIndexOrThrow(dataType))),
                            cursor.getLong(cursor.getColumnIndexOrThrow(SIZE)),
                            cursor.getBlob(cursor.getColumnIndexOrThrow(randomColumn)),
                            cursor.getString(cursor.getColumnIndexOrThrow(DATA_HASH)));
      } else {
        return null;
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

  }

  private @NonNull DataInfo setAttachmentData(@NonNull Uri uri,
                                              boolean isThumbnail,
                                              @Nullable AttachmentId attachmentId)
      throws MmsException
  {
    try {
      InputStream inputStream = PartAuthority.getAttachmentStream(context, uri);
      return setAttachmentData(inputStream, isThumbnail, attachmentId);
    } catch (IOException e) {
      throw new MmsException(e);
    }
  }

  private @NonNull DataInfo setAttachmentData(@NonNull InputStream in,
                                              boolean isThumbnail,
                                              @Nullable AttachmentId attachmentId)
      throws MmsException
  {
    try {
      File partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
      File dataFile       = File.createTempFile("part", ".mms", partsDirectory);
      return setAttachmentData(dataFile, in, isThumbnail, attachmentId);
    } catch (IOException e) {
      throw new MmsException(e);
    }
  }

  private @NonNull DataInfo setAttachmentData(@NonNull File destination,
                                              @NonNull InputStream in,
                                              boolean isThumbnail,
                                              @Nullable AttachmentId attachmentId)
      throws MmsException
  {
    try {
      MessageDigest              messageDigest     = MessageDigest.getInstance("SHA-256");
      DigestInputStream          digestInputStream = new DigestInputStream(in, messageDigest);
      Pair<byte[], OutputStream> out               = ModernEncryptingPartOutputStream.createFor(attachmentSecret, destination, false);
      long                       length            = Util.copy(digestInputStream, out.second);
      String                     hash              = Base64.encodeBytes(digestInputStream.getMessageDigest().digest());

      if (!isThumbnail) {
        SQLiteDatabase     database       = databaseHelper.getWritableDatabase();
        Optional<DataInfo> sharedDataInfo = findDuplicateDataFileInfo(database, hash, attachmentId);
        if (sharedDataInfo.isPresent()) {
          Log.i(TAG, "[setAttachmentData] Duplicate data file found! " + sharedDataInfo.get().file.getAbsolutePath());
          if (!destination.equals(sharedDataInfo.get().file) && destination.delete()) {
            Log.i(TAG, "[setAttachmentData] Deleted original file. " + destination);
          }
          return sharedDataInfo.get();
        } else {
          Log.i(TAG, "[setAttachmentData] No matching attachment data found. " + destination.getAbsolutePath());
        }
      }

      return new DataInfo(destination, length, out.first, hash);
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new MmsException(e);
    }
  }

  private static @NonNull Optional<DataInfo> findDuplicateDataFileInfo(@NonNull SQLiteDatabase database,
                                                                       @NonNull String hash,
                                                                       @Nullable AttachmentId excludedAttachmentId)
  {

    Pair<String, String[]> selectorArgs = buildSharedFileSelectorArgs(hash, excludedAttachmentId);
    try (Cursor cursor = database.query(TABLE_NAME,
                                        new String[]{DATA, DATA_RANDOM, SIZE},
                                        selectorArgs.first,
                                        selectorArgs.second,
                                        null,
                                        null,
                                        null,
                                        "1"))
    {
      if (cursor == null || !cursor.moveToFirst()) return Optional.absent();

      if (cursor.getCount() > 0) {
        DataInfo dataInfo = new DataInfo(new File(cursor.getString(cursor.getColumnIndex(DATA))),
                                         cursor.getLong(cursor.getColumnIndex(SIZE)),
                                         cursor.getBlob(cursor.getColumnIndex(DATA_RANDOM)),
                                         hash);
        return Optional.of(dataInfo);
      } else {
        return Optional.absent();
      }
    }
  }

  private static Pair<String, String[]> buildSharedFileSelectorArgs(@NonNull String newHash,
                                                                    @Nullable AttachmentId attachmentId)
  {
    final String   selector;
    final String[] selection;

    if (attachmentId == null) {
      selector  = DATA_HASH + " = ?";
      selection = new String[]{newHash};
    } else {
      selector  = PART_ID_WHERE_NOT + " AND " + DATA_HASH + " = ?";
      selection = new String[]{Long.toString(attachmentId.getRowId()),
                               Long.toString(attachmentId.getUniqueId()),
                               newHash};
    }

    return Pair.create(selector, selection);
  }

  public List<DatabaseAttachment> getAttachment(@NonNull Cursor cursor) {
    try {
      if (cursor.getColumnIndex(AttachmentDatabase.ATTACHMENT_JSON_ALIAS) != -1) {
        if (cursor.isNull(cursor.getColumnIndexOrThrow(ATTACHMENT_JSON_ALIAS))) {
          return new LinkedList<>();
        }

        List<DatabaseAttachment> result = new LinkedList<>();
        JSONArray                array  = new JSONArray(cursor.getString(cursor.getColumnIndexOrThrow(ATTACHMENT_JSON_ALIAS)));

        for (int i=0;i<array.length();i++) {
          JsonUtils.SaneJSONObject object = new JsonUtils.SaneJSONObject(array.getJSONObject(i));

          if (!object.isNull(ROW_ID)) {
            result.add(new DatabaseAttachment(new AttachmentId(object.getLong(ROW_ID), object.getLong(UNIQUE_ID)),
                                              object.getLong(MMS_ID),
                                              !TextUtils.isEmpty(object.getString(DATA)),
                                              !TextUtils.isEmpty(object.getString(THUMBNAIL)),
                                              object.getString(CONTENT_TYPE),
                                              object.getInt(TRANSFER_STATE),
                                              object.getLong(SIZE),
                                              object.getString(FILE_NAME),
                                              object.getString(CONTENT_LOCATION),
                                              object.getString(CONTENT_DISPOSITION),
                                              object.getString(NAME),
                                              null,
                                              object.getString(FAST_PREFLIGHT_ID),
                                              object.getInt(VOICE_NOTE) == 1,
                                              object.getInt(WIDTH),
                                              object.getInt(HEIGHT),
                                              object.getInt(QUOTE) == 1,
                                              object.getString(CAPTION),
                                              object.getInt(STICKER_ID) >= 0
                                                  ? new StickerLocator(object.getString(STICKER_PACK_ID),
                                                                       object.getString(STICKER_PACK_KEY),
                                                                       object.getInt(STICKER_ID))
                                                  : null,
                                              BlurHash.parseOrNull(object.getString(BLUR_HASH)),
                                              TransformProperties.parse(object.getString(TRANSFORM_PROPERTIES))));
          }
        }

        return result;
      } else {
        return Collections.singletonList(new DatabaseAttachment(new AttachmentId(cursor.getLong(cursor.getColumnIndexOrThrow(ROW_ID)),
                                                                                 cursor.getLong(cursor.getColumnIndexOrThrow(UNIQUE_ID))),
                                                                cursor.getLong(cursor.getColumnIndexOrThrow(MMS_ID)),
                                                                !cursor.isNull(cursor.getColumnIndexOrThrow(DATA)),
                                                                !cursor.isNull(cursor.getColumnIndexOrThrow(THUMBNAIL)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_TYPE)),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(TRANSFER_STATE)),
                                                                cursor.getLong(cursor.getColumnIndexOrThrow(SIZE)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(FILE_NAME)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_LOCATION)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_DISPOSITION)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(NAME)),
                                                                cursor.getBlob(cursor.getColumnIndexOrThrow(DIGEST)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(FAST_PREFLIGHT_ID)),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(VOICE_NOTE)) == 1,
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(WIDTH)),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(HEIGHT)),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(QUOTE)) == 1,
                                                                cursor.getString(cursor.getColumnIndexOrThrow(CAPTION)),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(STICKER_ID)) >= 0
                                                                    ? new StickerLocator(cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_ID)),
                                                                                         cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_KEY)),
                                                                                         cursor.getInt(cursor.getColumnIndexOrThrow(STICKER_ID)))
                                                                    : null,
                                                                BlurHash.parseOrNull(cursor.getString(cursor.getColumnIndexOrThrow(BLUR_HASH))),
                                                                TransformProperties.parse(cursor.getString(cursor.getColumnIndexOrThrow(TRANSFORM_PROPERTIES)))));
      }
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }


  private AttachmentId insertAttachment(long mmsId, Attachment attachment, boolean quote)
      throws MmsException
  {
    Log.d(TAG, "Inserting attachment for mms id: " + mmsId);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    DataInfo       dataInfo = null;
    long           uniqueId = System.currentTimeMillis();

    if (attachment.getDataUri() != null) {
      dataInfo = setAttachmentData(attachment.getDataUri(), false, null);
      Log.d(TAG, "Wrote part to file: " + dataInfo.file.getAbsolutePath());
    }

    Attachment template = attachment;

    if (dataInfo != null && dataInfo.hash != null) {
      Attachment possibleTemplate = findTemplateAttachment(dataInfo.hash);

      if (possibleTemplate != null) {
        Log.i(TAG, "Found a duplicate attachment upon insertion. Using it as a template.");
        template = possibleTemplate;
      }
    }

    ContentValues contentValues = new ContentValues();
    contentValues.put(MMS_ID, mmsId);
    contentValues.put(CONTENT_TYPE, template.getContentType());
    contentValues.put(TRANSFER_STATE, attachment.getTransferState());
    contentValues.put(UNIQUE_ID, uniqueId);
    contentValues.put(CONTENT_LOCATION, attachment.getLocation());
    contentValues.put(DIGEST, attachment.getDigest());
    contentValues.put(CONTENT_DISPOSITION, attachment.getKey());
    contentValues.put(NAME, attachment.getRelay());
    contentValues.put(FILE_NAME, StorageUtil.getCleanFileName(attachment.getFileName()));
    contentValues.put(SIZE, template.getSize());
    contentValues.put(FAST_PREFLIGHT_ID, attachment.getFastPreflightId());
    contentValues.put(VOICE_NOTE, attachment.isVoiceNote() ? 1 : 0);
    contentValues.put(WIDTH, template.getWidth());
    contentValues.put(HEIGHT, template.getHeight());
    contentValues.put(QUOTE, quote);
    contentValues.put(CAPTION, attachment.getCaption());
    contentValues.put(BLUR_HASH, getBlurHashStringOrNull(attachment.getBlurHash()));
    contentValues.put(TRANSFORM_PROPERTIES, template.getTransformProperties().serialize());

    if (attachment.isSticker()) {
      contentValues.put(STICKER_PACK_ID, attachment.getSticker().getPackId());
      contentValues.put(STICKER_PACK_KEY, attachment.getSticker().getPackKey());
      contentValues.put(STICKER_ID, attachment.getSticker().getStickerId());
    }

    if (dataInfo != null) {
      contentValues.put(DATA, dataInfo.file.getAbsolutePath());
      contentValues.put(SIZE, dataInfo.length);
      contentValues.put(DATA_RANDOM, dataInfo.random);
      contentValues.put(DATA_HASH, dataInfo.hash);
    }

    boolean      notifyPacks  = attachment.isSticker() && !hasStickerAttachments();
    long         rowId        = database.insert(TABLE_NAME, null, contentValues);
    AttachmentId attachmentId = new AttachmentId(rowId, uniqueId);
    Uri          thumbnailUri = attachment.getThumbnailUri();
    boolean      hasThumbnail = false;

    if (thumbnailUri != null) {
      try (InputStream attachmentStream = PartAuthority.getAttachmentStream(context, thumbnailUri)) {
        Pair<Integer, Integer> dimens = BitmapUtil.getDimensions(attachmentStream);
        updateAttachmentThumbnail(attachmentId,
                                  PartAuthority.getAttachmentStream(context, thumbnailUri),
                                  (float) dimens.first / (float) dimens.second);
        hasThumbnail = true;
      } catch (IOException | BitmapDecodingException e) {
        Log.w(TAG, "Failed to save existing thumbnail.", e);
      }
    }

    if (!hasThumbnail && dataInfo != null) {
      if (MediaUtil.hasVideoThumbnail(attachment.getDataUri())) {
        Bitmap bitmap = MediaUtil.getVideoThumbnail(context, attachment.getDataUri());

        if (bitmap != null) {
          try (ThumbnailData thumbnailData = new ThumbnailData(bitmap)) {
            updateAttachmentThumbnail(attachmentId, thumbnailData.toDataStream(), thumbnailData.getAspectRatio());
          }
        } else {
          Log.w(TAG, "Retrieving video thumbnail failed, submitting thumbnail generation job...");
          thumbnailExecutor.submit(new ThumbnailFetchCallable(attachmentId));
        }
      } else {
        Log.i(TAG, "Submitting thumbnail generation job...");
        thumbnailExecutor.submit(new ThumbnailFetchCallable(attachmentId));
      }
    }

    if (notifyPacks) {
      notifyStickerPackListeners();
    }

    return attachmentId;
  }

  private @Nullable DatabaseAttachment findTemplateAttachment(@NonNull String dataHash) {
    String   selection = DATA_HASH + " = ?";
    String[] args      = new String[] { dataHash };

    try (Cursor cursor = databaseHelper.getWritableDatabase().query(TABLE_NAME, null, selection, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return getAttachment(cursor).get(0);
      }
    }

    return null;
  }

  @SuppressWarnings("WeakerAccess")
  @VisibleForTesting
  protected void updateAttachmentThumbnail(AttachmentId attachmentId, InputStream in, float aspectRatio)
      throws MmsException
  {
    Log.i(TAG, "updating part thumbnail for #" + attachmentId);

    DataInfo thumbnailFile = setAttachmentData(in, true, attachmentId);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues(2);

    values.put(THUMBNAIL, thumbnailFile.file.getAbsolutePath());
    values.put(THUMBNAIL_ASPECT_RATIO, aspectRatio);
    values.put(THUMBNAIL_RANDOM, thumbnailFile.random);

    database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings());

    Cursor cursor = database.query(TABLE_NAME, new String[] {MMS_ID}, PART_ID_WHERE, attachmentId.toStrings(), null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst()) {
        notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(cursor.getLong(cursor.getColumnIndexOrThrow(MMS_ID))));
      }
    } finally {
      if (cursor != null) cursor.close();
    }
  }


  @VisibleForTesting
  class ThumbnailFetchCallable implements Callable<InputStream> {

    private final AttachmentId attachmentId;

    ThumbnailFetchCallable(AttachmentId attachmentId) {
      this.attachmentId = attachmentId;
    }

    @Override
    public @Nullable InputStream call() throws Exception {
      Log.d(TAG, "Executing thumbnail job...");
      final InputStream stream = getDataStream(attachmentId, THUMBNAIL, 0);

      if (stream != null) {
        return stream;
      }

      DatabaseAttachment attachment = getAttachment(attachmentId);

      if (attachment == null || !attachment.hasData()) {
        return null;
      }

      if (MediaUtil.isVideoType(attachment.getContentType())) {

        try (ThumbnailData data = generateVideoThumbnail(attachmentId)) {

          if (data != null) {
            updateAttachmentThumbnail(attachmentId, data.toDataStream(), data.getAspectRatio());

            return getDataStream(attachmentId, THUMBNAIL, 0);
          }
        }
      }

      return null;
    }

    private ThumbnailData generateVideoThumbnail(AttachmentId attachmentId) throws IOException {
      if (Build.VERSION.SDK_INT < 23) {
        Log.w(TAG, "Video thumbnails not supported...");
        return null;
      }

      try (MediaDataSource dataSource = mediaDataSourceFor(attachmentId)) {
        if (dataSource == null) return null;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        MediaMetadataRetrieverUtil.setDataSource(retriever, dataSource);

        Bitmap bitmap = retriever.getFrameAtTime(1000);

        Log.i(TAG, "Generated video thumbnail...");
        return bitmap != null ? new ThumbnailData(bitmap) : null;
      }
    }
  }

  @RequiresApi(23)
  public @Nullable MediaDataSource mediaDataSourceFor(@NonNull AttachmentId attachmentId) {
    DataInfo dataInfo = getAttachmentDataFileInfo(attachmentId, DATA);

    if (dataInfo == null) {
      Log.w(TAG, "No data file found for video attachment...");
      return null;
    }

    return EncryptedMediaDataSource.createFor(attachmentSecret, dataInfo.file, dataInfo.random, dataInfo.length);
  }

  private static class DataInfo {
    private final File   file;
    private final long   length;
    private final byte[] random;
    private final String hash;

    private DataInfo(File file, long length, byte[] random, String hash) {
      this.file   = file;
      this.length = length;
      this.random = random;
      this.hash   = hash;
    }
  }
  public static final class TransformProperties {

    @JsonProperty private final boolean skipTransform;

    public TransformProperties(@JsonProperty("skipTransform") boolean skipTransform) {
      this.skipTransform = skipTransform;
    }

    public static @NonNull TransformProperties empty() {
      return new TransformProperties(false);
    }

    public static @NonNull TransformProperties forSkipTransform() {
      return new TransformProperties(true);
    }

    public boolean shouldSkipTransform() {
      return skipTransform;
    }

    @NonNull String serialize() {
      return JsonUtil.toJson(this);
    }

    static @NonNull TransformProperties parse(@Nullable String serialized) {
      if (serialized == null) {
        return empty();
      }

      try {
        return JsonUtil.fromJson(serialized, TransformProperties.class);
      } catch (IOException e) {
        Log.w(TAG, "Failed to parse TransformProperties!", e);
        return empty();
      }
    }
  }
}
