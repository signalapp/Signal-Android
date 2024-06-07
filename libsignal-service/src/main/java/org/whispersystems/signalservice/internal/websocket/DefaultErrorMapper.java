package org.whispersystems.signalservice.internal.websocket;



import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;
import org.whispersystems.signalservice.api.push.exceptions.ExpectationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.internal.push.AuthCredentials;
import org.whispersystems.signalservice.internal.push.DeviceLimit;
import org.whispersystems.signalservice.internal.push.DeviceLimitExceededException;
import org.whispersystems.signalservice.internal.push.LockedException;
import org.whispersystems.signalservice.internal.push.MismatchedDevices;
import org.whispersystems.signalservice.internal.push.ProofRequiredResponse;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.StaleDevices;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A default implementation of a {@link ErrorMapper} that can parse most known application
 * errors.
 * <p>
 * Can be extended to add custom error mapping via {@link #extend()}.
 * <p>
 * While this call can be used directly, it is primarily intended to be used as part of
 * {@link DefaultResponseMapper}.
 */
public final class DefaultErrorMapper implements ErrorMapper {

  private static final DefaultErrorMapper INSTANCE = new DefaultErrorMapper();

  private final Map<Integer, ErrorMapper> customErrorMappers;

  public static DefaultErrorMapper getDefault() {
    return INSTANCE;
  }

  public static DefaultErrorMapper.Builder extend() {
    return new DefaultErrorMapper.Builder();
  }

  private DefaultErrorMapper() {
    this(Collections.emptyMap());
  }

  private DefaultErrorMapper(Map<Integer, ErrorMapper> customErrorMappers) {
    this.customErrorMappers = customErrorMappers;
  }

  public Throwable parseError(WebsocketResponse websocketResponse) {
    return parseError(websocketResponse.getStatus(), websocketResponse.getBody(), websocketResponse::getHeader);
  }

  @Override
  public Throwable parseError(int status, String body, Function<String, String> getHeader) {
    if (customErrorMappers.containsKey(status)) {
      try {
        return customErrorMappers.get(status).parseError(status, body, getHeader);
      } catch (MalformedResponseException e) {
        return e;
      }
    }

    switch (status) {
      case 401:
      case 403:
        return new AuthorizationFailedException(status, "Authorization failed!");
      case 402:
        return new CaptchaRequiredException();
      case 404:
        return new NotFoundException("Not found");
      case 409:
        try {
          return new MismatchedDevicesException(JsonUtil.fromJsonResponse(body, MismatchedDevices.class));
        } catch (MalformedResponseException e) {
          return e;
        }
      case 410:
        try {
          return new StaleDevicesException(JsonUtil.fromJsonResponse(body, StaleDevices.class));
        } catch (MalformedResponseException e) {
          return e;
        }
      case 411:
        try {
          return new DeviceLimitExceededException(JsonUtil.fromJsonResponse(body, DeviceLimit.class));
        } catch (MalformedResponseException e) {
          return e;
        }
      case 413:
      case 429: {
        long           retryAfterLong = Util.parseLong(getHeader.apply("Retry-After"), -1);
        Optional<Long> retryAfter     = retryAfterLong != -1 ? Optional.of(TimeUnit.SECONDS.toMillis(retryAfterLong)) : Optional.empty();
        return new RateLimitException(status, "Rate limit exceeded: " + status, retryAfter);
      }
      case 417:
        return new ExpectationFailedException();
      case 423:
        PushServiceSocket.RegistrationLockFailure accountLockFailure;
        try {
          accountLockFailure = JsonUtil.fromJsonResponse(body, PushServiceSocket.RegistrationLockFailure.class);
        } catch (MalformedResponseException e) {
          return e;
        }

        return new LockedException(accountLockFailure.length,
                                   accountLockFailure.timeRemaining,
                                   accountLockFailure.svr2Credentials,
                                   accountLockFailure.svr3Credentials);
      case 428:
        ProofRequiredResponse proofRequiredResponse;
        try {
          proofRequiredResponse = JsonUtil.fromJsonResponse(body, ProofRequiredResponse.class);
        } catch (MalformedResponseException e) {
          return e;
        }
        String retryAfterRaw = getHeader.apply("Retry-After");
        long retryAfter = Util.parseInt(retryAfterRaw, -1);

        return new ProofRequiredException(proofRequiredResponse, retryAfter);
      case 499:
        return new DeprecatedVersionException();
      case 508:
        return new ServerRejectedException();
    }

    if (status != 200 && status != 202 && status != 204) {
      return new NonSuccessfulResponseCodeException(status, "Bad response: " + status);
    }

    return null;
  }

  public static class Builder {
    private final Map<Integer, ErrorMapper> customErrorMappers = new HashMap<>();

    public Builder withCustom(int status, ErrorMapper errorMapper) {
      customErrorMappers.put(status, errorMapper);
      return this;
    }

    public ErrorMapper build() {
      return new DefaultErrorMapper(customErrorMappers);
    }
  }
}
