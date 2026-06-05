package org.whispersystems.signalservice.api.push.exceptions;

import org.signal.network.exceptions.NonSuccessfulResponseCodeException;

/**
 * Indicates the server has rejected the request and we should stop retrying.
 */
public class ServerRejectedException extends NonSuccessfulResponseCodeException {
  public ServerRejectedException() {
    super(508);
  }
}
