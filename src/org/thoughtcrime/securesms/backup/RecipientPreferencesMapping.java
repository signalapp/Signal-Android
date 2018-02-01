package org.thoughtcrime.securesms.backup;

import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;

public enum RecipientPreferencesMapping implements Exporter.Mapping {

  ID("_id", "id", FIELD_TYPE_INTEGER),
  ADDRESSES("recipient_ids", "recipient_ids", FIELD_TYPE_STRING),
  BLOCK("block", "block", FIELD_TYPE_INTEGER),
  NOTIFICATION("notification", "notification", FIELD_TYPE_STRING),
  VIBRATE("vibrate", "vibrate", FIELD_TYPE_INTEGER),
  MUTE_UNTIL("mute_until", "mute_until", FIELD_TYPE_INTEGER),
  COLOR("color", "color", FIELD_TYPE_STRING),
  SEEN_INVITE_REMINDER("seen_invite_reminder", "seen_invite_reminder", FIELD_TYPE_INTEGER),
  DEFAULT_SUBSCRIPTION_ID("default_subscription_id", "default_subscription_id", FIELD_TYPE_INTEGER),
  EXPIRE_MESSAGES("expire_messages", "expire_messages", FIELD_TYPE_INTEGER);

  public static final String TAG_RECIPIENT_PREFERENCES = "SignalRecipientPreferences";
  public static final String TABLE_NAME_RECIPIENT_PREFERENCES = "recipient_preferences";

  private String sqliteColumnName;
  private String xmlAttributeName;
  private int type;

  RecipientPreferencesMapping(String sqliteColumnName, String xmlAttributeName, int type) {
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
