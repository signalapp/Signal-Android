package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.messages.IncomingMessageObserver;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.util.concurrent.CascadingFuture;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Aids in the retrieval and decryption of profiles.
 */
public final class ProfileUtil {

  private static final String TAG = Log.tag(ProfileUtil.class);

  private ProfileUtil() {
  }

  @WorkerThread
  public static @NonNull ProfileAndCredential retrieveProfileSync(@NonNull Context context,
                                                                  @NonNull Recipient recipient,
                                                                  @NonNull SignalServiceProfile.RequestType requestType)
      throws IOException
  {
    try {
      return retrieveProfile(context, recipient, requestType).get(10, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof PushNetworkException) {
        throw (PushNetworkException) e.getCause();
      } else if (e.getCause() instanceof NotFoundException) {
        throw (NotFoundException) e.getCause();
      } else {
        throw new IOException(e);
      }
    } catch (InterruptedException | TimeoutException e) {
      throw new PushNetworkException(e);
    }
  }

  public static @NonNull ListenableFuture<ProfileAndCredential> retrieveProfile(@NonNull Context context,
                                                                                @NonNull Recipient recipient,
                                                                                @NonNull SignalServiceProfile.RequestType requestType)
  {
    SignalServiceAddress         address            = toSignalServiceAddress(context, recipient);
    Optional<UnidentifiedAccess> unidentifiedAccess = getUnidentifiedAccess(context, recipient);
    Optional<ProfileKey>         profileKey         = ProfileKeyUtil.profileKeyOptional(recipient.getProfileKey());

    if (unidentifiedAccess.isPresent()) {
      return new CascadingFuture<>(Arrays.asList(() -> getPipeRetrievalFuture(address, profileKey, unidentifiedAccess, requestType),
                                                 () -> getSocketRetrievalFuture(address, profileKey, unidentifiedAccess, requestType),
                                                 () -> getPipeRetrievalFuture(address, profileKey, Optional.absent(), requestType),
                                                 () -> getSocketRetrievalFuture(address, profileKey, Optional.absent(), requestType)),
                                   e -> !(e instanceof NotFoundException));
    } else {
      return new CascadingFuture<>(Arrays.asList(() -> getPipeRetrievalFuture(address, profileKey, Optional.absent(), requestType),
                                                 () -> getSocketRetrievalFuture(address, profileKey, Optional.absent(), requestType)),
                                   e -> !(e instanceof NotFoundException));
    }
  }

  public static @Nullable String decryptName(@NonNull ProfileKey profileKey, @Nullable String encryptedName)
      throws InvalidCiphertextException, IOException
  {
    if (encryptedName == null) {
      return null;
    }

    ProfileCipher profileCipher = new ProfileCipher(profileKey);
    return new String(profileCipher.decryptName(Base64.decode(encryptedName)));
  }

