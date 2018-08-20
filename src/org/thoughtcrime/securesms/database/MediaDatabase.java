package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.ContentObservable;
import android.database.ContentObserver;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;

import java.util.List;

public class MediaDatabase extends Database {

    private static final String BASE_MEDIA_QUERY = "SELECT " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " AS " + AttachmentDatabase.ROW_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_TYPE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.THUMBNAIL_ASPECT_RATIO + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UNIQUE_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFER_STATE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.SIZE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FILE_NAME + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DATA + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.THUMBNAIL + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_LOCATION + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_DISPOSITION + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DIGEST + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FAST_PREFLIGHT_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VOICE_NOTE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.WIDTH + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.HEIGHT + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.QUOTE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.NAME + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.MESSAGE_BOX + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_SENT + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_RECEIVED + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ADDRESS + " "
        + "FROM " + AttachmentDatabase.TABLE_NAME + " LEFT JOIN " + MmsDatabase.TABLE_NAME
        + " ON " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " "
        + "WHERE " + AttachmentDatabase.MMS_ID + " IN (SELECT " + MmsSmsColumns.ID
        + " FROM " + MmsDatabase.TABLE_NAME
        + " WHERE " + MmsDatabase.THREAD_ID + " = ?) AND (%s) AND "
        + AttachmentDatabase.DATA + " IS NOT NULL "
        + "ORDER BY " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " DESC";

  private static final String GALLERY_MEDIA_QUERY  = String.format(BASE_MEDIA_QUERY, AttachmentDatabase.CONTENT_TYPE + " LIKE 'image/%' OR " + AttachmentDatabase.CONTENT_TYPE + " LIKE 'video/%'");
  private static final String DOCUMENT_MEDIA_QUERY = String.format(BASE_MEDIA_QUERY, AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'image/%' AND " + AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'video/%' AND " + AttachmentDatabase.CONTENT_TYPE + " NOT LIKE 'audio/%'");

  MediaDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getGalleryMediaForThread(long threadId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor = database.rawQuery(GALLERY_MEDIA_QUERY, new String[]{threadId+""});
    setNotifyConverationListeners(cursor, threadId);
    return cursor;
  }

  public void subscribeToMediaChanges(@NonNull ContentObserver observer) {
    registerAttachmentListeners(observer);
  }

  public void unsubscribeToMediaChanges(@NonNull ContentObserver observer) {
    context.getContentResolver().unregisterContentObserver(observer);
  }

  public Cursor getDocumentMediaForThread(long threadId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor = database.rawQuery(DOCUMENT_MEDIA_QUERY, new String[]{threadId+""});
    setNotifyConverationListeners(cursor, threadId);
    return cursor;
  }

  public static class MediaRecord {

    private final DatabaseAttachment attachment;
    private final Address            address;
    private final long               date;
    private final boolean            outgoing;

    private MediaRecord(DatabaseAttachment attachment, @Nullable Address address, long date, boolean outgoing) {
      this.attachment = attachment;
      this.address    = address;
      this.date       = date;
      this.outgoing   = outgoing;
    }

    public static MediaRecord from(@NonNull Context context, @NonNull Cursor cursor) {
      AttachmentDatabase       attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
      List<DatabaseAttachment> attachments        = attachmentDatabase.getAttachment(cursor);
      String                   serializedAddress  = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS));
      boolean                  outgoing           = MessagingDatabase.Types.isOutgoingMessageType(cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX)));
      Address                  address            = null;

      if (serializedAddress != null) {
        address = Address.fromSerialized(serializedAddress);
      }

      long date;

      if (MmsDatabase.Types.isPushType(cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX)))) {
        date = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.DATE_SENT));
      } else {
        date = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.DATE_RECEIVED));
      }

      return new MediaRecord(attachments != null && attachments.size() > 0 ? attachments.get(0) : null, address, date, outgoing);
    }

    public DatabaseAttachment getAttachment() {
      return attachment;
    }

    public String getContentType() {
      return attachment.getContentType();
    }

    public @Nullable Address getAddress() {
      return address;
    }

    public long getDate() {
      return date;
    }

    public boolean isOutgoing() {
      return outgoing;
    }

  }


}
