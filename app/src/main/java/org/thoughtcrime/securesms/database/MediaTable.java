package org.thoughtcrime.securesms.database;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.util.List;

@SuppressLint({"RecipientIdDatabaseReferenceUsage", "ThreadIdDatabaseReferenceUsage"}) // Not a real table, just a view
public class MediaTable extends DatabaseTable {

  public  static final int    ALL_THREADS         = -1;
  private static final String THREAD_RECIPIENT_ID = "THREAD_RECIPIENT_ID";

    private static final String BASE_MEDIA_QUERY = "SELECT " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.ROW_ID + " AS " + AttachmentTable.ROW_ID + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_TYPE + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.UNIQUE_ID + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.TRANSFER_STATE + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.SIZE + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.FILE_NAME + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DATA + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CDN_NUMBER + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_LOCATION + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_DISPOSITION + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DIGEST + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.FAST_PREFLIGHT_ID + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VOICE_NOTE + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.BORDERLESS + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VIDEO_GIF + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.WIDTH + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.HEIGHT + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.QUOTE + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_PACK_ID + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_PACK_KEY + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_ID + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_EMOJI + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VISUAL_HASH + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.TRANSFORM_PROPERTIES + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DISPLAY_ORDER + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CAPTION + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.NAME + ", "
                                                   + AttachmentTable.TABLE_NAME + "." + AttachmentTable.UPLOAD_TIMESTAMP + ", "
                                                   + MmsTable.TABLE_NAME + "." + MmsTable.TYPE + ", "
                                                   + MmsTable.TABLE_NAME + "." + MmsTable.DATE_SENT + ", "
                                                   + MmsTable.TABLE_NAME + "." + MmsTable.DATE_RECEIVED + ", "
                                                   + MmsTable.TABLE_NAME + "." + MmsTable.DATE_SERVER + ", "
                                                   + MmsTable.TABLE_NAME + "." + MmsTable.THREAD_ID + ", "
                                                   + MmsTable.TABLE_NAME + "." + MmsTable.RECIPIENT_ID + ", "
                                                   + ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID + " as " + THREAD_RECIPIENT_ID + " "
                                                   + "FROM " + AttachmentTable.TABLE_NAME + " LEFT JOIN " + MmsTable.TABLE_NAME
                                                   + " ON " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + " = " + MmsTable.TABLE_NAME + "." + MmsTable.ID + " "
                                                   + "LEFT JOIN " + ThreadTable.TABLE_NAME
                                                   + " ON " + ThreadTable.TABLE_NAME + "." + ThreadTable.ID + " = " + MmsTable.TABLE_NAME + "." + MmsTable.THREAD_ID + " "
                                                   + "WHERE " + AttachmentTable.MMS_ID + " IN (SELECT " + MmsSmsColumns.ID
                                                   + " FROM " + MmsTable.TABLE_NAME
                                                   + " WHERE " + MmsTable.THREAD_ID + " __EQUALITY__ ?) AND (%s) AND "
                                                   + MmsTable.VIEW_ONCE + " = 0 AND "
                                                   + MmsTable.STORY_TYPE + " = 0 AND "
                                                   + AttachmentTable.DATA + " IS NOT NULL AND "
                                                   + "(" + AttachmentTable.QUOTE + " = 0 OR (" + AttachmentTable.QUOTE + " = 1 AND " + AttachmentTable.DATA_HASH + " IS NULL)) AND "
                                                   + AttachmentTable.STICKER_PACK_ID + " IS NULL AND "
                                                   + MmsTable.TABLE_NAME + "." + MmsTable.RECIPIENT_ID + " > 0 AND "
                                                   + THREAD_RECIPIENT_ID + " > 0";

   private static final String UNIQUE_MEDIA_QUERY = "SELECT "
                                                    + "MAX(" + AttachmentTable.SIZE + ") as " + AttachmentTable.SIZE + ", "
                                                    + AttachmentTable.CONTENT_TYPE + " "
                                                    + "FROM " + AttachmentTable.TABLE_NAME + " "
                                                    + "WHERE " + AttachmentTable.STICKER_PACK_ID + " IS NULL AND " + AttachmentTable.TRANSFER_STATE + " = " + AttachmentTable.TRANSFER_PROGRESS_DONE + " "
                                                    + "GROUP BY " + AttachmentTable.DATA;

