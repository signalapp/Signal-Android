package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.util.List;

public class MediaDatabase extends Database {

    public  static final int    ALL_THREADS         = -1;
    private static final String THREAD_RECIPIENT_ID = "THREAD_RECIPIENT_ID";

    private static final String BASE_MEDIA_QUERY = "SELECT " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " AS " + AttachmentDatabase.ROW_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_TYPE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UNIQUE_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFER_STATE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.SIZE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FILE_NAME + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DATA + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CDN_NUMBER + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_LOCATION + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_DISPOSITION + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DIGEST + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FAST_PREFLIGHT_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VOICE_NOTE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.BORDERLESS + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.WIDTH + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.HEIGHT + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.QUOTE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_PACK_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_PACK_KEY + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_EMOJI + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VISUAL_HASH + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFORM_PROPERTIES + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DISPLAY_ORDER + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CAPTION + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.NAME + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UPLOAD_TIMESTAMP + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.MESSAGE_BOX + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_SENT + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_RECEIVED + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_SERVER + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.THREAD_ID + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.RECIPIENT_ID + ", "
        + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.RECIPIENT_ID + " as " + THREAD_RECIPIENT_ID + " "
        + "FROM " + AttachmentDatabase.TABLE_NAME + " LEFT JOIN " + MmsDatabase.TABLE_NAME
        + " ON " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " "
        + "LEFT JOIN " + ThreadDatabase.TABLE_NAME
        + " ON " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.THREAD_ID + " "
        + "WHERE " + AttachmentDatabase.MMS_ID + " IN (SELECT " + MmsSmsColumns.ID
        + " FROM " + MmsDatabase.TABLE_NAME
        + " WHERE " + MmsDatabase.THREAD_ID + " __EQUALITY__ ?) AND (%s) AND "
        + MmsDatabase.VIEW_ONCE + " = 0 AND "
        + AttachmentDatabase.DATA + " IS NOT NULL AND "
        + "(" + AttachmentDatabase.QUOTE + " = 0 OR (" + AttachmentDatabase.QUOTE + " = 1 AND " + AttachmentDatabase.DATA_HASH + " IS NULL)) AND "
        + AttachmentDatabase.STICKER_PACK_ID + " IS NULL ";

   private static final String UNIQUE_MEDIA_QUERY = "SELECT "
        + "MAX(" + AttachmentDatabase.SIZE + ") as " + AttachmentDatabase.SIZE + ", "
        + AttachmentDatabase.CONTENT_TYPE + " "
        + "FROM " + AttachmentDatabase.TABLE_NAME + " "
        + "WHERE " + AttachmentDatabase.STICKER_PACK_ID + " IS NULL "
        + "GROUP BY " + AttachmentDatabase.DATA;

  private static final String GALLERY_MEDIA_QUERY  = String.format(BASE_MEDIA_QUERY, AttachmentDatabase.CONTENT_TYPE + " LIKE 'image/%' OR " + AttachmentDatabase.CONTENT_TYPE + " LIKE 'video/%'");
  private static final String AUDIO_MEDIA_QUERY    = String.format(BASE_MEDIA_QUERY, AttachmentDatabase.CONTENT_TYPE + " LIKE 'audio/%'");
  private static final String ALL_MEDIA_QUERY      = String.format(BASE_MEDIA_QUERY, AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'text/x-signal-plain'");
  private static final String DOCUMENT_MEDIA_QUERY = String.format(BASE_MEDIA_QUERY, AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'image/%' AND " +
                                                                                     AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'video/%' AND " +
                                                                                     AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'audio/%' AND " +
                                                                                     AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'text/x-signal-plain'");

  MediaDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public @NonNull Cursor getGalleryMediaForThread(long threadId, @NonNull Sorting sorting) {
    return getGalleryMediaForThread(threadId, sorting, false);
  }

  public @NonNull Cursor getGalleryMediaForThread(long threadId, @NonNull Sorting sorting, boolean listenToAllThreads) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String         query    = sorting.applyToQuery(applyEqualityOperator(threadId, GALLERY_MEDIA_QUERY));
    String[]       args     = {threadId + ""};
    Cursor         cursor   = database.rawQuery(query, args);
    if (listenToAllThreads) {
      setNotifyConversationListeners(cursor);
    } else {
      setNotifyConversationListeners(cursor, threadId);
    }
    return cursor;
  }

  public @NonNull Cursor getDocumentMediaForThread(long threadId, @NonNull Sorting sorting) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String         query    = sorting.applyToQuery(applyEqualityOperator(threadId, DOCUMENT_MEDIA_QUERY));
    String[]       args     = {threadId + ""};
    Cursor         cursor   = database.rawQuery(query, args);
    setNotifyConversationListeners(cursor, threadId);
    return cursor;
  }

  public @NonNull Cursor getAudioMediaForThread(long threadId, @NonNull Sorting sorting) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String         query    = sorting.applyToQuery(applyEqualityOperator(threadId, AUDIO_MEDIA_QUERY));
    String[]       args     = {threadId + ""};
    Cursor         cursor   = database.rawQuery(query, args);
    setNotifyConversationListeners(cursor, threadId);
    return cursor;
  }

  public @NonNull Cursor getAllMediaForThread(long threadId, @NonNull Sorting sorting) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String         query    = sorting.applyToQuery(applyEqualityOperator(threadId, ALL_MEDIA_QUERY));
    String[]       args     = {threadId + ""};
    Cursor         cursor   = database.rawQuery(query, args);
    setNotifyConversationListeners(cursor, threadId);
    return cursor;
  }

  private static String applyEqualityOperator(long threadId, String query) {
    return query.replace("__EQUALITY__", threadId == ALL_THREADS ? "!=" : "=");
  }

  public void subscribeToMediaChanges(@NonNull ContentObserver observer) {
    registerAttachmentListeners(observer);
  }

  public void unsubscribeToMediaChanges(@NonNull ContentObserver observer) {
    context.getContentResolver().unregisterContentObserver(observer);
  }

  public StorageBreakdown getStorageBreakdown() {
    StorageBreakdown storageBreakdown = new StorageBreakdown();
    SQLiteDatabase   database         = databaseHelper.getReadableDatabase();

    try (Cursor cursor = database.rawQuery(UNIQUE_MEDIA_QUERY, new String[0])) {
      int sizeColumn        = cursor.getColumnIndexOrThrow(AttachmentDatabase.SIZE);
      int contentTypeColumn = cursor.getColumnIndexOrThrow(AttachmentDatabase.CONTENT_TYPE);

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

    public static MediaRecord from(@NonNull Context context, @NonNull Cursor cursor) {
      AttachmentDatabase       attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
      List<DatabaseAttachment> attachments        = attachmentDatabase.getAttachment(cursor);
      RecipientId              recipientId        = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.RECIPIENT_ID)));
      long                     threadId           = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.THREAD_ID));
      boolean                  outgoing           = MessageDatabase.Types.isOutgoingMessageType(cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX)));

      long date;

      if (MmsDatabase.Types.isPushType(cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX)))) {
        date = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.DATE_SENT));
      } else {
        date = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.DATE_RECEIVED));
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
    Newest (AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + " DESC, " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DISPLAY_ORDER + " DESC, " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " DESC"),
    Oldest (AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + " ASC, "  + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DISPLAY_ORDER + " DESC, " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " ASC"),
    Largest(AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.SIZE   + " DESC, " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DISPLAY_ORDER + " DESC");

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
