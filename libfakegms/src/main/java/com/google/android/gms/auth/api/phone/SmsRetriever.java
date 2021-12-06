package com.google.android.gms.auth.api.phone;

import android.content.Context;

public final class SmsRetriever {
  public static final String EXTRA_SMS_MESSAGE = "com.google.android.gms.auth.api.phone.EXTRA_SMS_MESSAGE";
  public static final String EXTRA_STATUS = "com.google.android.gms.auth.api.phone.EXTRA_STATUS";
  public static final String SMS_RETRIEVED_ACTION = "com.google.android.gms.auth.api.phone.SMS_RETRIEVED";

  public static SmsRetrieverClient getClient(Context context) {
    return new SmsRetrieverClient();
  }
}
