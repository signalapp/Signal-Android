package com.google.android.gms.common.api;

public final class Status {
  public Status(int statusCode) {
  }

  public int getStatusCode() {
    return CommonStatusCodes.ERROR;
  }

  public static final int RESULT_SUCCESS = 0;
}
