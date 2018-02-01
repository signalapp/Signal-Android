package org.thoughtcrime.securesms.backup;

import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;

public enum PushMapping implements Exporter.Mapping {

  ID("_id", "id", FIELD_TYPE_INTEGER),
  TYPE("type", "type", FIELD_TYPE_INTEGER),
  SOURCE("source", "source", FIELD_TYPE_STRING),
  DEVICE_ID("device_id", "device_id", FIELD_TYPE_INTEGER),
  LEGACY_MSG("body", "body", FIELD_TYPE_STRING),
  CONTENT("content", "content", FIELD_TYPE_STRING),
  TIMESTAMP("timestamp", "timestamp", FIELD_TYPE_INTEGER);

  public static final String TAG_PUSH = "SignalPush";
  public static final String TABLE_NAME_PUSH = "push";

  private String sqliteColumnName;
  private String xmlAttributeName;
  private int type;

  PushMapping(String sqliteColumnName, String xmlAttributeName, int type) {
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
