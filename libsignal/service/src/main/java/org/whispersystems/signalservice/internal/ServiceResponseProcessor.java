package org.whispersystems.signalservice.internal;


import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Provide the basis for processing a {@link ServiceResponse} in a sharable, quasi-enforceable
 * ways. The goal is to balance the readability at the call sites where the various cases are handled
 * and provide call specific information of what should be expected.
 * <p>
 * General premise is for subclasses to override and expose (via access modifier) the types of errors that
 * should be handled when processing a response. For example, if {@link #notFound()} should be specifically
 * handled then a subclass should override it, change the modifier to public, and then the caller knows it's
 * a possible error case.
 * <p>
 * This doesn't exactly enforce the handling like a check exception would, but does hint
 * to the caller what they should be aware of as possible outcomes of processing a response.
 */
public abstract class ServiceResponseProcessor<T> {

  protected final ServiceResponse<T> response;

  public ServiceResponseProcessor(ServiceResponse<T> response) {
    this.response = response;
  }

  public ServiceResponse<T> getResponse() {
    return response;
  }

  public T getResult() {
    Preconditions.checkArgument(response.getResult().isPresent());
    return response.getResult().get();
  }

  public T getResultOrThrow() throws IOException {
    if (hasResult()) {
      return getResult();
    }

    Throwable error = getError();
    if (error instanceof IOException) {
      throw (IOException) error;
    } else if (error instanceof RuntimeException) {
      throw (RuntimeException) error;
    } else if (error instanceof InterruptedException || error instanceof TimeoutException) {
      throw new IOException(error);
    } else {
      throw new IllegalStateException("Unexpected error type for response processor", error);
    }
  }

  public boolean hasResult() {
    return response.getResult().isPresent();
  }

  protected Throwable getError() {
    return OptionalUtil.or(response.getApplicationError(), response.getExecutionError()).orElse(null);
  }

  protected boolean authorizationFailed() {
    return response.getStatus() == 401 || response.getStatus() == 403;
  }

  protected boolean notFound() {
    return response.getStatus() == 404;
  }

  protected boolean mismatchedDevices() {
    return response.getStatus() == 409;
  }

  protected boolean staleDevices() {
    return response.getStatus() == 410;
  }

  protected boolean deviceLimitedExceeded() {
    return response.getStatus() == 411;
  }

  protected boolean rateLimit() {
    return response.getStatus() == 413 || response.getStatus() == 429;
  }

  protected boolean expectationFailed() {
    return response.getStatus() == 417;
  }

  protected boolean registrationLock() {
    return response.getStatus() == 423;
  }

  protected boolean proofRequired() {
    return response.getStatus() == 428;
  }

  protected boolean deprecatedVersion() {
    return response.getStatus() == 499;
  }

  protected boolean serverRejected() {
    return response.getStatus() == 508;
  }

  protected boolean notSuccessful() {
    return response.getStatus() != 200 && response.getStatus() != 202 && response.getStatus() != 204;
  }

  protected boolean genericIoError() {
    Throwable error = getError();

    if (error instanceof NonSuccessfulResponseCodeException) {
      return false;
    }

    return error instanceof IOException ||
           error instanceof TimeoutException ||
           error instanceof InterruptedException;
  }

  public static final class DefaultProcessor<T> extends ServiceResponseProcessor<T> {
    public DefaultProcessor(ServiceResponse<T> response) {
      super(response);
    }
  }
}
