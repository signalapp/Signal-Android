package org.thoughtcrime.securesms.jobs;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.badges.BadgeRepository;
import org.thoughtcrime.securesms.badges.Badges;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.subscription.Subscriber;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription;
import org.whispersystems.signalservice.api.util.ExpiringProfileCredentialUtil;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.ReserveUsernameResponse;
import org.whispersystems.signalservice.internal.push.WhoAmIResponse;
import org.whispersystems.util.Base64UrlSafe;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Refreshes the profile of the local user. Different from {@link RetrieveProfileJob} in that we
 * have to sometimes look at/set different data stores, and we will *always* do the fetch regardless
 * of caching.
 */
public class RefreshOwnProfileJob extends BaseJob {

  public static final String KEY = "RefreshOwnProfileJob";

  private static final String TAG = Log.tag(RefreshOwnProfileJob.class);

  private static final String SUBSCRIPTION_QUEUE = ProfileUploadJob.QUEUE + "_Subscription";
  private static final String BOOST_QUEUE        = ProfileUploadJob.QUEUE + "_Boost";

  public RefreshOwnProfileJob() {
    this(ProfileUploadJob.QUEUE);
  }

  private RefreshOwnProfileJob(@NonNull String queue) {
    this(new Parameters.Builder()
             .addConstraint(NetworkConstraint.KEY)
             .setQueue(queue)
             .setMaxInstancesForFactory(1)
             .setMaxAttempts(10)
             .build());
  }

  public static @NonNull RefreshOwnProfileJob forSubscription() {
    return new RefreshOwnProfileJob(SUBSCRIPTION_QUEUE);
  }

  public static @NonNull RefreshOwnProfileJob forBoost() {
    return new RefreshOwnProfileJob(BOOST_QUEUE);
  }


