/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 * <p>
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.push.exceptions;

public final class RangeException extends NonSuccessfulResponseCodeException {

  public RangeException(long requested) {
    super("Range request out of bounds " + requested);
  }
}
