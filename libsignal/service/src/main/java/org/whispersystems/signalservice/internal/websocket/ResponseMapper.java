package org.whispersystems.signalservice.internal.websocket;

import org.whispersystems.libsignal.util.guava.Function;
import org.whispersystems.signalservice.internal.ServiceResponse;

/**
 * Responsible for taking an API response and converting it to a {@link ServiceResponse}. This includes
 * parsing for a success as well as any application errors. All errors (application or parsing related)
 * are encapsulated in an error version of a {@link ServiceResponse}, hence why no method throws an
 * exception.
 * <p>
 * Unless you need to do something really special, you should only be extending this to be provided to
 * {@link DefaultResponseMapper}.
 *
 * @param <T> - The final type the API response will map into.
 */
public interface ResponseMapper<T> {
  ServiceResponse<T> map(int status, String body, Function<String, String> getHeader, boolean unidentified);

  default ServiceResponse<T> map(WebsocketResponse response) {
    return map(response.getStatus(), response.getBody(), response::getHeader, response.isUnidentified());
  }
}
