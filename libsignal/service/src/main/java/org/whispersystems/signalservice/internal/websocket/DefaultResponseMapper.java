package org.whispersystems.signalservice.internal.websocket;

import org.whispersystems.libsignal.util.guava.Function;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.util.Objects;

/**
 * A default implementation of a {@link ResponseMapper} that can parse most known
 * application errors via {@link DefaultErrorMapper} and provides basic JSON parsing of the
 * response model if possible.
 * <p>
 * Can be extended to add custom parsing for both the result type and the error cases.
 * <p>
 * See {@link #extend(Class)} and {@link DefaultErrorMapper#extend()}.
 */
public class DefaultResponseMapper<Response> implements ResponseMapper<Response> {

  private final Class<Response>                clazz;
  private final ErrorMapper                    errorMapper;
  private final CustomResponseMapper<Response> customResponseMapper;

  public static <T> DefaultResponseMapper<T> getDefault(Class<T> clazz) {
    return new DefaultResponseMapper<>(clazz);
  }

  public static <T> DefaultResponseMapper.Builder<T> extend(Class<T> clazz) {
    return new DefaultResponseMapper.Builder<>(clazz);
  }

  private DefaultResponseMapper(Class<Response> clazz) {
    this(clazz, null, DefaultErrorMapper.getDefault());
  }

  private DefaultResponseMapper(Class<Response> clazz, CustomResponseMapper<Response> customResponseMapper, ErrorMapper errorMapper) {
    this.clazz                = clazz;
    this.customResponseMapper = customResponseMapper;
    this.errorMapper          = errorMapper;
  }

  @Override
  public ServiceResponse<Response> map(int status, String body, Function<String, String> getHeader, boolean unidentified) {
    Throwable applicationError;
    try {
      applicationError = errorMapper.parseError(status, body, getHeader);
    } catch (MalformedResponseException e) {
      applicationError = e;
    }
    if (applicationError == null) {
      try {
        if (customResponseMapper != null) {
          return Objects.requireNonNull(customResponseMapper.map(status, body, getHeader, unidentified));
        }
        return ServiceResponse.forResult(JsonUtil.fromJsonResponse(body, clazz), status, body);
      } catch (MalformedResponseException e) {
        applicationError = e;
      }
    }
    return ServiceResponse.forApplicationError(applicationError, status, body);
  }

  public static class Builder<Value> {
    private final Class<Value>                clazz;
    private       DefaultErrorMapper.Builder  errorMapperBuilder = DefaultErrorMapper.extend();
    private       CustomResponseMapper<Value> customResponseMapper;

    public Builder(Class<Value> clazz) {
      this.clazz = clazz;
    }

    public Builder<Value> withResponseMapper(CustomResponseMapper<Value> responseMapper) {
      this.customResponseMapper = responseMapper;
      return this;
    }

    public Builder<Value> withCustomError(int status, ErrorMapper errorMapper) {
      errorMapperBuilder.withCustom(status, errorMapper);
      return this;
    }

    public ResponseMapper<Value> build() {
      return new DefaultResponseMapper<>(clazz, customResponseMapper, errorMapperBuilder.build());
    }
  }

  public interface CustomResponseMapper<T> {
    ServiceResponse<T> map(int status, String body, Function<String, String> getHeader, boolean unidentified) throws MalformedResponseException;
  }
}
