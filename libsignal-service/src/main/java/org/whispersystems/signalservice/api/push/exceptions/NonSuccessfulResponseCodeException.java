/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.push.exceptions;

import java.io.IOException;

/**
 * Indicates a server response that is not successful, typically something outside the 2xx range.
 */
public class NonSuccessfulResponseCodeException extends IOException {

  private final int code;

  public NonSuccessfulResponseCodeException(int code) {
    super("StatusCode: " + code);
    this.code = code;
  }

  public NonSuccessfulResponseCodeException(int code, String s) {
    super("[" + code + "] " + s);
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public boolean is4xx() {
    return code >= 400 && code < 500;
  }

  public boolean is5xx() {
    return code >= 500 && code < 600;
  }
}
