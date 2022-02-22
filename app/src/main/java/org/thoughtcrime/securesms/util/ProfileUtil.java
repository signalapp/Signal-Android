package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddress;
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddressProfileUtil;
import org.thoughtcrime.securesms.payments.PaymentsAddressException;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;

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
    return retrieveProfileSync(context, recipient, requestType, true);
  }

  @WorkerThread
  public static @NonNull ProfileAndCredential retrieveProfileSync(@NonNull Context context,
                                                                  @NonNull Recipient recipient,
                                                                  @NonNull SignalServiceProfile.RequestType requestType,
                                                                  boolean allowUnidentifiedAccess)
      throws IOException
  {
    ProfileService profileService = new ProfileService(ApplicationDependencies.getGroupsV2Operations().getProfileOperations(),
                                                       ApplicationDependencies.getSignalServiceMessageReceiver(),
                                                       ApplicationDependencies.getSignalWebSocket());

    Pair<Recipient, ServiceResponse<ProfileAndCredential>> response = retrieveProfile(context, recipient, requestType, profileService, allowUnidentifiedAccess).blockingGet();
    return new ProfileService.ProfileResponseProcessor(response.second()).getResultOrThrow();
  }

  public static Single<Pair<Recipient, ServiceResponse<ProfileAndCredential>>> retrieveProfile(@NonNull Context context,
                                                                                               @NonNull Recipient recipient,
                                                                                               @NonNull SignalServiceProfile.RequestType requestType,
                                                                                               @NonNull ProfileService profileService)
  {
    return retrieveProfile(context, recipient, requestType, profileService, true);
  }

  private static Single<Pair<Recipient, ServiceResponse<ProfileAndCredential>>> retrieveProfile(@NonNull Context context,
                                                                                                @NonNull Recipient recipient,
                                                                                                @NonNull SignalServiceProfile.RequestType requestType,
                                                                                                @NonNull ProfileService profileService,
                                                                                                boolean allowUnidentifiedAccess)
  {
    Optional<UnidentifiedAccess> unidentifiedAccess = allowUnidentifiedAccess ? getUnidentifiedAccess(context, recipient) : Optional.absent();
    Optional<ProfileKey>         profileKey         = ProfileKeyUtil.profileKeyOptional(recipient.getProfileKey());

    return Single.fromCallable(() -> toSignalServiceAddress(context, recipient))
                 .flatMap(address -> profileService.getProfile(address, profileKey, unidentifiedAccess, requestType, Locale.getDefault()).map(p -> new Pair<>(recipient, p)))
                 .onErrorReturn(t -> new Pair<>(recipient, ServiceResponse.forUnknownError(t)));
  }

  public static @Nullable String decryptString(@NonNull ProfileKey profileKey, @Nullable byte[] encryptedString)
      throws InvalidCiphertextException, IOException
  {
    if (encryptedString == null) {
      return null;
    }

    ProfileCipher profileCipher = new ProfileCipher(profileKey);
    return profileCipher.decryptString(encryptedString);
  }

  public static @Nullable String decryptString(@NonNull ProfileKey profileKey, @Nullable String encryptedStringBase64)
      throws InvalidCiphertextException, IOException
  {
    if (encryptedStringBase64 == null) {
      return null;
    }

    return decryptString(profileKey, Base64.decode(encryptedStringBase64));
  }

  @WorkerThread
  public static @NonNull MobileCoinPublicAddress getAddressForRecipient(@NonNull Recipient recipient)
      throws IOException, PaymentsAddressException
  {
    ProfileKey profileKey;
    try {
      profileKey = getProfileKey(recipient);
    } catch (IOException e) {
      Log.w(TAG, "Profile key not available for " + recipient.getId());
      throw new PaymentsAddressException(PaymentsAddressException.Code.NO_PROFILE_KEY);
    }
    ProfileAndCredential profileAndCredential     = ProfileUtil.retrieveProfileSync(ApplicationDependencies.getApplication(), recipient, SignalServiceProfile.RequestType.PROFILE);
    SignalServiceProfile profile                  = profileAndCredential.getProfile();
    byte[]               encryptedPaymentsAddress = profile.getPaymentAddress();

    if (encryptedPaymentsAddress == null) {
      Log.w(TAG, "Payments not enabled for " + recipient.getId());
      throw new PaymentsAddressException(PaymentsAddressException.Code.NOT_ENABLED);
    }

    try {
      IdentityKey                        identityKey             = new IdentityKey(Base64.decode(profileAndCredential.getProfile().getIdentityKey()), 0);
      ProfileCipher                      profileCipher           = new ProfileCipher(profileKey);
      byte[]                             decrypted               = profileCipher.decryptWithLength(encryptedPaymentsAddress);
      SignalServiceProtos.PaymentAddress paymentAddress          = SignalServiceProtos.PaymentAddress.parseFrom(decrypted);
      byte[]                             bytes                   = MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(paymentAddress, identityKey);
      MobileCoinPublicAddress            mobileCoinPublicAddress = MobileCoinPublicAddress.fromBytes(bytes);

      if (mobileCoinPublicAddress == null) {
        throw new PaymentsAddressException(PaymentsAddressException.Code.INVALID_ADDRESS);
      }

      return mobileCoinPublicAddress;
    } catch (InvalidCiphertextException | IOException e) {
      Log.w(TAG, "Could not decrypt payments address, ProfileKey may be outdated for " + recipient.getId(), e);
      throw new PaymentsAddressException(PaymentsAddressException.Code.COULD_NOT_DECRYPT);
    } catch (InvalidKeyException e) {
      Log.w(TAG, "Could not verify payments address due to bad identity key " + recipient.getId(), e);
      throw new PaymentsAddressException(PaymentsAddressException.Code.INVALID_ADDRESS_SIGNATURE);
    }
  }

  private static ProfileKey getProfileKey(@NonNull Recipient recipient) throws IOException {
    byte[] profileKeyBytes = recipient.getProfileKey();

    if (profileKeyBytes == null) {
      Log.w(TAG, "Profile key unknown for " + recipient.getId());
      throw new IOException("No profile key");
    }

    ProfileKey profileKey;
    try {
      profileKey = new ProfileKey(profileKeyBytes);
    } catch (InvalidInputException e) {
      Log.w(TAG, "Profile key invalid for " + recipient.getId());
      throw new IOException("Invalid profile key");
    }
    return profileKey;
  }

  /**
   * Uploads the profile based on all state that's written to disk, except we'll use the provided
   * list of badges instead. This is useful when you want to ensure that the profile has been uploaded
   * successfully before persisting the change to disk.
   */
  public static void uploadProfileWithBadges(@NonNull Context context, @NonNull List<Badge> badges) throws IOException {
    Log.d(TAG, "uploadProfileWithBadges()");
    try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
      uploadProfile(context,
                    Recipient.self().getProfileName(),
                    Optional.fromNullable(Recipient.self().getAbout()).or(""),
                    Optional.fromNullable(Recipient.self().getAboutEmoji()).or(""),
                    getSelfPaymentsAddressProtobuf(),
                    avatar,
                    badges);
    }
  }

  /**
   * Uploads the profile based on all state that's written to disk, except we'll use the provided
   * profile name instead. This is useful when you want to ensure that the profile has been uploaded
   * successfully before persisting the change to disk.
   */
  public static void uploadProfileWithName(@NonNull Context context, @NonNull ProfileName profileName) throws IOException {
    Log.d(TAG, "uploadProfileWithName()");
    try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
      uploadProfile(context,
                    profileName,
                    Optional.fromNullable(Recipient.self().getAbout()).or(""),
                    Optional.fromNullable(Recipient.self().getAboutEmoji()).or(""),
                    getSelfPaymentsAddressProtobuf(),
                    avatar,
                    Recipient.self().getBadges());
    }
  }

  /**
   * Uploads the profile based on all state that's written to disk, except we'll use the provided
   * about/emoji instead. This is useful when you want to ensure that the profile has been uploaded
   * successfully before persisting the change to disk.
   */
  public static void uploadProfileWithAbout(@NonNull Context context, @NonNull String about, @NonNull String emoji) throws IOException {
    Log.d(TAG, "uploadProfileWithAbout()");
    try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
      uploadProfile(context,
                    Recipient.self().getProfileName(),
                    about,
                    emoji,
                    getSelfPaymentsAddressProtobuf(),
                    avatar,
                    Recipient.self().getBadges());
    }
  }

  /**
   * Uploads the profile based on all state that's already written to disk.
   */
  public static void uploadProfile(@NonNull Context context) throws IOException {
    Log.d(TAG, "uploadProfile()");
    try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
      uploadProfileWithAvatar(context, avatar);
    }
  }

  /**
   * Uploads the profile based on all state that's written to disk, except we'll use the provided
   * avatar instead. This is useful when you want to ensure that the profile has been uploaded
   * successfully before persisting the change to disk.
   */
  public static void uploadProfileWithAvatar(@NonNull Context context, @Nullable StreamDetails avatar) throws IOException {
    Log.d(TAG, "uploadProfileWithAvatar()");
    uploadProfile(context,
                  Recipient.self().getProfileName(),
                  Optional.fromNullable(Recipient.self().getAbout()).or(""),
                  Optional.fromNullable(Recipient.self().getAboutEmoji()).or(""),
                  getSelfPaymentsAddressProtobuf(),
                  avatar,
                  Recipient.self().getBadges());
  }

  private static void uploadProfile(@NonNull Context context,
                                    @NonNull ProfileName profileName,
                                    @Nullable String about,
                                    @Nullable String aboutEmoji,
                                    @Nullable SignalServiceProtos.PaymentAddress paymentsAddress,
                                    @Nullable StreamDetails avatar,
                                    @NonNull List<Badge> badges)
      throws IOException
  {

    List<String> badgeIds = badges.stream()
                                  .filter(Badge::getVisible)
                                  .map(Badge::getId)
                                  .collect(Collectors.toList());

    Log.d(TAG, "Uploading " + (!profileName.isEmpty() ? "non-" : "") + "empty profile name.");
    Log.d(TAG, "Uploading " + (!Util.isEmpty(about) ? "non-" : "") + "empty about.");
    Log.d(TAG, "Uploading " + (!Util.isEmpty(aboutEmoji) ? "non-" : "") + "empty emoji.");
    Log.d(TAG, "Uploading " + (paymentsAddress != null ? "non-" : "") + "empty payments address.");
    Log.d(TAG, "Uploading " + (avatar != null && avatar.getLength() != 0 ? "non-" : "") + "empty avatar.");
    Log.d(TAG, "Uploading " + ((!badgeIds.isEmpty()) ? "non-" : "") + "empty badge list");

    ProfileKey                  profileKey     = ProfileKeyUtil.getSelfProfileKey();
    SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    String                      avatarPath     = accountManager.setVersionedProfile(SignalStore.account().requireAci(),
                                                                                    profileKey,
                                                                                    profileName.serialize(),
                                                                                    about,
                                                                                    aboutEmoji,
                                                                                    Optional.fromNullable(paymentsAddress),
                                                                                    avatar,
                                                                                    badgeIds).orNull();
    SignalStore.registrationValues().markHasUploadedProfile();
    SignalDatabase.recipients().setProfileAvatar(Recipient.self().getId(), avatarPath);
  }

  private static @Nullable SignalServiceProtos.PaymentAddress getSelfPaymentsAddressProtobuf() {
    if (!SignalStore.paymentsValues().mobileCoinPaymentsEnabled()) {
      return null;
    } else {
      IdentityKeyPair         identityKeyPair = SignalStore.account().getAciIdentityKey();
      MobileCoinPublicAddress publicAddress   = ApplicationDependencies.getPayments()
                                                                       .getWallet()
                                                                       .getMobileCoinPublicAddress();

      return MobileCoinPublicAddressProfileUtil.signPaymentsAddress(publicAddress.serialize(), identityKeyPair);
    }
  }

  private static Optional<UnidentifiedAccess> getUnidentifiedAccess(@NonNull Context context, @NonNull Recipient recipient) {
    Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient, false);

    if (unidentifiedAccess.isPresent()) {
      return unidentifiedAccess.get().getTargetUnidentifiedAccess();
    }

    return Optional.absent();
  }

  private static @NonNull SignalServiceAddress toSignalServiceAddress(@NonNull Context context, @NonNull Recipient recipient) throws IOException {
    if (recipient.getRegistered() == RecipientDatabase.RegisteredState.NOT_REGISTERED) {
      if (recipient.hasServiceId()) {
        return new SignalServiceAddress(recipient.requireServiceId(), recipient.getE164().orNull());
      } else {
        throw new IOException(recipient.getId() + " not registered!");
      }
    } else {
      return RecipientUtil.toSignalServiceAddress(context, recipient);
    }
  }
}
