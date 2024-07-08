package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.Base64;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.util.Pair;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobs.GroupV2UpdateSelfProfileKeyJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddress;
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddressProfileUtil;
import org.thoughtcrime.securesms.payments.PaymentsAddressException;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.profiles.AvatarUploadParams;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.PaymentAddress;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;

/**
 * Aids in the retrieval and decryption of profiles.
 */
public final class ProfileUtil {

  private static final String TAG = Log.tag(ProfileUtil.class);

  private ProfileUtil() {
  }

  /**
   * Should be called after a change to our own profile key as been persisted to the database.
   */
  @WorkerThread
  public static void handleSelfProfileKeyChange() {
    List<Job> gv2UpdateJobs = SignalDatabase.groups()
                                            .getAllGroupV2Ids()
                                            .stream()
                                            .map(GroupV2UpdateSelfProfileKeyJob::withoutLimits)
                                            .collect(Collectors.toList());

    Log.w(TAG, "[handleSelfProfileKeyChange] Scheduling jobs, including " + gv2UpdateJobs.size() + " group update jobs.");

    AppDependencies.getJobManager()
                   .startChain(new RefreshAttributesJob())
                   .then(new ProfileUploadJob())
                   .then(new MultiDeviceProfileKeyUpdateJob())
                   .then(gv2UpdateJobs)
                   .enqueue();
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
    Pair<Recipient, ServiceResponse<ProfileAndCredential>> response = retrieveProfile(context, recipient, requestType, allowUnidentifiedAccess).blockingGet();
    return new ProfileService.ProfileResponseProcessor(response.second()).getResultOrThrow();
  }

  @WorkerThread
  public static @NonNull ProfileAndCredential retrieveProfileSync(@NonNull ServiceId.PNI pni,
                                                                  @NonNull SignalServiceProfile.RequestType requestType)
      throws IOException
  {
    ProfileService               profileService     = AppDependencies.getProfileService();

    ServiceResponse<ProfileAndCredential> response = Single
        .fromCallable(() -> new SignalServiceAddress(pni))
        .flatMap(address -> profileService.getProfile(address, Optional.empty(), SealedSenderAccess.NONE, requestType, Locale.getDefault()))
        .onErrorReturn(t -> ServiceResponse.forUnknownError(t))
        .blockingGet();

    return new ProfileService.ProfileResponseProcessor(response).getResultOrThrow();
  }

  public static Single<Pair<Recipient, ServiceResponse<ProfileAndCredential>>> retrieveProfile(@NonNull Context context,
                                                                                               @NonNull Recipient recipient,
                                                                                               @NonNull SignalServiceProfile.RequestType requestType)
  {
    return retrieveProfile(context, recipient, requestType, true);
  }

