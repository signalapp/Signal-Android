package org.whispersystems.signalservice.internal.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

import java.util.function.Function;

import okhttp3.ResponseBody;

public final class PaymentsRegionException extends NonSuccessfulResponseCodeException {
  public PaymentsRegionException(int code) {
    super(code);
  }
}
