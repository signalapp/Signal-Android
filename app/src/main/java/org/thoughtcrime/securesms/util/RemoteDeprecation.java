package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Stream;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;

import java.io.IOException;
import java.util.Objects;

public final class RemoteDeprecation {

  private static final String TAG = Log.tag(RemoteDeprecation.class);

  private RemoteDeprecation() { }

  /**
   * @return The amount of time (in milliseconds) until this client version expires, or -1 if
   *         there's no pending expiration.
   */
  public static long getTimeUntilDeprecation(long currentTime) {
    return getTimeUntilDeprecation(RemoteConfig.clientExpiration(), currentTime, BuildConfig.VERSION_NAME);
  }

  /**
   * @return The amount of time (in milliseconds) until this client version expires, or -1 if
   *         there's no pending expiration.
   */
  public static long getTimeUntilDeprecation() {
    return getTimeUntilDeprecation(System.currentTimeMillis());
  }

  /**
   * @return The amount of time (in milliseconds) until this client version expires, or -1 if
   *         there's no pending expiration.
   */
  @VisibleForTesting
  static long getTimeUntilDeprecation(String json, long currentTime, @NonNull String currentVersion) {
    if (Util.isEmpty(json)) {
      return -1;
    }

    try {
      SemanticVersion    ourVersion  = Objects.requireNonNull(SemanticVersion.parse(currentVersion));
      ClientExpiration[] expirations = JsonUtils.fromJson(json, ClientExpiration[].class);

      ClientExpiration expiration = Stream.of(expirations)
                                          .filter(c -> c.getVersion() != null && c.getExpiration() != -1)
                                          .filter(c -> c.requireVersion().compareTo(ourVersion) > 0)
                                          .sortBy(ClientExpiration::getExpiration)
                                          .findFirst()
                                          .orElse(null);

      if (expiration != null) {
        return Math.max(expiration.getExpiration() - currentTime, 0);
      }
    } catch (IOException e) {
      Log.w(TAG, e);
    }

    return -1;
  }

  private static final class ClientExpiration {
    @JsonProperty
    private final String minVersion;

    @JsonProperty
    private final String iso8601;

    ClientExpiration(@Nullable @JsonProperty("minVersion") String minVersion,
                     @Nullable @JsonProperty("iso8601") String iso8601)
    {
      this.minVersion = minVersion;
      this.iso8601    = iso8601;
    }

    public @Nullable SemanticVersion getVersion() {
      return SemanticVersion.parse(minVersion);
    }

    public @NonNull SemanticVersion requireVersion() {
      return Objects.requireNonNull(getVersion());
    }

    public long getExpiration() {
      return DateUtils.parseIso8601(iso8601);
    }
  }

}