  private static final String GALLERY_MEDIA_QUERY  = String.format(BASE_MEDIA_QUERY, AttachmentTable.CONTENT_TYPE + " NOT LIKE 'image/svg%' AND (" +
                                                                                     AttachmentTable.CONTENT_TYPE + " LIKE 'image/%' OR " +
                                                                                     AttachmentTable.CONTENT_TYPE + " LIKE 'video/%')");
  private static final String AUDIO_MEDIA_QUERY    = String.format(BASE_MEDIA_QUERY, AttachmentTable.CONTENT_TYPE + " LIKE 'audio/%'");
  private static final String ALL_MEDIA_QUERY      = String.format(BASE_MEDIA_QUERY, AttachmentTable.CONTENT_TYPE + " NOT LIKE 'text/x-signal-plain'");
  private static final String DOCUMENT_MEDIA_QUERY = String.format(BASE_MEDIA_QUERY, AttachmentTable.CONTENT_TYPE + " LIKE 'image/svg%' OR (" +
                                                                                     AttachmentTable.CONTENT_TYPE + " NOT LIKE 'image/%' AND " +
                                                                                     AttachmentTable.CONTENT_TYPE + " NOT LIKE 'video/%' AND " +
                                                                                     AttachmentTable.CONTENT_TYPE + " NOT LIKE 'audio/%' AND " +
                                                                                     AttachmentTable.CONTENT_TYPE + " NOT LIKE 'text/x-signal-plain')");

  MediaTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public @NonNull Cursor getGalleryMediaForThread(long threadId, @NonNull Sorting sorting) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String         query    = sorting.applyToQuery(applyEqualityOperator(threadId, GALLERY_MEDIA_QUERY));
    String[]       args     = {threadId + ""};

