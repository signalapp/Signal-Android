package org.whispersystems.signalservice.api.push.exceptions;

import org.signal.libsignal.protocol.logging.Log;
import org.whispersystems.signalservice.internal.push.ProofRequiredResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thrown when rate-limited by the server and proof of humanity is required to continue messaging.
 */
public class ProofRequiredException extends NonSuccessfulResponseCodeException {
  private static final String TAG = "ProofRequiredRateLimit";

  private final String      token;
  private final Set<Option> options;
  private final long        retryAfterSeconds;

  public ProofRequiredException(ProofRequiredResponse response, long retryAfterSeconds) {
    super(428);

    this.token             = response.getToken();
    this.options           = parseOptions(response.getOptions());
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public String getToken() {
    return token;
  }

  public Set<Option> getOptions() {
    return options;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }

  private static Set<Option> parseOptions(List<String> rawOptions) {
    Set<Option> options = new HashSet<>(rawOptions.size());

    for (String raw : rawOptions) {
      switch (raw) {
        case "captcha":
          options.add(Option.CAPTCHA);
          break;
        case "pushChallenge":
          options.add(Option.PUSH_CHALLENGE);
          break;
        default:
          Log.w(TAG, "Unrecognized challenge option: " + raw);
          break;
      }
    }

    return options;
  }

  public enum Option {
    CAPTCHA, PUSH_CHALLENGE
  }
}
