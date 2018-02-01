package org.thoughtcrime.securesms.backup;

import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;

public enum DraftsMapping implements Exporter.Mapping {

  ID("_id", "id", FIELD_TYPE_INTEGER),
  THREAD_ID("thread_id", "thread_id", FIELD_TYPE_INTEGER),
  DRAFT_TYPE("type", "type", FIELD_TYPE_STRING),
  DRAFT_VALUE("value", "value", FIELD_TYPE_STRING);

  public static final String TAG_DRAFTS = "SignalDrafts";
  public static final String TABLE_NAME_DRAFTS = "drafts";

  private String sqliteColumnName;
  private String xmlAttributeName;
  private int type;

  DraftsMapping(String sqliteColumnName, String xmlAttributeName, int type) {
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
