package org.thoughtcrime.securesms.backup;

import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;

public enum IdentitiesMapping implements Exporter.Mapping {

  ID("_id", "id", FIELD_TYPE_INTEGER),
  ADDRESS("address", "address", FIELD_TYPE_STRING),
  IDENTITY_KEY("key", "key", FIELD_TYPE_STRING),
  TIMESTAMP("timestamp", "timestamp", FIELD_TYPE_INTEGER),
  FIRST_USE("first_use", "first_use", FIELD_TYPE_INTEGER),
  NONBLOCKING_APPROVAL("nonblocking_approval", "nonblocking_approval", FIELD_TYPE_INTEGER),
  VERIFIED("verified", "verified", FIELD_TYPE_INTEGER);

  public static final String TAG_IDENTITIES = "SignalIdentities";
  public static final String TABLE_NAME_IDENTITIES = "identities";

  private String sqliteColumnName;
  private String xmlAttributeName;
  private int type;

  IdentitiesMapping(String sqliteColumnName, String xmlAttributeName, int type) {
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
