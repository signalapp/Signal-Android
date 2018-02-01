package org.thoughtcrime.securesms.backup;

import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;

public enum ThreadMapping implements Exporter.Mapping {

  ID("_id", "id", FIELD_TYPE_INTEGER),
  DATE("date", "date", FIELD_TYPE_INTEGER),
  MESSAGE_COUNT("message_count", "message_count", FIELD_TYPE_INTEGER),
  ADDRESSES("recipient_ids", "recipient_ids", FIELD_TYPE_STRING),
  SNIPPET("snippet", "snippet", FIELD_TYPE_STRING),
  SNIPPET_CHARSET("snippet_cs", "snippet_cs", FIELD_TYPE_INTEGER),
  READ("read", "read", FIELD_TYPE_INTEGER),
  TYPE("type", "type", FIELD_TYPE_INTEGER),
  ERROR("error", "error", FIELD_TYPE_INTEGER),
  SNIPPET_TYPE("snippet_type", "snippet_type", FIELD_TYPE_INTEGER),
  SNIPPET_URI("snippet_uri", "snippet_uri", FIELD_TYPE_STRING),
  ARCHIVED("archived", "archived", FIELD_TYPE_INTEGER),
  STATUS("status", "status", FIELD_TYPE_INTEGER),
  RECEIPT_COUNT("delivery_receipt_count", "delivery_receipt_count", FIELD_TYPE_INTEGER),
  EXPIRES_IN("expires_in", "expires_in", FIELD_TYPE_INTEGER),
  LAST_SEEN("last_seen", "last_seen", FIELD_TYPE_INTEGER);

  public static final String TAG_THREAD = "SignalThread";
  public static final String TABLE_NAME_THREAD = "thread";

  private String sqliteColumnName;
  private String xmlAttributeName;
  private int type;

  ThreadMapping(String sqliteColumnName, String xmlAttributeName, int type) {
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