  private RefreshOwnProfileJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!SignalStore.account().isRegistered() || TextUtils.isEmpty(SignalStore.account().getE164())) {
      Log.w(TAG, "Not yet registered!");
      return;
    }

    if (SignalStore.kbsValues().hasPin() && !SignalStore.kbsValues().hasOptedOut() && SignalStore.storageService().getLastSyncTime() == 0) {
      Log.i(TAG, "Registered with PIN but haven't completed storage sync yet.");
      return;
    }

    if (!SignalStore.registrationValues().hasUploadedProfile() && SignalStore.account().isPrimaryDevice()) {
      Log.i(TAG, "Registered but haven't uploaded profile yet.");
      return;
    }

    Recipient            self                 = Recipient.self();
    ProfileAndCredential profileAndCredential = ProfileUtil.retrieveProfileSync(context, self, getRequestType(self), false);
    SignalServiceProfile profile              = profileAndCredential.getProfile();

    if (Util.isEmpty(profile.getName()) &&
        Util.isEmpty(profile.getAvatar()) &&
        Util.isEmpty(profile.getAbout()) &&
        Util.isEmpty(profile.getAboutEmoji()))
    {
      Log.w(TAG, "The profile we retrieved was empty! Ignoring it.");

      if (!self.getProfileName().isEmpty()) {
        Log.w(TAG, "We have a name locally. Scheduling a profile upload.");
        ApplicationDependencies.getJobManager().add(new ProfileUploadJob());
      } else {
        Log.w(TAG, "We don't have a name locally, either!");
      }

      return;
    }

    setProfileName(profile.getName());
    setProfileAbout(profile.getAbout(), profile.getAboutEmoji());
    setProfileAvatar(profile.getAvatar());
    setProfileCapabilities(profile.getCapabilities());
    setProfileBadges(profile.getBadges());
    ensureUnidentifiedAccessCorrect(profile.getUnidentifiedAccess(), profile.isUnrestrictedUnidentifiedAccess());

    profileAndCredential.getExpiringProfileKeyCredential()
                        .ifPresent(expiringProfileKeyCredential -> setExpiringProfileKeyCredential(self, ProfileKeyUtil.getSelfProfileKey(), expiringProfileKeyCredential));

    StoryOnboardingDownloadJob.Companion.enqueueIfNeeded();

    if (FeatureFlags.usernames()) {
      checkUsernameIsInSync();
    }
  }

  private void setExpiringProfileKeyCredential(@NonNull Recipient recipient,
                                               @NonNull ProfileKey recipientProfileKey,
                                               @NonNull ExpiringProfileKeyCredential credential)
  {
    RecipientTable recipientTable = SignalDatabase.recipients();
    recipientTable.setProfileKeyCredential(recipient.getId(), recipientProfileKey, credential);
  }

  private static SignalServiceProfile.RequestType getRequestType(@NonNull Recipient recipient) {
    return ExpiringProfileCredentialUtil.isValid(recipient.getExpiringProfileKeyCredential()) ? SignalServiceProfile.RequestType.PROFILE
                                                                                              : SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL;
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() { }

  private void setProfileName(@Nullable String encryptedName) {
    try {
      ProfileKey  profileKey    = ProfileKeyUtil.getSelfProfileKey();
      String      plaintextName = ProfileUtil.decryptString(profileKey, encryptedName);
      ProfileName profileName   = ProfileName.fromSerialized(plaintextName);

      if (!profileName.isEmpty()) {
        Log.d(TAG, "Saving non-empty name.");
        SignalDatabase.recipients().setProfileName(Recipient.self().getId(), profileName);
      } else {
        Log.w(TAG, "Ignoring empty name.");
      }

    } catch (InvalidCiphertextException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private void setProfileAbout(@Nullable String encryptedAbout, @Nullable String encryptedEmoji) {
    try {
      ProfileKey  profileKey     = ProfileKeyUtil.getSelfProfileKey();
      String      plaintextAbout = ProfileUtil.decryptString(profileKey, encryptedAbout);
      String      plaintextEmoji = ProfileUtil.decryptString(profileKey, encryptedEmoji);

      Log.d(TAG, "Saving " + (!Util.isEmpty(plaintextAbout) ? "non-" : "") + "empty about.");
      Log.d(TAG, "Saving " + (!Util.isEmpty(plaintextEmoji) ? "non-" : "") + "empty emoji.");

      SignalDatabase.recipients().setAbout(Recipient.self().getId(), plaintextAbout, plaintextEmoji);
    } catch (InvalidCiphertextException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private static void setProfileAvatar(@Nullable String avatar) {
    Log.d(TAG, "Saving " + (!Util.isEmpty(avatar) ? "non-" : "") + "empty avatar.");
    ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(Recipient.self(), avatar));
  }

  private void setProfileCapabilities(@Nullable SignalServiceProfile.Capabilities capabilities) {
    if (capabilities == null) {
      return;
    }

    SignalDatabase.recipients().setCapabilities(Recipient.self().getId(), capabilities);
  }

  private void ensureUnidentifiedAccessCorrect(@Nullable String unidentifiedAccessVerifier, boolean universalUnidentifiedAccess) {
    if (unidentifiedAccessVerifier == null) {
      Log.w(TAG, "No unidentified access is set remotely! Refreshing attributes.");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
      return;
    }

    if (TextSecurePreferences.isUniversalUnidentifiedAccess(context) != universalUnidentifiedAccess) {
      Log.w(TAG, "The universal access flag doesn't match our local value (local: " + TextSecurePreferences.isUniversalUnidentifiedAccess(context) + ", remote: " + universalUnidentifiedAccess + ")! Refreshing attributes.");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
      return;
    }

    ProfileKey    profileKey = ProfileKeyUtil.getSelfProfileKey();
    ProfileCipher cipher     = new ProfileCipher(profileKey);

    boolean verified;
    try {
      verified = cipher.verifyUnidentifiedAccess(Base64.decode(unidentifiedAccessVerifier));
    } catch (IOException e) {
      Log.w(TAG, "Failed to decode unidentified access!", e);
      verified = false;
    }

    if (!verified) {
      Log.w(TAG, "Unidentified access failed to verify! Refreshing attributes.");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
    }
  }

  static void checkUsernameIsInSync() throws IOException {
    if (TextUtils.isEmpty(SignalDatabase.recipients().getUsername(Recipient.self().getId()))) {
      Log.i(TAG, "No local username. Clearing username from server.");
      ApplicationDependencies.getSignalServiceAccountManager().deleteUsername();
    } else {
      Log.i(TAG, "Local user has a username, attempting username synchronization.");
      performLocalRemoteComparison();
    }
  }

  private static void performLocalRemoteComparison() {
    try {
      String  localUsername    = SignalDatabase.recipients().getUsername(Recipient.self().getId());
      boolean hasLocalUsername = !TextUtils.isEmpty(localUsername);

      if (!hasLocalUsername) {
        return;
      }

      WhoAmIResponse whoAmIResponse     = ApplicationDependencies.getSignalServiceAccountManager().getWhoAmI();
      boolean        hasServerUsername  = !TextUtils.isEmpty(whoAmIResponse.getUsernameHash());
      String         serverUsernameHash = whoAmIResponse.getUsernameHash();
      String         localUsernameHash  = Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(localUsername));

      if (!hasServerUsername) {
        Log.w(TAG, "No remote username is set.");
      }

      if (!Objects.equals(localUsernameHash, serverUsernameHash)) {
        Log.w(TAG, "Local username hash does not match server username hash.");
      }

      if (!hasServerUsername || !Objects.equals(localUsernameHash, serverUsernameHash)) {
        Log.i(TAG, "Attempting to resynchronize username.");
        tryToReserveAndConfirmLocalUsername(localUsername, localUsernameHash);
      }
    } catch (IOException | BaseUsernameException e) {
      Log.w(TAG, "Failed perform synchronization check", e);
    }
  }

  private static void tryToReserveAndConfirmLocalUsername(@NonNull String localUsername, @NonNull String localUsernameHash) {
    try {
      ReserveUsernameResponse response = ApplicationDependencies.getSignalServiceAccountManager()
                                                                .reserveUsername(Collections.singletonList(localUsernameHash));

      ApplicationDependencies.getSignalServiceAccountManager()
                             .confirmUsername(localUsername, response);
    } catch (IOException e) {
      Log.d(TAG, "Failed to synchronize username.", e);
      SignalStore.phoneNumberPrivacy().markUsernameOutOfSync();
    }
  }

  private void setProfileBadges(@Nullable List<SignalServiceProfile.Badge> badges) throws IOException {
    if (badges == null) {
      return;
    }

    Set<String> localDonorBadgeIds  = Recipient.self()
                                               .getBadges()
                                               .stream()
                                               .filter(badge -> badge.getCategory() == Badge.Category.Donor)
                                               .map(Badge::getId)
                                               .collect(Collectors.toSet());

    Set<String> remoteDonorBadgeIds = badges.stream()
                                            .filter(badge -> Objects.equals(badge.getCategory(), Badge.Category.Donor.getCode()))
                                            .map(SignalServiceProfile.Badge::getId)
                                            .collect(Collectors.toSet());

    boolean remoteHasSubscriptionBadges = remoteDonorBadgeIds.stream().anyMatch(RefreshOwnProfileJob::isSubscription);
    boolean localHasSubscriptionBadges  = localDonorBadgeIds.stream().anyMatch(RefreshOwnProfileJob::isSubscription);
    boolean remoteHasBoostBadges        = remoteDonorBadgeIds.stream().anyMatch(RefreshOwnProfileJob::isBoost);
    boolean localHasBoostBadges         = localDonorBadgeIds.stream().anyMatch(RefreshOwnProfileJob::isBoost);
    boolean remoteHasGiftBadges         = remoteDonorBadgeIds.stream().anyMatch(RefreshOwnProfileJob::isGift);
    boolean localHasGiftBadges          = localDonorBadgeIds.stream().anyMatch(RefreshOwnProfileJob::isGift);

    if (!remoteHasSubscriptionBadges && localHasSubscriptionBadges) {
      Badge mostRecentExpiration = Recipient.self()
                                            .getBadges()
                                            .stream()
                                            .filter(badge -> badge.getCategory() == Badge.Category.Donor)
                                            .filter(badge -> isSubscription(badge.getId()))
                                            .max(Comparator.comparingLong(Badge::getExpirationTimestamp))
                                            .get();

      Log.d(TAG, "Marking subscription badge as expired, should notify next time the conversation list is open.", true);
      SignalStore.donationsValues().setExpiredBadge(mostRecentExpiration);

      if (!SignalStore.donationsValues().isUserManuallyCancelled()) {
        Log.d(TAG, "Detected an unexpected subscription expiry.", true);
        Subscriber subscriber = SignalStore.donationsValues().getSubscriber();

        boolean isDueToPaymentFailure = false;
        if (subscriber != null) {
          ServiceResponse<ActiveSubscription> response = ApplicationDependencies.getDonationsService()
                                                                                .getSubscription(subscriber.getSubscriberId());

          if (response.getResult().isPresent()) {
            ActiveSubscription activeSubscription = response.getResult().get();
            if (activeSubscription.isFailedPayment()) {
              Log.d(TAG, "Unexpected expiry due to payment failure.", true);
              isDueToPaymentFailure = true;
            }

            if (activeSubscription.getChargeFailure() != null) {
              Log.d(TAG, "Active payment contains a charge failure: " + activeSubscription.getChargeFailure().getCode(), true);
            }
          }
        }

        if (!isDueToPaymentFailure) {
          Log.d(TAG, "Unexpected expiry due to inactivity.", true);
        }

        MultiDeviceSubscriptionSyncRequestJob.enqueue();
        SignalStore.donationsValues().setShouldCancelSubscriptionBeforeNextSubscribeAttempt(true);
      }
    } else if (!remoteHasBoostBadges && localHasBoostBadges) {
      Badge mostRecentExpiration = Recipient.self()
                                            .getBadges()
                                            .stream()
                                            .filter(badge -> badge.getCategory() == Badge.Category.Donor)
                                            .filter(badge -> isBoost(badge.getId()))
                                            .max(Comparator.comparingLong(Badge::getExpirationTimestamp))
                                            .get();

      Log.d(TAG, "Marking boost badge as expired, should notify next time the conversation list is open.", true);
      SignalStore.donationsValues().setExpiredBadge(mostRecentExpiration);
    } else {
      Badge badge = SignalStore.donationsValues().getExpiredBadge();

      if (badge != null && badge.isSubscription() && remoteHasSubscriptionBadges) {
        Log.d(TAG, "Remote has subscription badges. Clearing local expired subscription badge.", true);
        SignalStore.donationsValues().setExpiredBadge(null);
      } else if (badge != null && badge.isBoost() && remoteHasBoostBadges) {
        Log.d(TAG, "Remote has boost badges. Clearing local expired boost badge.", true);
        SignalStore.donationsValues().setExpiredBadge(null);
      }
    }

    if (!remoteHasGiftBadges && localHasGiftBadges) {
      Badge mostRecentExpiration = Recipient.self()
                                            .getBadges()
                                            .stream()
                                            .filter(badge -> badge.getCategory() == Badge.Category.Donor)
                                            .filter(badge -> isGift(badge.getId()))
                                            .max(Comparator.comparingLong(Badge::getExpirationTimestamp))
                                            .get();

      Log.d(TAG, "Marking gift badge as expired, should notify next time the manage donations screen is open.", true);
      SignalStore.donationsValues().setExpiredGiftBadge(mostRecentExpiration);
    } else if (remoteHasGiftBadges) {
      Log.d(TAG, "We have remote gift badges. Clearing local expired gift badge.", true);
      SignalStore.donationsValues().setExpiredGiftBadge(null);
    }

    boolean userHasVisibleBadges   = badges.stream().anyMatch(SignalServiceProfile.Badge::isVisible);
    boolean userHasInvisibleBadges = badges.stream().anyMatch(b -> !b.isVisible());

    List<Badge> appBadges = badges.stream().map(Badges::fromServiceBadge).collect(Collectors.toList());

    if (userHasVisibleBadges && userHasInvisibleBadges) {
      boolean displayBadgesOnProfile = SignalStore.donationsValues().getDisplayBadgesOnProfile();
      Log.d(TAG, "Detected mixed visibility of badges. Telling the server to mark them all " +
                 (displayBadgesOnProfile ? "" : "not") +
                 " visible.", true);

      BadgeRepository badgeRepository = new BadgeRepository(context);
      List<Badge> updatedBadges = badgeRepository.setVisibilityForAllBadgesSync(displayBadgesOnProfile, appBadges);
      SignalDatabase.recipients().setBadges(Recipient.self().getId(), updatedBadges);
    } else {
      SignalDatabase.recipients().setBadges(Recipient.self().getId(), appBadges);
    }
  }

  private static boolean isSubscription(String badgeId) {
    return !isBoost(badgeId) && !isGift(badgeId);
  }

  private static boolean isBoost(String badgeId) {
    return Objects.equals(badgeId, Badge.BOOST_BADGE_ID);
  }

  private static boolean isGift(String badgeId) {
    return Objects.equals(badgeId, Badge.GIFT_BADGE_ID);
  }

  public static final class Factory implements Job.Factory<RefreshOwnProfileJob> {

    @Override
    public @NonNull RefreshOwnProfileJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new RefreshOwnProfileJob(parameters);
    }
  }
}
