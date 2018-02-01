package org.thoughtcrime.securesms.backup;

import static android.database.Cursor.FIELD_TYPE_BLOB;
import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;

public enum GroupsMapping implements Exporter.Mapping {

  ID("_id", "id", FIELD_TYPE_INTEGER),
  GROUP_ID("group_id", "group_id", FIELD_TYPE_STRING),
  TITLE("title", "title", FIELD_TYPE_STRING),
  MEMBERS("members", "members", FIELD_TYPE_STRING),
  AVATAR("avatar", "avatar", FIELD_TYPE_BLOB),
  AVATAR_ID("avatar_id", "avatar_id", FIELD_TYPE_INTEGER),
  AVATAR_KEY("avatar_key", "avatar_key", FIELD_TYPE_BLOB),
  AVATAR_CONTENT_TYPE("avatar_content_type", "avatar_content_type", FIELD_TYPE_STRING),
  AVATAR_RELAY("avatar_relay", "avatar_relay", FIELD_TYPE_STRING),
  AVATAR_DIGEST("avatar_digest", "avatar_digest", FIELD_TYPE_BLOB),
  TIMESTAMP("timestamp", "timestamp", FIELD_TYPE_INTEGER),
  ACTIVE("active", "active", FIELD_TYPE_INTEGER);

  public static final String TAG_GROUPS = "SignalGroups";
  public static final String TABLE_NAME_GROUPS = "groups";

  private String sqliteColumnName;
  private String xmlAttributeName;
  private int type;

  GroupsMapping(String sqliteColumnName, String xmlAttributeName, int type) {
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
