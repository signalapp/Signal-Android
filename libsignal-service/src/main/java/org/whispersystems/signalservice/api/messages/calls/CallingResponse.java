package org.whispersystems.signalservice.api.messages.calls;

/**
 * Encapsulate the response to an http request on behalf of ringrtc.
 */
public abstract class CallingResponse {
  private final long requestId;

  CallingResponse(long requestId) {
    this.requestId = requestId;
  }

  public long getRequestId() {
    return requestId;
  }

  public static class Success extends CallingResponse {
    private final int    responseStatus;
    private final byte[] responseBody;

    public Success(long requestId, int responseStatus, byte[] responseBody) {
      super(requestId);
      this.responseStatus = responseStatus;
      this.responseBody   = responseBody;
    }

    public int getResponseStatus() {
      return responseStatus;
    }

    public byte[] getResponseBody() {
      return responseBody;
    }
  }

  public static class Error extends CallingResponse {
    private final Throwable throwable;

    public Error(long requestId, Throwable throwable) {
      super(requestId);
      this.throwable = throwable;
    }

    public Throwable getThrowable() {
      return throwable;
    }
  }
}
