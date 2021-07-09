package org.whispersystems.signalservice.internal.websocket;

import org.whispersystems.libsignal.util.guava.Function;

/**
 * Can map an API response to an appropriate {@link Throwable}.
 * <p>
 * Unless you need to do something really special, you should only be implementing this to customize
 * {@link DefaultErrorMapper}.
 */
public interface ErrorMapper {
  Throwable parseError(int status, String body, Function<String, String> getHeader);
}
