/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.push.exceptions;

import java.io.IOException;

public class UnregisteredUserException extends IOException {

  private final String e164number;

  public UnregisteredUserException(String e164number, Exception exception) {
    super(exception);
    this.e164number = e164number;
  }

  public String getE164Number() {
    return e164number;
  }
}
