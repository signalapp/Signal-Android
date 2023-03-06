package org.thoughtcrime.securesms.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.util.Base64UrlSafe;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class UsernameUtil {

  private static final String TAG = Log.tag(UsernameUtil.class);

  public static final int MIN_LENGTH = 3;
  public static final int MAX_LENGTH = 32;

  private static final Pattern FULL_PATTERN        = Pattern.compile(String.format(Locale.US, "^[a-zA-Z_][a-zA-Z0-9_]{%d,%d}$", MIN_LENGTH - 1, MAX_LENGTH - 1), Pattern.CASE_INSENSITIVE);
  private static final Pattern DIGIT_START_PATTERN = Pattern.compile("^[0-9].*$");

  public static boolean isValidUsernameForSearch(@Nullable String value) {
    return !TextUtils.isEmpty(value) && !DIGIT_START_PATTERN.matcher(value).matches();
  }

  public static Optional<InvalidReason> checkUsername(@Nullable String value) {
    if (value == null) {
      return Optional.of(InvalidReason.TOO_SHORT);
    } else if (value.length() < MIN_LENGTH) {
      return Optional.of(InvalidReason.TOO_SHORT);
    } else if (value.length() > MAX_LENGTH) {
      return Optional.of(InvalidReason.TOO_LONG);
    } else if (DIGIT_START_PATTERN.matcher(value).matches()) {
      return Optional.of(InvalidReason.STARTS_WITH_NUMBER);
    } else if (!FULL_PATTERN.matcher(value).matches()) {
      return Optional.of(InvalidReason.INVALID_CHARACTERS);
    } else {
      return Optional.empty();
    }
  }

  @WorkerThread
  public static @NonNull Optional<ServiceId> fetchAciForUsername(@NonNull String username) {
    Optional<RecipientId> localId = SignalDatabase.recipients().getByUsername(username);

    if (localId.isPresent()) {
      Recipient recipient = Recipient.resolved(localId.get());

      if (recipient.getServiceId().isPresent()) {
        Log.i(TAG, "Found username locally -- using associated UUID.");
        return recipient.getServiceId();
      } else {
        Log.w(TAG, "Found username locally, but it had no associated UUID! Clearing it.");
        SignalDatabase.recipients().clearUsernameIfExists(username);
      }
    }

    Log.d(TAG, "No local user with this username. Searching remotely.");
    try {
      return fetchAciForUsernameHash(Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(username)));
    } catch (BaseUsernameException e) {
      return Optional.empty();
    }
  }

  @WorkerThread
  public static @NonNull Optional<ServiceId> fetchAciForUsernameHash(@NonNull String base64UrlSafeEncodedUsernameHash) {
    try {
      ACI aci = ApplicationDependencies.getSignalServiceAccountManager()
                                       .getAciByUsernameHash(base64UrlSafeEncodedUsernameHash);
      return Optional.ofNullable(aci);
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  public enum InvalidReason {
    TOO_SHORT, TOO_LONG, INVALID_CHARACTERS, STARTS_WITH_NUMBER
  }
}
