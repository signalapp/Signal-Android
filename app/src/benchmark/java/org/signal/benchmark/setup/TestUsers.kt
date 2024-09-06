package org.signal.benchmark.setup

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.data.LocalRegistrationMetadataUtil
import org.thoughtcrime.securesms.registration.data.RegistrationData
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.util.UUID

object TestUsers {

  private var generatedOthers: Int = 0
  private val TEST_E164 = "+15555550101"

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
        e164 = TEST_E164,
        password = Util.getSecret(18),
        registrationId = RegistrationRepository.getRegistrationId(),
        profileKey = RegistrationRepository.getProfileKey(TEST_E164),
        fcmToken = null,
        pniRegistrationId = RegistrationRepository.getPniRegistrationId(),
        recoveryPassword = "asdfasdfasdfasdf"
      )
      val remoteResult = RegistrationRepository.AccountRegistrationResult(
        uuid = UUID.randomUUID().toString(),
        pni = UUID.randomUUID().toString(),
        storageCapable = false,
        number = TEST_E164,
        masterKey = null,
        pin = null,
        aciPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(SignalStore.account.aciIdentityKey, SignalStore.account.aciPreKeys),
        pniPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(SignalStore.account.aciIdentityKey, SignalStore.account.pniPreKeys)
      )
      val localRegistrationData = LocalRegistrationMetadataUtil.createLocalRegistrationMetadata(SignalStore.account.aciIdentityKey, SignalStore.account.pniIdentityKey, registrationData, remoteResult, false)
      RegistrationRepository.registerAccountLocally(application, localRegistrationData)
    }

    SignalStore.svr.optOut()
    RegistrationUtil.maybeMarkRegistrationComplete()
    SignalDatabase.recipients.setProfileName(Recipient.self().id, ProfileName.fromParts("Tester", "McTesterson"))

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
        SignalDatabase.recipients.setCapabilities(recipientId, SignalServiceProfile.Capabilities(true, true, true))
        SignalDatabase.recipients.setProfileSharing(recipientId, true)
        SignalDatabase.recipients.markRegistered(recipientId, aci)
        val otherIdentity = IdentityKeyUtil.generateIdentityKeyPair()
        AppDependencies.protocolStore.aci().saveIdentity(SignalProtocolAddress(aci.toString(), 0), otherIdentity.publicKey)

        others += recipientId
      }

      generatedOthers += othersCount
    }

    return others
  }
}
