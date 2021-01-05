package org.thoughtcrime.securesms.crypto;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Locale;

public final class ProfileKeyUtil {

  private static final String TAG = Log.tag(ProfileKeyUtil.class);

  private ProfileKeyUtil() {
  }

  /** @deprecated Use strongly typed {@link org.signal.zkgroup.profiles.ProfileKey}
   * from {@link #getSelfProfileKey()}
   * or {@code getSelfProfileKey().serialize()} if you need the bytes. */
  @Deprecated
  public static @NonNull byte[] getProfileKey(@NonNull Context context) {
    byte[] profileKey = Recipient.self().getProfileKey();
    if (profileKey == null) {
      throw new AssertionError();
    }
    return profileKey;
  }

  public static synchronized @NonNull ProfileKey getSelfProfileKey() {
    try {
      return new ProfileKey(Recipient.self().getProfileKey());
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  public static @Nullable ProfileKey profileKeyOrNull(@Nullable byte[] profileKey) {
    if (profileKey != null) {
      try {
        return new ProfileKey(profileKey);
      } catch (InvalidInputException e) {
        Log.w(TAG, String.format(Locale.US, "Seen non-null profile key of wrong length %d", profileKey.length), e);
      }
    }

    return null;
  }

  public static @Nullable ProfileKeyCredential profileKeyCredentialOrNull(@Nullable byte[] profileKeyCredential) {
    if (profileKeyCredential != null) {
      try {
        return new ProfileKeyCredential(profileKeyCredential);
      } catch (InvalidInputException e) {
        Log.w(TAG, String.format(Locale.US, "Seen non-null profile key credential of wrong length %d", profileKeyCredential.length), e);
      }
    }

    return null;
  }

  public static @NonNull ProfileKey profileKeyOrThrow(@NonNull byte[] profileKey) {
    try {
      return new ProfileKey(profileKey);
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  public static @NonNull Optional<ProfileKey> profileKeyOptional(@Nullable byte[] profileKey) {
    return Optional.fromNullable(profileKeyOrNull(profileKey));
  }

  public static @NonNull Optional<ProfileKey> profileKeyOptionalOrThrow(@NonNull byte[] profileKey) {
    return Optional.of(profileKeyOrThrow(profileKey));
  }

  public static @NonNull ProfileKey createNew() {
    try {
      return new ProfileKey(Util.getSecretBytes(32));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }
}
