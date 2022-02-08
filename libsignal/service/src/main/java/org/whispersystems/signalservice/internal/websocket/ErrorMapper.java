package org.whispersystems.signalservice.internal.websocket;

import org.whispersystems.libsignal.util.guava.Function;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;

/**
 * Can map an API response to an appropriate {@link Throwable}.
 * <p>
 * Unless you need to do something really special, you should only be implementing this to customize
 * {@link DefaultErrorMapper}.
 */
public interface ErrorMapper {
  Throwable parseError(int status, String body, Function<String, String> getHeader) throws MalformedResponseException;

  default Throwable parseError(int status) throws MalformedResponseException {
    return parseError(status, "", s -> "");
  }
}
