package org.thoughtcrime.securesms.backup;

import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;

public enum SmsMapping implements Exporter.Mapping {

  ID("_id", "id", FIELD_TYPE_INTEGER),
  THREAD_ID("thread_id", "thread_id", FIELD_TYPE_INTEGER),
  ADDRESS("address", "address", FIELD_TYPE_STRING),
  ADDRESS_DEVICE_ID("address_device_id", "address_device_id", FIELD_TYPE_INTEGER),
  PERSON("person", "person", FIELD_TYPE_INTEGER),
  DATE_RECEIVED("date", "date_received", FIELD_TYPE_INTEGER),
  DATE_SENT("date_sent", "date_sent", FIELD_TYPE_INTEGER),
  PROTOCOL("protocol", "protocol", FIELD_TYPE_INTEGER),
  READ("read", "read", FIELD_TYPE_INTEGER),
  STATUS("status", "status", FIELD_TYPE_INTEGER),
  TYPE("type", "type", FIELD_TYPE_INTEGER),
  REPLY_PATH_PRESENT("reply_path_present", "reply_path_present", FIELD_TYPE_INTEGER),
  RECEIPT_COUNT("delivery_receipt_count", "delivery_receipt_count", FIELD_TYPE_INTEGER),
  SUBJECT("subject", "subject", FIELD_TYPE_STRING),
  BODY("body", "body", FIELD_TYPE_STRING),
  MISMATCHED_IDENTITIES("mismatched_identities", "mismatched_identities", FIELD_TYPE_STRING),
  SERVICE_CENTER("service_center", "service_center", FIELD_TYPE_STRING),
  SUBSCRIPTION_ID("subscription_id", "subscription_id", FIELD_TYPE_INTEGER),
  EXPIRES_IN("expires_in", "expires_in", FIELD_TYPE_INTEGER),
  EXPIRE_STARTED("expire_started", "expire_started", FIELD_TYPE_INTEGER);

  public static final String TAG_SMS = "SignalSms";
  public static final String TABLE_NAME_SMS = "sms";

  private String sqliteColumnName;
  private String xmlAttributeName;
  private int type;

  SmsMapping(String sqliteColumnName, String xmlAttributeName, int type) {
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
