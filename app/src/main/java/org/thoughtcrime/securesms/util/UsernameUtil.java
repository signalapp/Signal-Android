package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class UsernameUtil {

  private static final String TAG = Log.tag(UsernameUtil.class);

  public static final int MIN_LENGTH = 4;
  public static final int MAX_LENGTH = 26;

  private static final Pattern FULL_PATTERN        = Pattern.compile("^[a-z_][a-z0-9_]{3,25}$", Pattern.CASE_INSENSITIVE);
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
      return Optional.absent();
    }
  }

  @WorkerThread
  public static @NonNull Optional<UUID> fetchUuidForUsername(@NonNull Context context, @NonNull String username) {
    Optional<RecipientId> localId = DatabaseFactory.getRecipientDatabase(context).getByUsername(username);

    if (localId.isPresent()) {
      Recipient recipient = Recipient.resolved(localId.get());

      if (recipient.getUuid().isPresent()) {
        Log.i(TAG, "Found username locally -- using associated UUID.");
        return recipient.getUuid();
      } else {
        Log.w(TAG, "Found username locally, but it had no associated UUID! Clearing it.");
        DatabaseFactory.getRecipientDatabase(context).clearUsernameIfExists(username);
      }
    }

    try {
      Log.d(TAG, "No local user with this username. Searching remotely.");
      SignalServiceProfile profile = ApplicationDependencies.getSignalServiceMessageReceiver().retrieveProfileByUsername(username, Optional.absent());
      return Optional.fromNullable(profile.getUuid());
    } catch (IOException e) {
      return Optional.absent();
    }
  }

  public enum InvalidReason {
    TOO_SHORT, TOO_LONG, INVALID_CHARACTERS, STARTS_WITH_NUMBER
  }
}
