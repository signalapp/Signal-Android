package org.whispersystems.signalservice.internal.push.exceptions;

import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.util.function.Function;

import okhttp3.ResponseBody;

public final class PaymentsRegionException extends NonSuccessfulResponseCodeException {
  public PaymentsRegionException(int code) {
    super(code);
  }

  /**
   * Promotes a 403 to this exception type.
   */
  public static void responseCodeHandler(int responseCode, ResponseBody body, Function<String, String> getHeader) throws PaymentsRegionException {
    if (responseCode == 403) {
      throw new PaymentsRegionException(responseCode);
    }
  }
}