  /**
   * Uploads the profile based on all state that's written to disk, except we'll use the provided
   * profile name instead. This is useful when you want to ensure that the profile has been uploaded
   * successfully before persisting the change to disk.
   */
  public static void uploadProfileWithName(@NonNull Context context, @NonNull ProfileName profileName) throws IOException {
    try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
      uploadProfile(context,
                    profileName,
                    Optional.fromNullable(Recipient.self().getAbout()).or(""),
                    Optional.fromNullable(Recipient.self().getAboutEmoji()).or(""),
                    avatar);
    }
  }

  /**
   * Uploads the profile based on all state that's written to disk, except we'll use the provided
   * about/emoji instead. This is useful when you want to ensure that the profile has been uploaded
   * successfully before persisting the change to disk.
   */
  public static void uploadProfileWithAbout(@NonNull Context context, @NonNull String about, @NonNull String emoji) throws IOException {
    try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
      uploadProfile(context,
                    Recipient.self().getProfileName(),
                    about,
                    emoji,
                    avatar);
    }
  }

  /**
   * Uploads the profile based on all state that's written to disk, except we'll use the provided
   * avatar instead. This is useful when you want to ensure that the profile has been uploaded
   * successfully before persisting the change to disk.
   */
  public static void uploadProfileWithAvatar(@NonNull Context context, @Nullable StreamDetails avatar) throws IOException {
      uploadProfile(context,
                    Recipient.self().getProfileName(),
                    Optional.fromNullable(Recipient.self().getAbout()).or(""),
                    Optional.fromNullable(Recipient.self().getAboutEmoji()).or(""),
                    avatar);
  }

  /**
   * Uploads the profile based on all state that's already written to disk.
   */
  public static void uploadProfile(@NonNull Context context) throws IOException {
    try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
      uploadProfile(context,
                    Recipient.self().getProfileName(),
                    Optional.fromNullable(Recipient.self().getAbout()).or(""),
                    Optional.fromNullable(Recipient.self().getAboutEmoji()).or(""),
                    avatar);
    }
  }

  private static void uploadProfile(@NonNull Context context,
                                    @NonNull ProfileName profileName,
                                    @Nullable String about,
                                    @Nullable String aboutEmoji,
                                    @Nullable StreamDetails avatar)
      throws IOException
  {
    Log.d(TAG, "Uploading " + (!Util.isEmpty(about) ? "non-" : "") + "empty about.");
    Log.d(TAG, "Uploading " + (!Util.isEmpty(aboutEmoji) ? "non-" : "") + "empty emoji.");

    ProfileKey                  profileKey     = ProfileKeyUtil.getSelfProfileKey();
    SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    String                      avatarPath     = accountManager.setVersionedProfile(Recipient.self().getUuid().get(), profileKey, profileName.serialize(), about, aboutEmoji, avatar).orNull();

    DatabaseFactory.getRecipientDatabase(context).setProfileAvatar(Recipient.self().getId(), avatarPath);
  }

  private static @NonNull ListenableFuture<ProfileAndCredential> getPipeRetrievalFuture(@NonNull SignalServiceAddress address,
                                                                                        @NonNull Optional<ProfileKey> profileKey,
                                                                                        @NonNull Optional<UnidentifiedAccess> unidentifiedAccess,
                                                                                        @NonNull SignalServiceProfile.RequestType requestType)
      throws IOException
  {
    SignalServiceMessagePipe authPipe         = IncomingMessageObserver.getPipe();
    SignalServiceMessagePipe unidentifiedPipe = IncomingMessageObserver.getUnidentifiedPipe();
    SignalServiceMessagePipe pipe             = unidentifiedPipe != null && unidentifiedAccess.isPresent() ? unidentifiedPipe
                                                                                                           : authPipe;
    if (pipe != null) {
      return pipe.getProfile(address, profileKey, unidentifiedAccess, requestType);
    }

    throw new IOException("No pipe available!");
  }

  private static @NonNull ListenableFuture<ProfileAndCredential> getSocketRetrievalFuture(@NonNull SignalServiceAddress address,
                                                                                          @NonNull Optional<ProfileKey> profileKey,
                                                                                          @NonNull Optional<UnidentifiedAccess> unidentifiedAccess,
                                                                                          @NonNull SignalServiceProfile.RequestType requestType)
  {
    SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
    return receiver.retrieveProfile(address, profileKey, unidentifiedAccess, requestType);
  }

  private static Optional<UnidentifiedAccess> getUnidentifiedAccess(@NonNull Context context, @NonNull Recipient recipient) {
    Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient, false);

    if (unidentifiedAccess.isPresent()) {
      return unidentifiedAccess.get().getTargetUnidentifiedAccess();
    }

    return Optional.absent();
  }

  private static @NonNull SignalServiceAddress toSignalServiceAddress(@NonNull Context context, @NonNull Recipient recipient) {
    if (recipient.getRegistered() == RecipientDatabase.RegisteredState.NOT_REGISTERED) {
      return new SignalServiceAddress(recipient.getUuid().orNull(), recipient.getE164().orNull());
    } else {
      return RecipientUtil.toSignalServiceAddressBestEffort(context, recipient);
    }
  }
}
