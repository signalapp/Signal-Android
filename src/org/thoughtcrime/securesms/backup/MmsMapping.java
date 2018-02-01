package org.thoughtcrime.securesms.backup;

import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;

public enum MmsMapping implements Exporter.Mapping {

  ID("_id", "id", FIELD_TYPE_INTEGER),
  THREAD_ID("thread_id", "thread_id", FIELD_TYPE_INTEGER),
  DATE_SENT("date", "date", FIELD_TYPE_INTEGER),
  DATE_RECEIVED("date_received", "date_received", FIELD_TYPE_INTEGER),
  MESSAGE_BOX("msg_box", "msg_box", FIELD_TYPE_INTEGER),
  READ("read", "read", FIELD_TYPE_INTEGER),
  M_ID("m_id", "m_id", FIELD_TYPE_STRING),
  SUB("sub", "sub", FIELD_TYPE_STRING),
  SUB_CS("sub_cs", "sub_cs", FIELD_TYPE_INTEGER),
  BODY("body", "body", FIELD_TYPE_STRING),
  PART_COUNT("part_count", "part_count", FIELD_TYPE_INTEGER),
  CT_T("ct_t", "ct_t", FIELD_TYPE_STRING),
  CONTENT_LOCATION("ct_l", "ct_l", FIELD_TYPE_STRING),
  ADDRESS("address", "address", FIELD_TYPE_STRING),
  ADDRESS_DEVICE_ID("address_device_id", "address_device_id", FIELD_TYPE_INTEGER),
  EXPIRY("exp", "exp", FIELD_TYPE_INTEGER),
  M_CLS("m_cls", "m_cls", FIELD_TYPE_STRING),
  MESSAGE_TYPE("m_type", "m_type", FIELD_TYPE_INTEGER),
  V("v", "v", FIELD_TYPE_INTEGER),
  MESSAGE_SIZE("m_size", "m_size", FIELD_TYPE_INTEGER),
  PRI("pri", "pri", FIELD_TYPE_INTEGER),
  RR("rr", "rr", FIELD_TYPE_INTEGER),
  RPT_A("rpt_a", "rpt_a", FIELD_TYPE_INTEGER),
  RESP_ST("resp_st", "resp_st", FIELD_TYPE_INTEGER),
  STATUS("st", "st", FIELD_TYPE_INTEGER),
  TRANSACTION_ID("tr_id", "tr_id", FIELD_TYPE_STRING),
  RETR_ST("retr_st", "retr_st", FIELD_TYPE_INTEGER),
  RETR_TXT("retr_txt", "retr_txt", FIELD_TYPE_STRING),
  RETR_TXT_CS("retr_txt_cs", "retr_txt_cs", FIELD_TYPE_INTEGER),
  READ_STATUS("read_status", "read_status", FIELD_TYPE_INTEGER),
  CT_CLS("ct_cls", "ct_cls", FIELD_TYPE_INTEGER),
  RESP_TXT("resp_txt", "resp_txt", FIELD_TYPE_STRING),
  D_TM("d_tm", "d_tm", FIELD_TYPE_INTEGER),
  RECEIPT_COUNT("delivery_receipt_count", "delivery_receipt_count", FIELD_TYPE_INTEGER),
  MISMATCHED_IDENTITIES("mismatched_identities", "mismatched_identities", FIELD_TYPE_STRING),
  NETWORK_FAILURE("network_failures", "network_failures", FIELD_TYPE_STRING),
  D_RPT("d_rpt", "d_rpt", FIELD_TYPE_INTEGER),
  SUBSCRIPTION_ID("subscription_id", "subscription_id", FIELD_TYPE_INTEGER),
  EXPIRES_IN("expires_in", "expires_in", FIELD_TYPE_INTEGER),
  EXPIRE_STARTED("expire_started", "expire_started", FIELD_TYPE_INTEGER),
  NOTIFIED("notified", "notified", FIELD_TYPE_INTEGER);

  public static final String TAG_MMS = "SignalMms";
  public static final String TABLE_NAME_MMS = "mms";

  private String sqliteColumnName;
  private String xmlAttributeName;
  private int type;

  MmsMapping(String sqliteColumnName, String xmlAttributeName, int type) {
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
