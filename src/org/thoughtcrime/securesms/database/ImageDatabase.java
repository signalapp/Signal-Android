package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;

public class ImageDatabase extends Database {

    private final static String IMAGES_QUERY = "SELECT " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_TYPE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.THUMBNAIL_ASPECT_RATIO + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UNIQUE_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFER_STATE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.SIZE + ", "
        + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DATA + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.MESSAGE_BOX + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_SENT + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_RECEIVED + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ADDRESS + " "
        + "FROM " + AttachmentDatabase.TABLE_NAME + " LEFT JOIN " + MmsDatabase.TABLE_NAME
        + " ON " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " "
        + "WHERE " + AttachmentDatabase.MMS_ID + " IN (SELECT " + MmsSmsColumns.ID
        + " FROM " + MmsDatabase.TABLE_NAME
        + " WHERE " + MmsDatabase.THREAD_ID + " = ?) AND "
        + AttachmentDatabase.CONTENT_TYPE + " LIKE 'image/%' AND "
        + AttachmentDatabase.DATA + " IS NOT NULL "
        + "ORDER BY " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " DESC";

  public ImageDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getImagesForThread(long threadId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor = database.rawQuery(IMAGES_QUERY, new String[]{threadId+""});
    setNotifyConverationListeners(cursor, threadId);
    return cursor;
  }

  public static class ImageRecord {
    private final AttachmentId attachmentId;
    private final long         mmsId;
    private final boolean      hasData;
    private final String       contentType;
    private final String       address;
    private final long         date;
    private final int          transferState;
    private final long         size;

    private ImageRecord(AttachmentId attachmentId, long mmsId, boolean hasData,
                        String contentType, String address, long date,
                        int transferState, long size)
    {
      this.attachmentId  = attachmentId;
      this.mmsId         = mmsId;
      this.hasData       = hasData;
      this.contentType   = contentType;
      this.address       = address;
      this.date          = date;
      this.transferState = transferState;
      this.size          = size;
    }

    public static ImageRecord from(Cursor cursor) {
      AttachmentId attachmentId = new AttachmentId(cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.ROW_ID)),
                                                   cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.UNIQUE_ID)));

      long date;

      if (MmsDatabase.Types.isPushType(cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX)))) {
        date = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.DATE_SENT));
      } else {
        date = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.DATE_RECEIVED));
      }

      return new ImageRecord(attachmentId,
                             cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.MMS_ID)),
                             !cursor.isNull(cursor.getColumnIndexOrThrow(AttachmentDatabase.DATA)),
                             cursor.getString(cursor.getColumnIndexOrThrow(AttachmentDatabase.CONTENT_TYPE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS)),
                             date,
                             cursor.getInt(cursor.getColumnIndexOrThrow(AttachmentDatabase.TRANSFER_STATE)),
                             cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentDatabase.SIZE)));
    }

    public Attachment getAttachment() {
      return new DatabaseAttachment(attachmentId, mmsId, hasData, contentType, transferState, size, null, null, null);
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

  }


}
