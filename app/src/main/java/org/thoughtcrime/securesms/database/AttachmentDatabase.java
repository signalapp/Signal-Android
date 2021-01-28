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
import android.media.MediaDataSource;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.bumptech.glide.Glide;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.json.JSONArray;
import org.json.JSONException;
import org.signal.core.util.StreamUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.audio.AudioHash;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData;
import org.thoughtcrime.securesms.mms.MediaStream;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.FileUtils;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.video.EncryptedMediaDataSource;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
  private static final String TRANSFER_FILE          = "transfer_file";
  public  static final String SIZE                   = "data_size";
          static final String FILE_NAME              = "file_name";
  public  static final String UNIQUE_ID              = "unique_id";
          static final String DIGEST                 = "digest";
          static final String VOICE_NOTE             = "voice_note";
          static final String BORDERLESS             = "borderless";
          static final String QUOTE                  = "quote";
  public  static final String STICKER_PACK_ID        = "sticker_pack_id";
  public  static final String STICKER_PACK_KEY       = "sticker_pack_key";
          static final String STICKER_ID             = "sticker_id";
          static final String STICKER_EMOJI          = "sticker_emoji";
          static final String FAST_PREFLIGHT_ID      = "fast_preflight_id";
  public  static final String DATA_RANDOM            = "data_random";
          static final String WIDTH                  = "width";
          static final String HEIGHT                 = "height";
          static final String CAPTION                = "caption";
          static final String DATA_HASH              = "data_hash";
          static final String VISUAL_HASH            = "blur_hash";
          static final String TRANSFORM_PROPERTIES   = "transform_properties";
          static final String DISPLAY_ORDER          = "display_order";
          static final String UPLOAD_TIMESTAMP       = "upload_timestamp";
          static final String CDN_NUMBER             = "cdn_number";

  public  static final String DIRECTORY              = "parts";

  public static final int TRANSFER_PROGRESS_DONE    = 0;
  public static final int TRANSFER_PROGRESS_STARTED = 1;
  public static final int TRANSFER_PROGRESS_PENDING = 2;
  public static final int TRANSFER_PROGRESS_FAILED  = 3;

  public static final long PREUPLOAD_MESSAGE_ID = -8675309;

  private static final String PART_ID_WHERE     = ROW_ID + " = ? AND " + UNIQUE_ID + " = ?";
  private static final String PART_ID_WHERE_NOT = ROW_ID + " != ? AND " + UNIQUE_ID + " != ?";

  private static final String[] PROJECTION = new String[] {ROW_ID,
                                                           MMS_ID, CONTENT_TYPE, NAME, CONTENT_DISPOSITION,
                                                           CDN_NUMBER, CONTENT_LOCATION, DATA,
                                                           TRANSFER_STATE, SIZE, FILE_NAME, UNIQUE_ID, DIGEST,
                                                           FAST_PREFLIGHT_ID, VOICE_NOTE, BORDERLESS, QUOTE, DATA_RANDOM,
                                                           WIDTH, HEIGHT, CAPTION, STICKER_PACK_ID,
                                                           STICKER_PACK_KEY, STICKER_ID, STICKER_EMOJI, DATA_HASH, VISUAL_HASH,
                                                           TRANSFORM_PROPERTIES, TRANSFER_FILE, DISPLAY_ORDER,
                                                           UPLOAD_TIMESTAMP };

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ROW_ID                 + " INTEGER PRIMARY KEY, " +
                                                                                  MMS_ID                 + " INTEGER, " +
                                                                                  "seq"                  + " INTEGER DEFAULT 0, " +
                                                                                  CONTENT_TYPE           + " TEXT, " +
                                                                                  NAME                   + " TEXT, " +
                                                                                  "chset"                + " INTEGER, " +
                                                                                  CONTENT_DISPOSITION    + " TEXT, " +
                                                                                  "fn"                   + " TEXT, " +
                                                                                  "cid"                  + " TEXT, "  +
                                                                                  CONTENT_LOCATION       + " TEXT, " +
                                                                                  "ctt_s"                + " INTEGER, " +
                                                                                  "ctt_t"                + " TEXT, " +
                                                                                  "encrypted"            + " INTEGER, " +
                                                                                  TRANSFER_STATE         + " INTEGER, " +
                                                                                  DATA                   + " TEXT, " +
                                                                                  SIZE                   + " INTEGER, " +
                                                                                  FILE_NAME              + " TEXT, " +
                                                                                  UNIQUE_ID              + " INTEGER NOT NULL, " +
                                                                                  DIGEST                 + " BLOB, " +
                                                                                  FAST_PREFLIGHT_ID      + " TEXT, " +
                                                                                  VOICE_NOTE             + " INTEGER DEFAULT 0, " +
                                                                                  BORDERLESS             + " INTEGER DEFAULT 0, " +
                                                                                  DATA_RANDOM            + " BLOB, " +
                                                                                  QUOTE                  + " INTEGER DEFAULT 0, " +
                                                                                  WIDTH                  + " INTEGER DEFAULT 0, " +
                                                                                  HEIGHT                 + " INTEGER DEFAULT 0, " +
                                                                                  CAPTION                + " TEXT DEFAULT NULL, " +
                                                                                  STICKER_PACK_ID        + " TEXT DEFAULT NULL, " +
                                                                                  STICKER_PACK_KEY       + " DEFAULT NULL, " +
                                                                                  STICKER_ID             + " INTEGER DEFAULT -1, " +
                                                                                  STICKER_EMOJI          + " STRING DEFAULT NULL, " +
                                                                                  DATA_HASH              + " TEXT DEFAULT NULL, " +
                                                                                  VISUAL_HASH            + " TEXT DEFAULT NULL, " +
                                                                                  TRANSFORM_PROPERTIES   + " TEXT DEFAULT NULL, " +
                                                                                  TRANSFER_FILE          + " TEXT DEFAULT NULL, " +
                                                                                  DISPLAY_ORDER          + " INTEGER DEFAULT 0, " +
                                                                                  UPLOAD_TIMESTAMP       + " INTEGER DEFAULT 0, " +
                                                                                  CDN_NUMBER             + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS part_mms_id_index ON " + TABLE_NAME + " (" + MMS_ID + ");",
    "CREATE INDEX IF NOT EXISTS pending_push_index ON " + TABLE_NAME + " (" + TRANSFER_STATE + ");",
    "CREATE INDEX IF NOT EXISTS part_sticker_pack_id_index ON " + TABLE_NAME + " (" + STICKER_PACK_ID + ");",
    "CREATE INDEX IF NOT EXISTS part_data_hash_index ON " + TABLE_NAME + " (" + DATA_HASH + ");",
    "CREATE INDEX IF NOT EXISTS part_data_index ON " + TABLE_NAME + " (" + DATA + ");"
  };

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

  public boolean hasAttachment(@NonNull AttachmentId id) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    try (Cursor cursor = database.query(TABLE_NAME,
                                        new String[]{ROW_ID, UNIQUE_ID},
                                        PART_ID_WHERE,
                                        id.toStrings(),
                                        null,
                                        null,
                                        null)) {
      if (cursor != null && cursor.getCount() > 0) {
        return true;
      }
    }
    return false;
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
      cursor = database.query(TABLE_NAME, new String[] {DATA, CONTENT_TYPE, ROW_ID, UNIQUE_ID}, MMS_ID + " = ?",
                              new String[] {mmsId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        deleteAttachmentOnDisk(cursor.getString(cursor.getColumnIndex(DATA)),
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

  /**
   * Deletes all attachments with an ID of {@link #PREUPLOAD_MESSAGE_ID}. These represent
   * attachments that were pre-uploaded and haven't been assigned to a message. This should only be
   * done when you *know* that all attachments *should* be assigned a real mmsId. For instance, when
   * the app starts. Otherwise you could delete attachments that are legitimately being
   * pre-uploaded.
   */
  public int deleteAbandonedPreuploadedAttachments() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    String   query = MMS_ID + " = ?";
    String[] args  = new String[] { String.valueOf(PREUPLOAD_MESSAGE_ID) };
    int      count = 0;

    try (Cursor cursor = db.query(TABLE_NAME, null, query, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        long         rowId    = cursor.getLong(cursor.getColumnIndexOrThrow(ROW_ID));
        long         uniqueId = cursor.getLong(cursor.getColumnIndexOrThrow(UNIQUE_ID));
        AttachmentId id       = new AttachmentId(rowId, uniqueId);

        deleteAttachment(id);
        count++;
      }
    }

    return count;
  }

  public void deleteAttachmentFilesForViewOnceMessage(long mmsId) {
    Log.d(TAG, "[deleteAttachmentFilesForViewOnceMessage] mmsId: " + mmsId);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {DATA, CONTENT_TYPE, ROW_ID, UNIQUE_ID}, MMS_ID + " = ?",
          new String[] {mmsId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        deleteAttachmentOnDisk(cursor.getString(cursor.getColumnIndex(DATA)),
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
    values.put(FILE_NAME, (String) null);
    values.put(CAPTION, (String) null);
    values.put(SIZE, 0);
    values.put(WIDTH, 0);
    values.put(HEIGHT, 0);
    values.put(TRANSFER_STATE, TRANSFER_PROGRESS_DONE);
    values.put(VISUAL_HASH, (String) null);
    values.put(CONTENT_TYPE, MediaUtil.VIEW_ONCE);

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
                                        new String[]{DATA, CONTENT_TYPE},
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
      String contentType = cursor.getString(cursor.getColumnIndex(CONTENT_TYPE));

      database.delete(TABLE_NAME, PART_ID_WHERE, id.toStrings());
      deleteAttachmentOnDisk(data, contentType, id);
      notifyAttachmentListeners();
    }
  }

  public void trimAllAbandonedAttachments() {
    SQLiteDatabase db              = databaseHelper.getWritableDatabase();
    String         selectAllMmsIds = "SELECT " + MmsDatabase.ID + " FROM " + MmsDatabase.TABLE_NAME;
    String         selectDataInUse = "SELECT DISTINCT " + DATA + " FROM " + TABLE_NAME + " WHERE " + QUOTE + " = 0 AND (" + MMS_ID + " IN (" + selectAllMmsIds + ") OR " + MMS_ID + " = " + PREUPLOAD_MESSAGE_ID + ")";
    String         where           = MMS_ID + " NOT IN (" + selectAllMmsIds + ") AND " + DATA + " NOT IN (" + selectDataInUse + ")";

    db.delete(TABLE_NAME, where, null);
  }

  public void deleteAbandonedAttachmentFiles() {
    Set<String> filesOnDisk = new HashSet<>();
    Set<String> filesInDb   = new HashSet<>();

    File attachmentDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
    for (File file : attachmentDirectory.listFiles()) {
      filesOnDisk.add(file.getAbsolutePath());
    }

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(true, TABLE_NAME, new String[] { DATA }, null, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        filesInDb.add(CursorUtil.requireString(cursor, DATA));
      }
    }

    filesInDb.addAll(DatabaseFactory.getStickerDatabase(context).getAllStickerFiles());

    Set<String> onDiskButNotInDatabase = SetUtil.difference(filesOnDisk, filesInDb);

    for (String filePath : onDiskButNotInDatabase) {
      //noinspection ResultOfMethodCallIgnored
      new File(filePath).delete();
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  void deleteAllAttachments() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);

    FileUtils.deleteDirectoryContents(context.getDir(DIRECTORY, Context.MODE_PRIVATE));

    notifyAttachmentListeners();
  }

  private void deleteAttachmentOnDisk(@Nullable String data,
                                      @Nullable String contentType,
                                      @NonNull AttachmentId attachmentId)
  {
    DataUsageResult dataUsage = getAttachmentFileUsages(data, attachmentId);

    if (dataUsage.hasStrongReference()) {
      Log.i(TAG, "[deleteAttachmentOnDisk] Attachment in use. Skipping deletion. " + data + " " + attachmentId);
      return;
    }

    Log.i(TAG, "[deleteAttachmentOnDisk] No other strong uses of this attachment. Safe to delete. " + data + " " + attachmentId);

    if (!TextUtils.isEmpty(data)) {
      if (new File(data).delete()) {
        Log.i(TAG, "[deleteAttachmentOnDisk] Deleted attachment file. " + data + " " + attachmentId);

        List<AttachmentId> removableWeakReferences = dataUsage.getRemovableWeakReferences();

        if (removableWeakReferences.size() > 0) {
          Log.i(TAG, String.format(Locale.US, "[deleteAttachmentOnDisk] Deleting %d weak references for %s", removableWeakReferences.size(), data));
          SQLiteDatabase database     = databaseHelper.getWritableDatabase();
          int            deletedCount = 0;
          database.beginTransaction();
          try {
            for (AttachmentId weakReference : removableWeakReferences) {
              Log.i(TAG, String.format("[deleteAttachmentOnDisk] Clearing weak reference for %s %s", data, weakReference));
              ContentValues values = new ContentValues();
              values.putNull(DATA);
              values.putNull(DATA_RANDOM);
              values.putNull(DATA_HASH);
              deletedCount += database.update(TABLE_NAME, values, PART_ID_WHERE, weakReference.toStrings());
            }
            database.setTransactionSuccessful();
          } finally {
            database.endTransaction();
          }
          String logMessage = String.format(Locale.US, "[deleteAttachmentOnDisk] Cleared %d/%d weak references for %s", deletedCount, removableWeakReferences.size(), data);
          if (deletedCount != removableWeakReferences.size()) {
            Log.w(TAG, logMessage);
          } else {
            Log.i(TAG, logMessage);
          }
        }
      } else {
        Log.w(TAG, "[deleteAttachmentOnDisk] Failed to delete attachment. " + data + " " + attachmentId);
      }
    }

    if (MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType)) {
      Glide.get(context).clearDiskCache();
    }
  }

  private @NonNull DataUsageResult getAttachmentFileUsages(@Nullable String data, @NonNull AttachmentId attachmentId) {
    if (data == null) return DataUsageResult.NOT_IN_USE;

    SQLiteDatabase     database  = databaseHelper.getReadableDatabase();
    String             selection = DATA + " = ? AND " + UNIQUE_ID + " != ? AND " + ROW_ID + " != ?";
    String[]           args      = {data, Long.toString(attachmentId.getUniqueId()), Long.toString(attachmentId.getRowId())};
    List<AttachmentId> quoteRows = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, new String[]{ROW_ID, UNIQUE_ID, QUOTE}, selection, args, null, null, null, null)) {
      while (cursor.moveToNext()) {
        boolean isQuote = cursor.getInt(cursor.getColumnIndexOrThrow(QUOTE)) == 1;
        if (isQuote) {
          quoteRows.add(new AttachmentId(cursor.getLong(cursor.getColumnIndexOrThrow(ROW_ID)), cursor.getLong(cursor.getColumnIndexOrThrow(UNIQUE_ID))));
        } else {
          return DataUsageResult.IN_USE;
        }
      }
    }

    return new DataUsageResult(quoteRows);
  }

  public void insertAttachmentsForPlaceholder(long mmsId, @NonNull AttachmentId attachmentId, @NonNull InputStream inputStream)
      throws MmsException
  {
    DatabaseAttachment placeholder  = getAttachment(attachmentId);
    SQLiteDatabase     database     = databaseHelper.getWritableDatabase();
    ContentValues      values       = new ContentValues();
    DataInfo           oldInfo      = getAttachmentDataFileInfo(attachmentId, DATA);
    DataInfo           dataInfo     = setAttachmentData(inputStream, attachmentId);
    File               transferFile = getTransferFile(databaseHelper.getReadableDatabase(), attachmentId);

    if (oldInfo != null) {
      updateAttachmentDataHash(database, oldInfo.hash, dataInfo);
    }

    values.put(DATA, dataInfo.file.getAbsolutePath());
    values.put(SIZE, dataInfo.length);
    values.put(DATA_RANDOM, dataInfo.random);
    values.put(DATA_HASH, dataInfo.hash);

    String visualHashString = getVisualHashStringOrNull(placeholder);
    if (visualHashString != null) {
      values.put(VISUAL_HASH, visualHashString);
    }

    values.put(TRANSFER_STATE, TRANSFER_PROGRESS_DONE);
    values.put(TRANSFER_FILE, (String)null);

    values.put(TRANSFORM_PROPERTIES, TransformProperties.forSkipTransform().serialize());

    if (database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings()) == 0) {
      //noinspection ResultOfMethodCallIgnored
      dataInfo.file.delete();
    } else {
      notifyConversationListeners(DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(mmsId));
      notifyConversationListListeners();
    }

    if (transferFile != null) {
      //noinspection ResultOfMethodCallIgnored
      transferFile.delete();
    }
  }

  private static @Nullable String getVisualHashStringOrNull(@Nullable Attachment attachment) {
         if (attachment == null)                return null;
    else if (attachment.getBlurHash()  != null) return attachment.getBlurHash().getHash();
    else if (attachment.getAudioHash() != null) return attachment.getAudioHash().getHash();
    else                                        return null;
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
    contentValues.put(CDN_NUMBER, sourceAttachment.getCdnNumber());
    contentValues.put(CONTENT_LOCATION, sourceAttachment.getLocation());
    contentValues.put(DIGEST, sourceAttachment.getDigest());
    contentValues.put(CONTENT_DISPOSITION, sourceAttachment.getKey());
    contentValues.put(NAME, sourceAttachment.getRelay());
    contentValues.put(SIZE, sourceAttachment.getSize());
    contentValues.put(FAST_PREFLIGHT_ID, sourceAttachment.getFastPreflightId());
    contentValues.put(WIDTH, sourceAttachment.getWidth());
    contentValues.put(HEIGHT, sourceAttachment.getHeight());
    contentValues.put(CONTENT_TYPE, sourceAttachment.getContentType());
    contentValues.put(VISUAL_HASH, getVisualHashStringOrNull(sourceAttachment));

    database.update(TABLE_NAME, contentValues, PART_ID_WHERE, destinationId.toStrings());
  }

  public void updateAttachmentCaption(@NonNull AttachmentId id, @Nullable String caption) {
    ContentValues values = new ContentValues(1);
    values.put(CAPTION, caption);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, values, PART_ID_WHERE, id.toStrings());
  }

  public void updateDisplayOrder(@NonNull Map<AttachmentId, Integer> orderMap) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      for (Map.Entry<AttachmentId, Integer> entry : orderMap.entrySet()) {
        ContentValues values = new ContentValues(1);
        values.put(DISPLAY_ORDER, entry.getValue());

        databaseHelper.getWritableDatabase().update(TABLE_NAME, values, PART_ID_WHERE, entry.getKey().toStrings());
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

  }

  public void updateAttachmentAfterUpload(@NonNull AttachmentId id, @NonNull Attachment attachment, long uploadTimestamp) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    DataInfo       dataInfo = getAttachmentDataFileInfo(id, DATA);
    ContentValues  values   = new ContentValues();

    values.put(TRANSFER_STATE, TRANSFER_PROGRESS_DONE);
    values.put(CDN_NUMBER, attachment.getCdnNumber());
    values.put(CONTENT_LOCATION, attachment.getLocation());
    values.put(DIGEST, attachment.getDigest());
    values.put(CONTENT_DISPOSITION, attachment.getKey());
    values.put(NAME, attachment.getRelay());
    values.put(SIZE, attachment.getSize());
    values.put(FAST_PREFLIGHT_ID, attachment.getFastPreflightId());
    values.put(VISUAL_HASH, getVisualHashStringOrNull(attachment));
    values.put(UPLOAD_TIMESTAMP, uploadTimestamp);

    if (dataInfo != null && dataInfo.hash != null) {
      updateAttachmentAndMatchingHashes(database, id, dataInfo.hash, values);
    } else {
      database.update(TABLE_NAME, values, PART_ID_WHERE, id.toStrings());
    }
  }

  public @NonNull DatabaseAttachment insertAttachmentForPreUpload(@NonNull Attachment attachment) throws MmsException {
    Map<Attachment, AttachmentId> result = insertAttachmentsForMessage(PREUPLOAD_MESSAGE_ID,
                                                                       Collections.singletonList(attachment),
                                                                       Collections.emptyList());

    if (result.values().isEmpty()) {
      throw new MmsException("Bad attachment result!");
    }

    DatabaseAttachment databaseAttachment = getAttachment(result.values().iterator().next());

    if (databaseAttachment == null) {
      throw new MmsException("Failed to retrieve attachment we just inserted!");
    }

    return databaseAttachment;
  }

  public void updateMessageId(@NonNull Collection<AttachmentId> attachmentIds, long mmsId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      ContentValues values = new ContentValues(1);
      values.put(MMS_ID, mmsId);

      for (AttachmentId attachmentId : attachmentIds) {
        db.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings());
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
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

  /**
   * @param onlyModifyThisAttachment If false and more than one attachment shares this file, they will all be updated.
   *                                 If true, then guarantees not to affect other attachments.
   */
  public void updateAttachmentData(@NonNull DatabaseAttachment databaseAttachment,
                                   @NonNull MediaStream mediaStream,
                                   boolean onlyModifyThisAttachment)
    throws MmsException, IOException
  {
    SQLiteDatabase database    = databaseHelper.getWritableDatabase();
    DataInfo       oldDataInfo = getAttachmentDataFileInfo(databaseAttachment.getAttachmentId(), DATA);

    if (oldDataInfo == null) {
      throw new MmsException("No attachment data found!");
    }

    File destination = oldDataInfo.file;

    if (onlyModifyThisAttachment) {
      if (fileReferencedByMoreThanOneAttachment(destination)) {
        Log.i(TAG, "Creating a new file as this one is used by more than one attachment");
        destination = newFile();
      }
    }

    DataInfo dataInfo = setAttachmentData(destination,
                                          mediaStream.getStream(),
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

  /**
   * Returns true if the file referenced by two or more attachments.
   * Returns false if the file is referenced by zero or one attachments.
   */
  private boolean fileReferencedByMoreThanOneAttachment(@NonNull File file) {
    SQLiteDatabase database  = databaseHelper.getReadableDatabase();
    String         selection = DATA + " = ?";
    String[]       args      = new String[]{file.getAbsolutePath()};

    try (Cursor cursor = database.query(TABLE_NAME, null, selection, args, null, null, null, "2")) {
      return cursor != null && cursor.moveToFirst() && cursor.moveToNext();
    }
  }

  public void markAttachmentAsTransformed(@NonNull AttachmentId attachmentId) {
    updateAttachmentTransformProperties(attachmentId, TransformProperties.forSkipTransform());
  }

  public void updateAttachmentTransformProperties(@NonNull AttachmentId attachmentId, @NonNull TransformProperties transformProperties) {
    DataInfo dataInfo = getAttachmentDataFileInfo(attachmentId, DATA);

    if (dataInfo == null) {
      Log.w(TAG, "[updateAttachmentTransformProperties] No data info found!");
      return;
    }

    ContentValues contentValues = new ContentValues();
    contentValues.put(TRANSFORM_PROPERTIES, transformProperties.serialize());

    int updateCount = updateAttachmentAndMatchingHashes(databaseHelper.getWritableDatabase(), attachmentId, dataInfo.hash, contentValues);
    Log.i(TAG, "[updateAttachmentTransformProperties] Updated " + updateCount + " rows.");
  }

  public @NonNull File getOrCreateTransferFile(@NonNull AttachmentId attachmentId) throws IOException {
    SQLiteDatabase db       = databaseHelper.getWritableDatabase();
    File           existing = getTransferFile(db, attachmentId);

    if (existing != null) {
      return existing;
    }

    File partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
    File transferFile   = File.createTempFile("transfer", ".mms", partsDirectory);

    ContentValues values = new ContentValues();
    values.put(TRANSFER_FILE, transferFile.getAbsolutePath());

    db.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings());

    return transferFile;
  }

  private @Nullable static File getTransferFile(@NonNull SQLiteDatabase db, @NonNull AttachmentId attachmentId) {
    try (Cursor cursor = db.query(TABLE_NAME, new String[] { TRANSFER_FILE }, PART_ID_WHERE, attachmentId.toStrings(), null, null, "1")) {
      if (cursor != null && cursor.moveToFirst()) {
        String path = cursor.getString(cursor.getColumnIndexOrThrow(TRANSFER_FILE));
        if (path != null) {
          return new File(path);
        }
      }
    }

    return null;
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

    try {
      cursor = database.query(TABLE_NAME, new String[]{dataType, SIZE, DATA_RANDOM, DATA_HASH}, PART_ID_WHERE, attachmentId.toStrings(),
                              null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        if (cursor.isNull(cursor.getColumnIndexOrThrow(dataType))) {
          return null;
        }

        return new DataInfo(new File(cursor.getString(cursor.getColumnIndexOrThrow(dataType))),
                            cursor.getLong(cursor.getColumnIndexOrThrow(SIZE)),
                            cursor.getBlob(cursor.getColumnIndexOrThrow(DATA_RANDOM)),
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
                                              @Nullable AttachmentId attachmentId)
      throws MmsException
  {
    try {
      InputStream inputStream = PartAuthority.getAttachmentStream(context, uri);
      return setAttachmentData(inputStream, attachmentId);
    } catch (IOException e) {
      throw new MmsException(e);
    }
  }

  private @NonNull DataInfo setAttachmentData(@NonNull InputStream in,
                                              @Nullable AttachmentId attachmentId)
      throws MmsException
  {
    try {
      File dataFile = newFile();
      return setAttachmentData(dataFile, in, attachmentId);
    } catch (IOException e) {
      throw new MmsException(e);
    }
  }

  public File newFile() throws IOException {
    File partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
    return File.createTempFile("part", ".mms", partsDirectory);
  }

  private @NonNull DataInfo setAttachmentData(@NonNull File destination,
                                              @NonNull InputStream in,
                                              @Nullable AttachmentId attachmentId)
      throws MmsException
  {
    try {
      MessageDigest              messageDigest     = MessageDigest.getInstance("SHA-256");
      DigestInputStream          digestInputStream = new DigestInputStream(in, messageDigest);
      Pair<byte[], OutputStream> out               = ModernEncryptingPartOutputStream.createFor(attachmentSecret, destination, false);
      long                       length            = StreamUtil.copy(digestInputStream, out.second);
      String                     hash              = Base64.encodeBytes(digestInputStream.getMessageDigest().digest());

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
            String contentType = object.getString(CONTENT_TYPE);
            result.add(new DatabaseAttachment(new AttachmentId(object.getLong(ROW_ID), object.getLong(UNIQUE_ID)),
                                              object.getLong(MMS_ID),
                                              !TextUtils.isEmpty(object.getString(DATA)),
                                              MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType),
                                              contentType,
                                              object.getInt(TRANSFER_STATE),
                                              object.getLong(SIZE),
                                              object.getString(FILE_NAME),
                                              object.getInt(CDN_NUMBER),
                                              object.getString(CONTENT_LOCATION),
                                              object.getString(CONTENT_DISPOSITION),
                                              object.getString(NAME),
                                              null,
                                              object.getString(FAST_PREFLIGHT_ID),
                                              object.getInt(VOICE_NOTE) == 1,
                                              object.getInt(BORDERLESS) == 1,
                                              object.getInt(WIDTH),
                                              object.getInt(HEIGHT),
                                              object.getInt(QUOTE) == 1,
                                              object.getString(CAPTION),
                                              object.getInt(STICKER_ID) >= 0
                                                  ? new StickerLocator(object.getString(STICKER_PACK_ID),
                                                                       object.getString(STICKER_PACK_KEY),
                                                                       object.getInt(STICKER_ID),
                                                                       object.getString(STICKER_EMOJI))
                                                  : null,
                                              MediaUtil.isAudioType(contentType) ? null : BlurHash.parseOrNull(object.getString(VISUAL_HASH)),
                                              MediaUtil.isAudioType(contentType) ? AudioHash.parseOrNull(object.getString(VISUAL_HASH)) : null,
                                              TransformProperties.parse(object.getString(TRANSFORM_PROPERTIES)),
                                              object.getInt(DISPLAY_ORDER),
                                              object.getLong(UPLOAD_TIMESTAMP)));
          }
        }

        return result;
      } else {
        String contentType = cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_TYPE));
        return Collections.singletonList(new DatabaseAttachment(new AttachmentId(cursor.getLong(cursor.getColumnIndexOrThrow(ROW_ID)),
                                                                                 cursor.getLong(cursor.getColumnIndexOrThrow(UNIQUE_ID))),
                                                                cursor.getLong(cursor.getColumnIndexOrThrow(MMS_ID)),
                                                                !cursor.isNull(cursor.getColumnIndexOrThrow(DATA)),
                                                                MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType),
                                                                contentType,
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(TRANSFER_STATE)),
                                                                cursor.getLong(cursor.getColumnIndexOrThrow(SIZE)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(FILE_NAME)),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(CDN_NUMBER)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_LOCATION)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_DISPOSITION)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(NAME)),
                                                                cursor.getBlob(cursor.getColumnIndexOrThrow(DIGEST)),
                                                                cursor.getString(cursor.getColumnIndexOrThrow(FAST_PREFLIGHT_ID)),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(VOICE_NOTE)) == 1,
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(BORDERLESS)) == 1,
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(WIDTH)),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(HEIGHT)),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(QUOTE)) == 1,
                                                                cursor.getString(cursor.getColumnIndexOrThrow(CAPTION)),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(STICKER_ID)) >= 0
                                                                    ? new StickerLocator(CursorUtil.requireString(cursor, STICKER_PACK_ID),
                                                                                         CursorUtil.requireString(cursor, STICKER_PACK_KEY),
                                                                                         CursorUtil.requireInt(cursor, STICKER_ID),
                                                                                         CursorUtil.requireString(cursor, STICKER_EMOJI))
                                                                    : null,
                                                                MediaUtil.isAudioType(contentType) ? null : BlurHash.parseOrNull(cursor.getString(cursor.getColumnIndexOrThrow(VISUAL_HASH))),
                                                                MediaUtil.isAudioType(contentType) ? AudioHash.parseOrNull(cursor.getString(cursor.getColumnIndexOrThrow(VISUAL_HASH))) : null,
                                                                TransformProperties.parse(cursor.getString(cursor.getColumnIndexOrThrow(TRANSFORM_PROPERTIES))),
                                                                cursor.getInt(cursor.getColumnIndexOrThrow(DISPLAY_ORDER)),
                                                                cursor.getLong(cursor.getColumnIndexOrThrow(UPLOAD_TIMESTAMP))));
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

    database.beginTransaction();
    try {
      DataInfo       dataInfo        = null;
      long           uniqueId        = System.currentTimeMillis();

      if (attachment.getUri() != null) {
        dataInfo = setAttachmentData(attachment.getUri(), null);
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

      boolean useTemplateUpload = template.getUploadTimestamp() > attachment.getUploadTimestamp() &&
                                  template.getTransferState() == TRANSFER_PROGRESS_DONE           &&
                                  template.getTransformProperties().shouldSkipTransform()         &&
                                  !attachment.getTransformProperties().isVideoEdited();

      ContentValues contentValues = new ContentValues();
      contentValues.put(MMS_ID, mmsId);
      contentValues.put(CONTENT_TYPE, template.getContentType());
      contentValues.put(TRANSFER_STATE, attachment.getTransferState());
      contentValues.put(UNIQUE_ID, uniqueId);
      contentValues.put(CDN_NUMBER, useTemplateUpload ? template.getCdnNumber() : attachment.getCdnNumber());
      contentValues.put(CONTENT_LOCATION, useTemplateUpload ? template.getLocation() : attachment.getLocation());
      contentValues.put(DIGEST, useTemplateUpload ? template.getDigest() : attachment.getDigest());
      contentValues.put(CONTENT_DISPOSITION, useTemplateUpload ? template.getKey() : attachment.getKey());
      contentValues.put(NAME, useTemplateUpload ? template.getRelay() : attachment.getRelay());
      contentValues.put(FILE_NAME, StorageUtil.getCleanFileName(attachment.getFileName()));
      contentValues.put(SIZE, template.getSize());
      contentValues.put(FAST_PREFLIGHT_ID, attachment.getFastPreflightId());
      contentValues.put(VOICE_NOTE, attachment.isVoiceNote() ? 1 : 0);
      contentValues.put(BORDERLESS, attachment.isBorderless() ? 1 : 0);
      contentValues.put(WIDTH, template.getWidth());
      contentValues.put(HEIGHT, template.getHeight());
      contentValues.put(QUOTE, quote);
      contentValues.put(CAPTION, attachment.getCaption());
      contentValues.put(UPLOAD_TIMESTAMP, useTemplateUpload ? template.getUploadTimestamp() : attachment.getUploadTimestamp());
      if (attachment.getTransformProperties().isVideoEdited()) {
        contentValues.putNull(VISUAL_HASH);
        contentValues.put(TRANSFORM_PROPERTIES, attachment.getTransformProperties().serialize());
      } else {
        contentValues.put(VISUAL_HASH, getVisualHashStringOrNull(template));
        contentValues.put(TRANSFORM_PROPERTIES, template.getTransformProperties().serialize());
      }

      if (attachment.isSticker()) {
        contentValues.put(STICKER_PACK_ID, attachment.getSticker().getPackId());
        contentValues.put(STICKER_PACK_KEY, attachment.getSticker().getPackKey());
        contentValues.put(STICKER_ID, attachment.getSticker().getStickerId());
        contentValues.put(STICKER_EMOJI, attachment.getSticker().getEmoji());
      }

      if (dataInfo != null) {
        contentValues.put(DATA, dataInfo.file.getAbsolutePath());
        contentValues.put(SIZE, dataInfo.length);
        contentValues.put(DATA_RANDOM, dataInfo.random);
        if (attachment.getTransformProperties().isVideoEdited()) {
          contentValues.putNull(DATA_HASH);
        } else {
          contentValues.put(DATA_HASH, dataInfo.hash);
        }
      }

      boolean      notifyPacks  = attachment.isSticker() && !hasStickerAttachments();
      long         rowId        = database.insert(TABLE_NAME, null, contentValues);
      AttachmentId attachmentId = new AttachmentId(rowId, uniqueId);

      if (notifyPacks) {
        notifyStickerPackListeners();
      }

      database.setTransactionSuccessful();

      return attachmentId;
    } finally {
      database.endTransaction();
    }
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

  @WorkerThread
  public void writeAudioHash(@NonNull AttachmentId attachmentId, @Nullable AudioWaveFormData audioWaveForm) {
    Log.i(TAG, "updating part audio wave form for #" + attachmentId);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues(1);

    if (audioWaveForm != null) {
      values.put(VISUAL_HASH, new AudioHash(audioWaveForm).getHash());
    } else {
      values.putNull(VISUAL_HASH);
    }

    database.update(TABLE_NAME, values, PART_ID_WHERE, attachmentId.toStrings());
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

  private static final class DataUsageResult {
    private final boolean            hasStrongReference;
    private final List<AttachmentId> removableWeakReferences;

    private static final DataUsageResult IN_USE     = new DataUsageResult(true, Collections.emptyList());
    private static final DataUsageResult NOT_IN_USE = new DataUsageResult(false, Collections.emptyList());

    DataUsageResult(@NonNull List<AttachmentId> removableWeakReferences) {
      this(false, removableWeakReferences);
    }

    private DataUsageResult(boolean hasStrongReference, @NonNull List<AttachmentId> removableWeakReferences) {
      if (hasStrongReference && removableWeakReferences.size() > 0) {
        throw new AssertionError();
      }
      this.hasStrongReference      = hasStrongReference;
      this.removableWeakReferences = removableWeakReferences;
    }

    boolean hasStrongReference() {
      return hasStrongReference;
    }

    /**
     * Entries in here can be removed from the database.
     * <p>
     * Only possible to be non-empty when {@link #hasStrongReference} is false.
     */
    @NonNull List<AttachmentId> getRemovableWeakReferences() {
      return removableWeakReferences;
    }
  }

  public static final class TransformProperties {

    @JsonProperty private final boolean skipTransform;
    @JsonProperty private final boolean videoTrim;
    @JsonProperty private final long    videoTrimStartTimeUs;
    @JsonProperty private final long    videoTrimEndTimeUs;

    @JsonCreator
    public TransformProperties(@JsonProperty("skipTransform") boolean skipTransform,
                               @JsonProperty("videoTrim") boolean videoTrim,
                               @JsonProperty("videoTrimStartTimeUs") long videoTrimStartTimeUs,
                               @JsonProperty("videoTrimEndTimeUs") long videoTrimEndTimeUs)
    {
      this.skipTransform        = skipTransform;
      this.videoTrim            = videoTrim;
      this.videoTrimStartTimeUs = videoTrimStartTimeUs;
      this.videoTrimEndTimeUs   = videoTrimEndTimeUs;
    }

    public static @NonNull TransformProperties empty() {
      return new TransformProperties(false, false, 0, 0);
    }

    public static @NonNull TransformProperties forSkipTransform() {
      return new TransformProperties(true, false, 0, 0);
    }

    public static @NonNull TransformProperties forVideoTrim(long videoTrimStartTimeUs, long videoTrimEndTimeUs) {
      return new TransformProperties(false, true, videoTrimStartTimeUs, videoTrimEndTimeUs);
    }

    public boolean shouldSkipTransform() {
      return skipTransform;
    }

    public boolean isVideoEdited() {
      return isVideoTrim();
    }

    public boolean isVideoTrim() {
      return videoTrim;
    }

    public long getVideoTrimStartTimeUs() {
      return videoTrimStartTimeUs;
    }

    public long getVideoTrimEndTimeUs() {
      return videoTrimEndTimeUs;
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
