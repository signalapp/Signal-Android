package org.thoughtcrime.securesms.backup;

import static android.database.Cursor.FIELD_TYPE_BLOB;
import static android.database.Cursor.FIELD_TYPE_FLOAT;
import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;

public enum AttachmentMapping implements Exporter.Mapping {

  ID("_id", "id", FIELD_TYPE_INTEGER),
  MMS_ID("mid", "mid", FIELD_TYPE_INTEGER),
  SEQ("seq", "seq", FIELD_TYPE_INTEGER),
  CONTENT_TYPE("ct", "ct", FIELD_TYPE_STRING),
  NAME("name", "name", FIELD_TYPE_STRING),
  CONTENT_DISPOSITION("cd", "cd", FIELD_TYPE_STRING),
  FN("fn", "fn", FIELD_TYPE_STRING),
  CID("cid", "cid", FIELD_TYPE_STRING),
  CONTENT_LOCATION("cl", "cl", FIELD_TYPE_STRING),
  CTT_S("ctt_s", "ctt_s", FIELD_TYPE_INTEGER),
  CTT_T("ctt_t", "ctt_t", FIELD_TYPE_STRING),
  ENCRYPTED("encrypted", "encrypted", FIELD_TYPE_INTEGER),
  TRANSFER_STATE("pending_push", "pending_push", FIELD_TYPE_INTEGER),
  DATA("_data", "data", FIELD_TYPE_STRING),
  SIZE("data_size", "data_size", FIELD_TYPE_INTEGER),
  FILE_NAME("file_name", "file_name", FIELD_TYPE_STRING),
  THUMBNAIL("thumbnail", "thumbnail", FIELD_TYPE_STRING),
  THUMBNAIL_ASPECT_RATIO("aspect_ratio", "aspect_ratio", FIELD_TYPE_FLOAT),
  UNIQUE_ID("unique_id", "unique_id", FIELD_TYPE_INTEGER),
  DIGEST("digest", "digest", FIELD_TYPE_BLOB),
  FAST_PREFLIGHT_ID("fast_preflight_id", "fast_preflight_id", FIELD_TYPE_STRING),
  VOICE_NOTE("voice_note", "voice_note", FIELD_TYPE_INTEGER);

  public static final String TAG_ATTACHMENT_ROW = "SignalAttachmentRow";
  public static final String TABLE_NAME_ATTACHMENT_ROW = "part";

  private String sqliteColumnName;
  private String xmlAttributeName;
  private int type;

  AttachmentMapping(String sqliteColumnName, String xmlAttributeName, int type) {
    this.sqliteColumnName = sqliteColumnName;
    this.xmlAttributeName = xmlAttributeName;
    this.type = type;
  }

  @Override
  public String sqliteColumnName() {
    return this.sqliteColumnName;
  }

  @Override
  public String xmlAttributeName() {
    return this.xmlAttributeName;
  }

  @Override
  public int type() {
    return this.type;
  }
}
