package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;

public class ImageDatabase extends Database {

    private final static String IMAGES_QUERY = "SELECT " + PartDatabase.TABLE_NAME + "." + PartDatabase.ROW_ID + ", "
        + PartDatabase.TABLE_NAME + "." + PartDatabase.CONTENT_TYPE + ", "
        + PartDatabase.TABLE_NAME + "." + PartDatabase.THUMBNAIL_ASPECT_RATIO + ", "
        + PartDatabase.TABLE_NAME + "." + PartDatabase.UNIQUE_ID + ", "
        + PartDatabase.TABLE_NAME + "." + PartDatabase.MMS_ID + ", "
        + PartDatabase.TABLE_NAME + "." + PartDatabase.TRANSFER_STATE + ", "
        + PartDatabase.TABLE_NAME + "." + PartDatabase.SIZE + ", "
        + PartDatabase.TABLE_NAME + "." + PartDatabase.DATA + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.NORMALIZED_DATE_RECEIVED + ", "
        + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ADDRESS + " "
        + "FROM " + PartDatabase.TABLE_NAME + " LEFT JOIN " + MmsDatabase.TABLE_NAME
        + " ON " + PartDatabase.TABLE_NAME + "." + PartDatabase.MMS_ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " "
        + "WHERE " + PartDatabase.MMS_ID + " IN (SELECT " + MmsSmsColumns.ID
        + " FROM " + MmsDatabase.TABLE_NAME
        + " WHERE " + MmsDatabase.THREAD_ID + " = ?) AND "
        + PartDatabase.CONTENT_TYPE + " LIKE 'image/%' "
        + "ORDER BY " + PartDatabase.TABLE_NAME + "." + PartDatabase.ROW_ID + " DESC";

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
      AttachmentId attachmentId = new AttachmentId(cursor.getLong(cursor.getColumnIndexOrThrow(PartDatabase.ROW_ID)),
                                 cursor.getLong(cursor.getColumnIndexOrThrow(PartDatabase.UNIQUE_ID)));

      return new ImageRecord(attachmentId,
                             cursor.getLong(cursor.getColumnIndexOrThrow(PartDatabase.MMS_ID)),
                             !cursor.isNull(cursor.getColumnIndexOrThrow(PartDatabase.DATA)),
                             cursor.getString(cursor.getColumnIndexOrThrow(PartDatabase.CONTENT_TYPE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS)),
                             cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_RECEIVED)),
                             cursor.getInt(cursor.getColumnIndexOrThrow(PartDatabase.TRANSFER_STATE)),
                             cursor.getLong(cursor.getColumnIndexOrThrow(PartDatabase.SIZE)));
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
