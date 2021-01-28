package org.whispersystems.signalservice.api.push.exceptions;

/**
 * Indicates the server has rejected the request and we should stop retrying.
 */
public class ServerRejectedException extends NonSuccessfulResponseCodeException {
}
