/*
 * Copyright (C) 2019 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.push.exceptions;

public class RemoteAttestationResponseExpiredException extends NonSuccessfulResponseCodeException {
  public RemoteAttestationResponseExpiredException(String message) {
    super(message);
  }
}
