/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.push.exceptions;

import java.io.IOException;

public class NonSuccessfulResponseCodeException extends IOException {

  public NonSuccessfulResponseCodeException() {
    super();
  }

  public NonSuccessfulResponseCodeException(String s) {
    super(s);
  }
}