  private static Single<Pair<Recipient, ServiceResponse<ProfileAndCredential>>> retrieveProfile(@NonNull Context context,
                                                                                                @NonNull Recipient recipient,
                                                                                                @NonNull SignalServiceProfile.RequestType requestType,
                                                                                                boolean allowUnidentifiedAccess)
  {
    ProfileService       profileService     = AppDependencies.getProfileService();
    SealedSenderAccess   sealedSenderAccess = allowUnidentifiedAccess ? SealedSenderAccessUtil.getSealedSenderAccessFor(recipient, false) : SealedSenderAccess.NONE;
    Optional<ProfileKey> profileKey         = ProfileKeyUtil.profileKeyOptional(recipient.getProfileKey());

    return Single.fromCallable(() -> toSignalServiceAddress(context, recipient))
                 .flatMap(address -> profileService.getProfile(address, profileKey, sealedSenderAccess, requestType, Locale.getDefault()).map(p -> new Pair<>(recipient, p)))
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

  public static Optional<Boolean> decryptBoolean(@NonNull ProfileKey profileKey, @Nullable String encryptedBooleanBase64)
      throws InvalidCiphertextException, IOException
  {
    if (encryptedBooleanBase64 == null) {
      return Optional.empty();
    }

    ProfileCipher profileCipher = new ProfileCipher(profileKey);
    return profileCipher.decryptBoolean(Base64.decode(encryptedBooleanBase64));
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
    ProfileAndCredential profileAndCredential     = ProfileUtil.retrieveProfileSync(AppDependencies.getApplication(), recipient, SignalServiceProfile.RequestType.PROFILE);
    SignalServiceProfile profile                  = profileAndCredential.getProfile();
    byte[]               encryptedPaymentsAddress = profile.getPaymentAddress();

    if (encryptedPaymentsAddress == null) {
      Log.w(TAG, "Payments not enabled for " + recipient.getId());
      throw new PaymentsAddressException(PaymentsAddressException.Code.NOT_ENABLED);
    }

    try {
      IdentityKey             identityKey             = new IdentityKey(Base64.decode(profileAndCredential.getProfile().getIdentityKey()), 0);
      ProfileCipher           profileCipher           = new ProfileCipher(profileKey);
      byte[]                  decrypted               = profileCipher.decryptWithLength(encryptedPaymentsAddress);
      PaymentAddress          paymentAddress          = PaymentAddress.ADAPTER.decode(decrypted);
      byte[]                  bytes                   = MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(paymentAddress, identityKey);
      MobileCoinPublicAddress mobileCoinPublicAddress = MobileCoinPublicAddress.fromBytes(bytes);

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
    uploadProfile(Recipient.self().getProfileName(),
                  Optional.ofNullable(Recipient.self().getAbout()).orElse(""),
                  Optional.ofNullable(Recipient.self().getAboutEmoji()).orElse(""),
                  getSelfPaymentsAddressProtobuf(),
                  AvatarUploadParams.unchanged(AvatarHelper.hasAvatar(context, Recipient.self().getId())),
                  badges);
  }

  /**
   * Uploads the profile based on all state that's written to disk, except we'll use the provided
   * profile name instead. This is useful when you want to ensure that the profile has been uploaded
   * successfully before persisting the change to disk.
   */
  public static void uploadProfileWithName(@NonNull Context context, @NonNull ProfileName profileName) throws IOException {
    Log.d(TAG, "uploadProfileWithName()");
    try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
      uploadProfile(profileName,
                    Optional.ofNullable(Recipient.self().getAbout()).orElse(""),
                    Optional.ofNullable(Recipient.self().getAboutEmoji()).orElse(""),
                    getSelfPaymentsAddressProtobuf(),
                    AvatarUploadParams.unchanged(AvatarHelper.hasAvatar(context, Recipient.self().getId())),
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
      uploadProfile(Recipient.self().getProfileName(),
                    about,
                    emoji,
                    getSelfPaymentsAddressProtobuf(),
                    AvatarUploadParams.unchanged(AvatarHelper.hasAvatar(context, Recipient.self().getId())),
                    Recipient.self().getBadges());
    }
  }

  /**
   * Uploads the profile based on all state that's already written to disk.
   */
  public static void uploadProfile(@NonNull Context context) throws IOException {
    Log.d(TAG, "uploadProfile()");
    try (StreamDetails avatar = AvatarHelper.getSelfProfileAvatarStream(context)) {
      uploadProfileWithAvatar(avatar);
    }
  }

  /**
   * Uploads the profile based on all state that's written to disk, except we'll use the provided
   * avatar instead. This is useful when you want to ensure that the profile has been uploaded
   * successfully before persisting the change to disk.
   */
  public static void uploadProfileWithAvatar(@Nullable StreamDetails avatar) throws IOException {
    Log.d(TAG, "uploadProfileWithAvatar()");
    uploadProfile(Recipient.self().getProfileName(),
                  Optional.ofNullable(Recipient.self().getAbout()).orElse(""),
                  Optional.ofNullable(Recipient.self().getAboutEmoji()).orElse(""),
                  getSelfPaymentsAddressProtobuf(),
                  AvatarUploadParams.forAvatar(avatar),
                  Recipient.self().getBadges());
  }

  /**
   * Attempts to update just the expiring profile key credential with a new one. If unable, an empty optional is returned.
   *
   * Note: It will try to find missing profile key credentials from the server and persist locally.
   */
  public static Optional<ExpiringProfileKeyCredential> updateExpiringProfileKeyCredential(@NonNull Recipient recipient) throws IOException {
    ProfileKey profileKey = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());

    if (profileKey != null) {
      Log.i(TAG, String.format("Updating profile key credential on recipient %s, fetching", recipient.getId()));

      Optional<ExpiringProfileKeyCredential> profileKeyCredentialOptional = AppDependencies.getSignalServiceAccountManager()
                                                                                           .resolveProfileKeyCredential(recipient.requireAci(), profileKey, Locale.getDefault());

      if (profileKeyCredentialOptional.isPresent()) {
        boolean updatedProfileKey = SignalDatabase.recipients().setProfileKeyCredential(recipient.getId(), profileKey, profileKeyCredentialOptional.get());

        if (!updatedProfileKey) {
          Log.w(TAG, String.format("Failed to update the profile key credential on recipient %s", recipient.getId()));
        } else {
          Log.i(TAG, String.format("Got new profile key credential for recipient %s", recipient.getId()));
          return profileKeyCredentialOptional;
        }
      }
    }

    return Optional.empty();
  }

  private static void uploadProfile(@NonNull ProfileName profileName,
                                    @Nullable String about,
                                    @Nullable String aboutEmoji,
                                    @Nullable PaymentAddress paymentsAddress,
                                    @NonNull AvatarUploadParams avatar,
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
    Log.d(TAG, "Uploading " + ((!badgeIds.isEmpty()) ? "non-" : "") + "empty badge list.");

    if (avatar.keepTheSame) {
      Log.d(TAG, "Leaving avatar unchanged. We think we " + (avatar.hasAvatar ? "" : "do not ") + "have one.");
    } else {
      Log.d(TAG, "Uploading " + (avatar.stream != null && avatar.stream.getLength() != 0 ? "non-" : "") + "empty avatar.");
    }

    ProfileKey                  profileKey     = ProfileKeyUtil.getSelfProfileKey();
    SignalServiceAccountManager accountManager = AppDependencies.getSignalServiceAccountManager();
    String                      avatarPath     = accountManager.setVersionedProfile(SignalStore.account().requireAci(),
                                                                                    profileKey,
                                                                                    profileName.serialize(),
                                                                                    about,
                                                                                    aboutEmoji,
                                                                                    Optional.ofNullable(paymentsAddress),
                                                                                    avatar,
                                                                                    badgeIds,
                                                                                    SignalStore.phoneNumberPrivacy().isPhoneNumberSharingEnabled()).orElse(null);
    SignalStore.registration().markHasUploadedProfile();
    if (!avatar.keepTheSame) {
      SignalDatabase.recipients().setProfileAvatar(Recipient.self().getId(), avatarPath);
    }
    AppDependencies.getJobManager().add(new RefreshOwnProfileJob());
  }

  private static @Nullable PaymentAddress getSelfPaymentsAddressProtobuf() {
    if (!SignalStore.payments().mobileCoinPaymentsEnabled()) {
      return null;
    } else {
      IdentityKeyPair         identityKeyPair = SignalStore.account().getAciIdentityKey();
      MobileCoinPublicAddress publicAddress   = AppDependencies.getPayments()
                                                               .getWallet()
                                                               .getMobileCoinPublicAddress();

      return MobileCoinPublicAddressProfileUtil.signPaymentsAddress(publicAddress.serialize(), identityKeyPair);
    }
  }

  private static @NonNull SignalServiceAddress toSignalServiceAddress(@NonNull Context context, @NonNull Recipient recipient) throws IOException {
    if (recipient.getRegistered() == RecipientTable.RegisteredState.NOT_REGISTERED) {
      if (recipient.getHasServiceId()) {
        return new SignalServiceAddress(recipient.requireServiceId(), recipient.getE164().orElse(null));
      } else {
        throw new IOException(recipient.getId() + " not registered!");
      }
    } else {
      return RecipientUtil.toSignalServiceAddress(context, recipient);
    }
  }
}
