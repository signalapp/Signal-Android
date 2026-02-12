package org.thoughtcrime.securesms.jobs

import android.text.TextUtils
import org.signal.core.util.Base64
import org.signal.core.util.Util
import org.signal.core.util.logging.Log
import org.signal.libsignal.usernames.BaseUsernameException
import org.signal.libsignal.usernames.Username
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.RecipientTable.PhoneNumberSharingState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ProfileUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.NetworkResultUtil
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException
import org.whispersystems.signalservice.api.crypto.ProfileCipher
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.util.ExpiringProfileCredentialUtil
import java.io.IOException

/**
 * Refreshes the profile of the local user. Different from [RetrieveProfileJob] in that we
 * have to sometimes look at/set different data stores, and we will *always* do the fetch regardless
 * of caching.
 */
class RefreshOwnProfileJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY: String = "RefreshOwnProfileJob"

    private val TAG = Log.tag(RefreshOwnProfileJob::class.java)

    private val SUBSCRIPTION_QUEUE = ProfileUploadJob.QUEUE + "_Subscription"
    private val BOOST_QUEUE = ProfileUploadJob.QUEUE + "_Boost"

    fun forSubscription(): RefreshOwnProfileJob {
      return RefreshOwnProfileJob(SUBSCRIPTION_QUEUE)
    }

    fun forBoost(): RefreshOwnProfileJob {
      return RefreshOwnProfileJob(BOOST_QUEUE)
    }
  }

  constructor() : this(ProfileUploadJob.QUEUE)

  private constructor(queue: String) : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setQueue(queue)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(10)
      .build()
  )

  override fun serialize(): ByteArray? {
    return null
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  @Throws(Exception::class)
  override fun onRun() {
    if (!SignalStore.account.isRegistered || SignalStore.account.e164.isNullOrEmpty()) {
      Log.w(TAG, "Not yet registered!")
      return
    }

    if ((SignalStore.svr.hasPin() || SignalStore.account.restoredAccountEntropyPool) && !SignalStore.svr.hasOptedOut() && SignalStore.storageService.lastSyncTime == 0L) {
      Log.i(TAG, "Registered with PIN or AEP but haven't completed storage sync yet.")
      return
    }

    if (!SignalStore.registration.hasUploadedProfile && SignalStore.account.isPrimaryDevice) {
      Log.i(TAG, "Registered but haven't uploaded profile yet.")
      return
    }

    val self = Recipient.self()

    val profileAndCredential: ProfileAndCredential
    try {
      profileAndCredential = ProfileUtil.retrieveProfileSync(context, self, getRequestType(self), false)
    } catch (e: IllegalStateException) {
      Log.w(TAG, "Unexpected exception result from profile fetch. Skipping.")
      return
    }

    val profile = profileAndCredential.getProfile()

    if (Util.isEmpty(profile.getName()) &&
      Util.isEmpty(profile.getAvatar()) &&
      Util.isEmpty(profile.getAbout()) &&
      Util.isEmpty(profile.getAboutEmoji())
    ) {
      Log.w(TAG, "The profile we retrieved was empty! Ignoring it.")

      if (!self.profileName.isEmpty()) {
        Log.w(TAG, "We have a name locally. Scheduling a profile upload.")
        AppDependencies.jobManager.add(ProfileUploadJob())
      } else {
        Log.w(TAG, "We don't have a name locally, either!")
      }

      return
    }

    setProfileName(profile.getName())
    setProfileAbout(profile.getAbout(), profile.getAboutEmoji())
    setProfileAvatar(profile.getAvatar())
    setProfileCapabilities(profile.getCapabilities())
    setProfileBadges(profile.getBadges())
    ensureUnidentifiedAccessCorrect(profile.getUnidentifiedAccess(), profile.isUnrestrictedUnidentifiedAccess())
    ensurePhoneNumberSharingIsCorrect(profile.getPhoneNumberSharing())

    profileAndCredential.getExpiringProfileKeyCredential()
      .ifPresent { setExpiringProfileKeyCredential(self, ProfileKeyUtil.getSelfProfileKey(), it) }

    SignalStore.registration.hasDownloadedProfile = true

    StoryOnboardingDownloadJob.enqueueIfNeeded()

    checkUsernameIsInSync()
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is PushNetworkException
  }

  override fun onFailure() = Unit

  private fun setExpiringProfileKeyCredential(
    recipient: Recipient,
    recipientProfileKey: ProfileKey,
    credential: ExpiringProfileKeyCredential
  ) {
    SignalDatabase.recipients.setProfileKeyCredential(recipient.id, recipientProfileKey, credential)
  }

  private fun setProfileName(encryptedName: String?) {
    try {
      val profileKey = ProfileKeyUtil.getSelfProfileKey()
      val plaintextName = ProfileUtil.decryptString(profileKey, encryptedName)
      val profileName = ProfileName.fromSerialized(plaintextName)

      if (!profileName.isEmpty()) {
        Log.d(TAG, "Saving non-empty name.")
        SignalDatabase.recipients.setProfileName(Recipient.self().id, profileName)
      } else {
        Log.w(TAG, "Ignoring empty name.")
      }
    } catch (e: InvalidCiphertextException) {
      Log.w(TAG, e)
    } catch (e: IOException) {
      Log.w(TAG, e)
    }
  }

  private fun setProfileAbout(encryptedAbout: String?, encryptedEmoji: String?) {
    try {
      val profileKey = ProfileKeyUtil.getSelfProfileKey()
      val plaintextAbout = ProfileUtil.decryptString(profileKey, encryptedAbout)
      val plaintextEmoji = ProfileUtil.decryptString(profileKey, encryptedEmoji)

      Log.d(TAG, "Saving " + (if (!Util.isEmpty(plaintextAbout)) "non-" else "") + "empty about.")
      Log.d(TAG, "Saving " + (if (!Util.isEmpty(plaintextEmoji)) "non-" else "") + "empty emoji.")

      SignalDatabase.recipients.setAbout(Recipient.self().id, plaintextAbout, plaintextEmoji)
    } catch (e: InvalidCiphertextException) {
      Log.w(TAG, e)
    } catch (e: IOException) {
      Log.w(TAG, e)
    }
  }

  private fun setProfileAvatar(avatar: String?) {
    Log.d(TAG, "Saving " + (if (!Util.isEmpty(avatar)) "non-" else "") + "empty avatar.")
    AppDependencies.jobManager.add(RetrieveProfileAvatarJob(Recipient.self(), avatar))
  }

  private fun setProfileCapabilities(capabilities: SignalServiceProfile.Capabilities?) {
    if (capabilities == null) {
      return
    }

    SignalDatabase.recipients.setCapabilities(Recipient.self().id, capabilities)
  }

  private fun ensureUnidentifiedAccessCorrect(unidentifiedAccessVerifier: String?, universalUnidentifiedAccess: Boolean) {
    if (unidentifiedAccessVerifier == null) {
      Log.w(TAG, "No unidentified access is set remotely! Refreshing attributes.")
      AppDependencies.jobManager.add(RefreshAttributesJob())
      return
    }

    if (TextSecurePreferences.isUniversalUnidentifiedAccess(context) != universalUnidentifiedAccess) {
      Log.w(TAG, "The universal access flag doesn't match our local value (local: " + TextSecurePreferences.isUniversalUnidentifiedAccess(context) + ", remote: " + universalUnidentifiedAccess + ")! Refreshing attributes.")
      AppDependencies.jobManager.add(RefreshAttributesJob())
      return
    }

    val profileKey = ProfileKeyUtil.getSelfProfileKey()
    val cipher = ProfileCipher(profileKey)

    val verified = try {
      cipher.verifyUnidentifiedAccess(Base64.decode(unidentifiedAccessVerifier))
    } catch (e: IOException) {
      Log.w(TAG, "Failed to decode unidentified access!", e)
      false
    }

    if (!verified) {
      Log.w(TAG, "Unidentified access failed to verify! Refreshing attributes.")
      AppDependencies.jobManager.add(RefreshAttributesJob())
    }
  }

  /**
   * Checks to make sure that our phone number sharing setting matches what's on our profile. If there's a mismatch, we first sync with storage service
   * (to limit race conditions between devices) and then upload our profile.
   */
  private fun ensurePhoneNumberSharingIsCorrect(phoneNumberSharingCiphertext: String?) {
    if (phoneNumberSharingCiphertext == null) {
      Log.w(TAG, "No phone number sharing is set remotely! Syncing with storage service, then uploading our profile.")
      syncWithStorageServiceThenUploadProfile()
      return
    }

    val profileKey = ProfileKeyUtil.getSelfProfileKey()
    val cipher = ProfileCipher(profileKey)

    try {
      val remotePhoneNumberSharing = cipher.decryptBoolean(Base64.decode(phoneNumberSharingCiphertext))
        .map { value -> if (value) PhoneNumberSharingState.ENABLED else PhoneNumberSharingState.DISABLED }
        .orElse(PhoneNumberSharingState.UNKNOWN)

      if (remotePhoneNumberSharing == PhoneNumberSharingState.UNKNOWN || remotePhoneNumberSharing.enabled != SignalStore.phoneNumberPrivacy.isPhoneNumberSharingEnabled()) {
        Log.w(TAG, "Phone number sharing setting did not match! Syncing with storage service, then uploading our profile.")
        syncWithStorageServiceThenUploadProfile()
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to decode phone number sharing! Syncing with storage service, then uploading our profile.", e)
      syncWithStorageServiceThenUploadProfile()
    } catch (e: InvalidCiphertextException) {
      Log.w(TAG, "Failed to decrypt phone number sharing! Syncing with storage service, then uploading our profile.", e)
      syncWithStorageServiceThenUploadProfile()
    }
  }

  private fun syncWithStorageServiceThenUploadProfile() {
    AppDependencies.jobManager
      .startChain(StorageSyncJob.forRemoteChange())
      .then(ProfileUploadJob())
      .enqueue()
  }

  @Throws(IOException::class)
  private fun setProfileBadges(badges: List<SignalServiceProfile.Badge>?) {
    if (badges == null) {
      return
    }

    val localDonorBadgeIds = Recipient.self()
      .badges
      .filter { it.category == Badge.Category.Donor }
      .map { it.id }
      .toSet()

    val remoteDonorBadgeIds = badges
      .filter { it.getCategory() == Badge.Category.Donor.code }
      .map { it.getId() }
      .toSet()

    val remoteHasSubscriptionBadges = remoteDonorBadgeIds.any { isSubscription(it) }
    val localHasSubscriptionBadges = localDonorBadgeIds.any { isSubscription(it) }
    val remoteHasBoostBadges = remoteDonorBadgeIds.any { isBoost(it) }
    val localHasBoostBadges = localDonorBadgeIds.any { isBoost(it) }
    val remoteHasGiftBadges = remoteDonorBadgeIds.any { isGift(it) }
    val localHasGiftBadges = localDonorBadgeIds.any { isGift(it) }

    if (!remoteHasSubscriptionBadges && localHasSubscriptionBadges) {
      val mostRecentExpiration = Recipient.self()
        .badges
        .filter { it.category == Badge.Category.Donor }
        .filter { isSubscription(it.id) }
        .maxByOrNull { it.expirationTimestamp }
        ?: throw NoSuchElementException("No value present")

      Log.d(TAG, "Marking subscription badge as expired, should notify next time the conversation list is open.", true)
      SignalStore.inAppPayments.setExpiredBadge(mostRecentExpiration)

      if (!InAppPaymentsRepository.isUserManuallyCancelled(InAppPaymentSubscriberRecord.Type.DONATION)) {
        Log.d(TAG, "Detected an unexpected subscription expiry.", true)
        val subscriber = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)

        var isDueToPaymentFailure = false
        if (subscriber != null) {
          val response = AppDependencies.donationsService
            .getSubscription(subscriber.subscriberId)

          if (response.getResult().isPresent()) {
            val activeSubscription = response.getResult().get()
            if (activeSubscription.isFailedPayment()) {
              Log.d(TAG, "Unexpected expiry due to payment failure.", true)
              isDueToPaymentFailure = true
            }

            if (activeSubscription.getChargeFailure() != null) {
              Log.d(TAG, "Active payment contains a charge failure: " + activeSubscription.getChargeFailure().getCode(), true)
            }
          }

          InAppPaymentsRepository.setShouldCancelSubscriptionBeforeNextSubscribeAttempt(subscriber, true)
        }

        if (!isDueToPaymentFailure) {
          Log.d(TAG, "Unexpected expiry due to inactivity.", true)
        }

        MultiDeviceSubscriptionSyncRequestJob.enqueue()
      }
    } else if (!remoteHasBoostBadges && localHasBoostBadges) {
      val mostRecentExpiration = Recipient.self()
        .badges
        .filter { it.category == Badge.Category.Donor }
        .filter { isBoost(it.id) }
        .maxByOrNull { it.expirationTimestamp }
        ?: throw NoSuchElementException("No value present")

      Log.d(TAG, "Marking boost badge as expired, should notify next time the conversation list is open.", true)
      SignalStore.inAppPayments.setExpiredBadge(mostRecentExpiration)
    } else {
      val badge = SignalStore.inAppPayments.getExpiredBadge()

      if (badge != null && badge.isSubscription() && remoteHasSubscriptionBadges) {
        Log.d(TAG, "Remote has subscription badges. Clearing local expired subscription badge.", true)
        SignalStore.inAppPayments.setExpiredBadge(null)
      } else if (badge != null && badge.isBoost() && remoteHasBoostBadges) {
        Log.d(TAG, "Remote has boost badges. Clearing local expired boost badge.", true)
        SignalStore.inAppPayments.setExpiredBadge(null)
      }
    }

    if (!remoteHasGiftBadges && localHasGiftBadges) {
      val mostRecentExpiration = Recipient.self()
        .badges
        .filter { it.category == Badge.Category.Donor }
        .filter { isGift(it.id) }
        .maxByOrNull { it.expirationTimestamp }
        ?: throw NoSuchElementException("No value present")

      Log.d(TAG, "Marking gift badge as expired, should notify next time the manage donations screen is open.", true)
      SignalStore.inAppPayments.setExpiredGiftBadge(mostRecentExpiration)
    } else if (remoteHasGiftBadges) {
      Log.d(TAG, "We have remote gift badges. Clearing local expired gift badge.", true)
      SignalStore.inAppPayments.setExpiredGiftBadge(null)
    }

    val userHasVisibleBadges = badges.any { it.isVisible() }
    val userHasInvisibleBadges = badges.any { !it.isVisible() }

    val appBadges = badges.map { Badges.fromServiceBadge(it) }

    if (userHasVisibleBadges && userHasInvisibleBadges) {
      val displayBadgesOnProfile = SignalStore.inAppPayments.getDisplayBadgesOnProfile()
      Log.d(
        TAG, "Detected mixed visibility of badges. Telling the server to mark them all " +
          (if (displayBadgesOnProfile) "" else "not") +
          " visible.", true
      )

      val badgeRepository = BadgeRepository(context)
      val updatedBadges = badgeRepository.setVisibilityForAllBadgesSync(displayBadgesOnProfile, appBadges)
      SignalDatabase.recipients.setBadges(Recipient.self().id, updatedBadges)
    } else {
      SignalDatabase.recipients.setBadges(Recipient.self().id, appBadges)
    }
  }

  private fun checkUsernameIsInSync() {
    var validated = false

    try {
      val localUsername = SignalStore.account.username

      val whoAmIResponse = AppDependencies.signalServiceAccountManager.getWhoAmI()
      val remoteUsernameHash = whoAmIResponse.usernameHash
      val localUsernameHash = if (localUsername != null) Base64.encodeUrlSafeWithoutPadding(Username(localUsername).getHash()) else null

      if (TextUtils.isEmpty(localUsernameHash) && TextUtils.isEmpty(remoteUsernameHash)) {
        Log.d(TAG, "Local and remote username hash are both empty. Considering validated.")
        UsernameRepository.onUsernameConsistencyValidated()
      } else if (localUsernameHash != remoteUsernameHash) {
        Log.w(
          TAG,
          "Local username hash does not match server username hash. Local hash: " + (if (TextUtils.isEmpty(localUsername)) "empty" else "present") + ", Remote hash: " + (if (TextUtils.isEmpty(remoteUsernameHash)) "empty" else "present")
        )
        UsernameRepository.onUsernameMismatchDetected()
        return
      } else {
        Log.d(TAG, "Username validated.")
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed perform synchronization check during username phase.", e)
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "Our local username data is invalid!", e)
      UsernameRepository.onUsernameMismatchDetected()
      return
    }

    try {
      val localUsernameLink = SignalStore.account.usernameLink

      if (localUsernameLink != null) {
        val remoteEncryptedUsername = NetworkResultUtil.toBasicLegacy<ByteArray>(SignalNetwork.username.getEncryptedUsernameFromLinkServerId(localUsernameLink.serverId))
        val combinedLink = Username.UsernameLink(localUsernameLink.entropy, remoteEncryptedUsername)
        val remoteUsername = Username.fromLink(combinedLink)

        if (remoteUsername.getUsername() != SignalStore.account.username) {
          Log.w(TAG, "The remote username decrypted ok, but the decrypted username did not match our local username!")
          UsernameRepository.onUsernameLinkMismatchDetected()
        } else {
          Log.d(TAG, "Username link validated.")
        }

        validated = true
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed perform synchronization check during the username link phase.", e)
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "Failed to decrypt username link using the remote encrypted username and our local entropy!", e)
      UsernameRepository.onUsernameLinkMismatchDetected()
    }

    if (validated) {
      UsernameRepository.onUsernameConsistencyValidated()
    }
  }

  private fun getRequestType(recipient: Recipient): SignalServiceProfile.RequestType {
    return if (ExpiringProfileCredentialUtil.isValid(recipient.expiringProfileKeyCredential))
      SignalServiceProfile.RequestType.PROFILE
    else
      SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL
  }

  private fun isSubscription(badgeId: String?): Boolean {
    return !isBoost(badgeId) && !isGift(badgeId)
  }

  private fun isBoost(badgeId: String?): Boolean {
    return badgeId == Badge.BOOST_BADGE_ID
  }

  private fun isGift(badgeId: String?): Boolean {
    return badgeId == Badge.GIFT_BADGE_ID
  }

  class Factory : Job.Factory<RefreshOwnProfileJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RefreshOwnProfileJob {
      return RefreshOwnProfileJob(parameters)
    }
  }
}
