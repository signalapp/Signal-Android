package org.signal.benchmark.setup

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import kotlinx.coroutines.runBlocking
import okio.ByteString
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.Util
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.storageservice.storage.protos.groups.AccessControl
import org.signal.storageservice.storage.protos.groups.Member
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember
import org.signal.storageservice.storage.protos.groups.local.DecryptedTimer
import org.signal.storageservice.storage.protos.groups.local.EnabledState
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.CertificateType
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.Skipped
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.data.AccountRegistrationResult
import org.thoughtcrime.securesms.registration.data.LocalRegistrationMetadataUtil
import org.thoughtcrime.securesms.registration.data.RegistrationData
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

object TestUsers {

  private var generatedOthers: Int = 1

  fun setupSelf(): Recipient {
    val application: Application = AppDependencies.application
    DeviceTransferBlockingInterceptor.getInstance().blockNetwork()

    PreferenceManager.getDefaultSharedPreferences(application).edit().putBoolean("pref_prompted_push_registration", true).commit()
    val masterSecret = MasterSecretUtil.generateMasterSecret(application, MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
    MasterSecretUtil.generateAsymmetricMasterSecret(application, masterSecret)
    val preferences: SharedPreferences = application.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0)
    preferences.edit().putBoolean("passphrase_initialized", true).commit()

    SignalStore.account.generateAciIdentityKeyIfNecessary()
    SignalStore.account.generatePniIdentityKeyIfNecessary()

    runBlocking {
      val registrationData = RegistrationData(
        code = "123123",
        e164 = Harness.SELF_E164,
        password = Util.getSecret(18),
        registrationId = RegistrationRepository.getRegistrationId(),
        profileKey = RegistrationRepository.getProfileKey(Harness.SELF_E164),
        fcmToken = null,
        pniRegistrationId = RegistrationRepository.getPniRegistrationId(),
        recoveryPassword = "asdfasdfasdfasdf"
      )
      val remoteResult = AccountRegistrationResult(
        uuid = Harness.SELF_ACI.toString(),
        pni = UUID.randomUUID().toString(),
        storageCapable = false,
        number = Harness.SELF_E164,
        masterKey = null,
        pin = null,
        aciPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(SignalStore.account.aciIdentityKey, SignalStore.account.aciPreKeys),
        pniPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(SignalStore.account.aciIdentityKey, SignalStore.account.pniPreKeys),
        reRegistration = false
      )
      val localRegistrationData = LocalRegistrationMetadataUtil.createLocalRegistrationMetadata(SignalStore.account.aciIdentityKey, SignalStore.account.pniIdentityKey, registrationData, remoteResult, false)
      RegistrationRepository.registerAccountLocally(application, localRegistrationData)
    }

    SignalStore.svr.optOut()
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.Skipped
    RegistrationUtil.maybeMarkRegistrationComplete()
    SignalDatabase.recipients.setProfileName(Recipient.self().id, ProfileName.fromParts("Tester", "McTesterson"))
    TextSecurePreferences.setPromptedOptimizeDoze(application, true)
    TextSecurePreferences.setRatingEnabled(application, false)

    PreKeyUtil.generateAndStoreSignedPreKey(AppDependencies.protocolStore.aci(), SignalStore.account.aciPreKeys)
    PreKeyUtil.generateAndStoreOneTimeEcPreKeys(AppDependencies.protocolStore.aci(), SignalStore.account.aciPreKeys)
    PreKeyUtil.generateAndStoreOneTimeKyberPreKeys(AppDependencies.protocolStore.aci(), SignalStore.account.aciPreKeys)

    val aliceSenderCertificate = Harness.createCertificateFor(
      uuid = Harness.SELF_ACI.rawUuid,
      e164 = Harness.SELF_E164,
      deviceId = 1,
      identityKey = SignalStore.account.aciIdentityKey.publicKey.publicKey,
      expires = System.currentTimeMillis().milliseconds + 30.days
    )

    val aliceSenderCertificate2 = Harness.createCertificateFor(
      uuid = Harness.SELF_ACI.rawUuid,
      e164 = null,
      deviceId = 1,
      identityKey = SignalStore.account.aciIdentityKey.publicKey.publicKey,
      expires = System.currentTimeMillis().milliseconds + 30.days
    )

    SignalStore.certificate.setUnidentifiedAccessCertificate(CertificateType.ACI_AND_E164, aliceSenderCertificate.serialized)
    SignalStore.certificate.setUnidentifiedAccessCertificate(CertificateType.ACI_ONLY, aliceSenderCertificate2.serialized)

    return Recipient.self()
  }