    return database.rawQuery(query, args);
  }

  public @NonNull Cursor getDocumentMediaForThread(long threadId, @NonNull Sorting sorting) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String         query    = sorting.applyToQuery(applyEqualityOperator(threadId, DOCUMENT_MEDIA_QUERY));
    String[]       args     = {threadId + ""};

    return database.rawQuery(query, args);
  }

  public @NonNull Cursor getAudioMediaForThread(long threadId, @NonNull Sorting sorting) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String         query    = sorting.applyToQuery(applyEqualityOperator(threadId, AUDIO_MEDIA_QUERY));
    String[]       args     = {threadId + ""};

    return database.rawQuery(query, args);
  }

  public @NonNull Cursor getAllMediaForThread(long threadId, @NonNull Sorting sorting) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String         query    = sorting.applyToQuery(applyEqualityOperator(threadId, ALL_MEDIA_QUERY));
    String[]       args     = {threadId + ""};

    return database.rawQuery(query, args);
  }

  private static String applyEqualityOperator(long threadId, String query) {
    return query.replace("__EQUALITY__", threadId == ALL_THREADS ? "!=" : "=");
  }

  public StorageBreakdown getStorageBreakdown() {
    StorageBreakdown storageBreakdown = new StorageBreakdown();
    SQLiteDatabase   database         = databaseHelper.getSignalReadableDatabase();

    try (Cursor cursor = database.rawQuery(UNIQUE_MEDIA_QUERY, new String[0])) {
      int sizeColumn        = cursor.getColumnIndexOrThrow(AttachmentTable.SIZE);
      int contentTypeColumn = cursor.getColumnIndexOrThrow(AttachmentTable.CONTENT_TYPE);

      while (cursor.moveToNext()) {
        int    size = cursor.getInt(sizeColumn);
        String type = cursor.getString(contentTypeColumn);

        switch (MediaUtil.getSlideTypeFromContentType(type)) {
          case GIF:
          case IMAGE:
          case MMS:
            storageBreakdown.photoSize += size;
            break;
          case VIDEO:
            storageBreakdown.videoSize += size;
            break;
          case AUDIO:
            storageBreakdown.audioSize += size;
            break;
          case LONG_TEXT:
          case DOCUMENT:
            storageBreakdown.documentSize += size;
            break;
          default:
            break;
        }
      }
    }

    return storageBreakdown;
  }

  public static class MediaRecord {

    private final DatabaseAttachment attachment;
    private final RecipientId        recipientId;
    private final RecipientId        threadRecipientId;
    private final long               threadId;
    private final long               date;
    private final boolean            outgoing;

    private MediaRecord(@Nullable DatabaseAttachment attachment,
                        @NonNull RecipientId recipientId,
                        @NonNull RecipientId threadRecipientId,
                        long threadId,
                        long date,
                        boolean outgoing)
    {
      this.attachment        = attachment;
      this.recipientId       = recipientId;
      this.threadRecipientId = threadRecipientId;
      this.threadId          = threadId;
      this.date              = date;
      this.outgoing          = outgoing;
    }

    public static MediaRecord from(@NonNull Cursor cursor) {
      AttachmentTable          attachmentDatabase = SignalDatabase.attachments();
      List<DatabaseAttachment> attachments        = attachmentDatabase.getAttachments(cursor);
      RecipientId              recipientId        = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(MmsTable.RECIPIENT_ID)));
      long                     threadId           = cursor.getLong(cursor.getColumnIndexOrThrow(MmsTable.THREAD_ID));
      boolean                  outgoing           = MessageTable.Types.isOutgoingMessageType(cursor.getLong(cursor.getColumnIndexOrThrow(MmsTable.TYPE)));

      long date;

      if (MmsTable.Types.isPushType(cursor.getLong(cursor.getColumnIndexOrThrow(MmsTable.TYPE)))) {
        date = cursor.getLong(cursor.getColumnIndexOrThrow(MmsTable.DATE_SENT));
      } else {
        date = cursor.getLong(cursor.getColumnIndexOrThrow(MmsTable.DATE_RECEIVED));
      }

      RecipientId threadRecipient = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_RECIPIENT_ID)));

      return new MediaRecord(attachments != null && attachments.size() > 0 ? attachments.get(0) : null,
                             recipientId,
                             threadRecipient,
                             threadId,
                             date,
                             outgoing);
    }

    public @Nullable DatabaseAttachment getAttachment() {
      return attachment;
    }

    public String getContentType() {
      return attachment.getContentType();
    }

    public @NonNull RecipientId getRecipientId() {
      return recipientId;
    }

    public @NonNull RecipientId getThreadRecipientId() {
      return threadRecipientId;
    }

    public long getThreadId() {
      return threadId;
    }

    public long getDate() {
      return date;
    }

    public boolean isOutgoing() {
      return outgoing;
    }
  }

  public enum Sorting {
    Newest (AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + " DESC, " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DISPLAY_ORDER + " DESC, " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.ROW_ID + " DESC"),
    Oldest (AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + " ASC, " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DISPLAY_ORDER + " DESC, " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.ROW_ID + " ASC"),
    Largest(AttachmentTable.TABLE_NAME + "." + AttachmentTable.SIZE + " DESC, " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DISPLAY_ORDER + " DESC");

    private final String postFix;

    Sorting(@NonNull String order) {
      postFix = " ORDER BY " + order;
    }

    private String applyToQuery(@NonNull String query) {
      return query + postFix;
    }

    public boolean isRelatedToFileSize() {
      return this == Largest;
    }

    public static @NonNull Sorting deserialize(int code) {
      switch (code) {
        case 0:
          return Newest;
        case 1:
          return Oldest;
        case 2:
          return Largest;
        default:
          throw new IllegalArgumentException("Unknown code: " + code);
      }
    }
  }

  public final static class StorageBreakdown {
    private long photoSize;
    private long videoSize;
    private long audioSize;
    private long documentSize;

    public long getPhotoSize() {
      return photoSize;
    }

    public long getVideoSize() {
      return videoSize;
    }

    public long getAudioSize() {
      return audioSize;
    }

    public long getDocumentSize() {
      return documentSize;
    }
  }
}