  fun setupTestRecipient(): RecipientId {
    return setupTestRecipients(1).first()
  }

  fun setupTestRecipients(othersCount: Int): List<RecipientId> {
    val others = mutableListOf<RecipientId>()
    synchronized(this) {
      if (generatedOthers + othersCount !in 0 until 1000) {
        throw IllegalArgumentException("$othersCount must be between 0 and 1000")
      }

      for (i in generatedOthers until generatedOthers + othersCount) {
        val aci = ACI.from(UUID.randomUUID())
        val recipientId = RecipientId.from(SignalServiceAddress(aci, "+15555551%03d".format(i)))
        SignalDatabase.recipients.setProfileName(recipientId, ProfileName.fromParts("Buddy", "#$i"))
        SignalDatabase.recipients.setProfileKeyIfAbsent(recipientId, ProfileKeyUtil.createNew())
        SignalDatabase.recipients.setCapabilities(recipientId, SignalServiceProfile.Capabilities(true, true))
        SignalDatabase.recipients.setProfileSharing(recipientId, true)
        SignalDatabase.recipients.markRegistered(recipientId, aci)
        val otherIdentity = IdentityKeyPair.generate()
        AppDependencies.protocolStore.aci().saveIdentity(SignalProtocolAddress(aci.toString(), 1), otherIdentity.publicKey)

        others += recipientId
      }

      generatedOthers += othersCount
    }

    return others
  }

  fun setupTestClients(othersCount: Int): List<RecipientId> {
    val others = mutableListOf<RecipientId>()
    synchronized(this) {
      for (i in 0 until othersCount) {
        val otherClient = Harness.otherClients[i]

        val recipientId = RecipientId.from(SignalServiceAddress(otherClient.serviceId, otherClient.e164))
        SignalDatabase.recipients.setProfileName(recipientId, ProfileName.fromParts("Buddy", "#$i"))
        SignalDatabase.recipients.setProfileKeyIfAbsent(recipientId, otherClient.profileKey)
        SignalDatabase.recipients.setCapabilities(recipientId, SignalServiceProfile.Capabilities(true, true))
        SignalDatabase.recipients.setProfileSharing(recipientId, true)
        SignalDatabase.recipients.markRegistered(recipientId, otherClient.serviceId)
        AppDependencies.protocolStore.aci().saveIdentity(SignalProtocolAddress(otherClient.serviceId.toString(), 1), otherClient.identityKeyPair.publicKey)

        others += recipientId
      }

      generatedOthers += othersCount
    }

    return others
  }

  fun setupGroup() {
    val members = setupTestClients(5)
    val self = Recipient.self()

    val fullMembers = buildList {
      add(member(aci = self.requireAci()))
      addAll(members.map { member(aci = Recipient.resolved(it).requireAci()) })
    }

    val group = DecryptedGroup(
      title = "Title",
      avatar = "",
      disappearingMessagesTimer = DecryptedTimer(),
      accessControl = AccessControl(),
      revision = 1,
      members = fullMembers,
      pendingMembers = emptyList(),
      requestingMembers = emptyList(),
      inviteLinkPassword = ByteString.EMPTY,
      description = "Description",
      isAnnouncementGroup = EnabledState.DISABLED,
      bannedMembers = emptyList(),
      isPlaceholderGroup = false
    )

    val groupId = SignalDatabase.groups.create(
      groupMasterKey = Harness.groupMasterKey,
      groupState = group,
      groupSendEndorsements = null
    )

    SignalDatabase.recipients.setProfileSharing(Recipient.externalGroupExact(groupId!!).id, true)
  }

  private fun member(aci: ACI, role: Member.Role = Member.Role.DEFAULT, joinedAt: Int = 0, labelEmoji: String = "", labelString: String = ""): DecryptedMember {
    return DecryptedMember(
      role = role,
      aciBytes = aci.toByteString(),
      joinedAtRevision = joinedAt,
      labelEmoji = labelEmoji,
      labelString = labelString
    )
  }
}
