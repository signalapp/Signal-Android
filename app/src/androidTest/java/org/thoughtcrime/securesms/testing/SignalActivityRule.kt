package org.thoughtcrime.securesms.testing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.RegistrationData
import org.thoughtcrime.securesms.registration.RegistrationRepository
import org.thoughtcrime.securesms.registration.RegistrationUtil
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import java.util.UUID

/**
 * Test rule to use that sets up the application in a mostly registered state. Enough so that most
 * activities should be launchable directly.
 *
 * To use: `@get:Rule val harness = SignalActivityRule()`
 */
class SignalActivityRule : ExternalResource() {

  val application: Application = ApplicationDependencies.getApplication()

  lateinit var context: Context
    private set
  lateinit var self: Recipient
    private set
  lateinit var others: List<RecipientId>
    private set

  override fun before() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    self = setupSelf()
    others = setupOthers()
  }

  private fun setupSelf(): Recipient {
    DeviceTransferBlockingInterceptor.getInstance().blockNetwork()

    PreferenceManager.getDefaultSharedPreferences(application).edit().putBoolean("pref_prompted_push_registration", true).commit()
    val masterSecret = MasterSecretUtil.generateMasterSecret(application, MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
    MasterSecretUtil.generateAsymmetricMasterSecret(application, masterSecret)
    val preferences: SharedPreferences = application.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0)
    preferences.edit().putBoolean("passphrase_initialized", true).commit()

    val registrationRepository = RegistrationRepository(application)

    registrationRepository.registerAccountWithoutRegistrationLock(
      RegistrationData(
        code = "123123",
        e164 = "+15554045550101",
        password = Util.getSecret(18),
        registrationId = registrationRepository.registrationId,
        profileKey = registrationRepository.getProfileKey("+15554045550101"),
        fcmToken = null
      ),
      VerifyAccountResponse(UUID.randomUUID().toString(), UUID.randomUUID().toString(), false)
    ).blockingGet()

    SignalStore.kbsValues().optOut()
    RegistrationUtil.maybeMarkRegistrationComplete(application)
    SignalDatabase.recipients.setProfileName(Recipient.self().id, ProfileName.fromParts("Tester", "McTesterson"))

    return Recipient.self()
  }

  private fun setupOthers(): List<RecipientId> {
    val others = mutableListOf<RecipientId>()

    for (i in 0..4) {
      val aci = ACI.from(UUID.randomUUID())
      val recipientId = RecipientId.from(aci, "+1555555101$i")
      SignalDatabase.recipients.setProfileName(recipientId, ProfileName.fromParts("Buddy", "#$i"))
      SignalDatabase.recipients.setProfileKeyIfAbsent(recipientId, ProfileKeyUtil.createNew())
      SignalDatabase.recipients.setCapabilities(recipientId, SignalServiceProfile.Capabilities(true, true, true, true, true, true, true))
      SignalDatabase.recipients.setProfileSharing(recipientId, true)
      ApplicationDependencies.getProtocolStore().aci().saveIdentity(SignalProtocolAddress(aci.toString(), 0), IdentityKeyUtil.generateIdentityKeyPair().publicKey)
      others += recipientId
    }

    return others
  }

  inline fun <reified T : Activity> launchActivity(initIntent: Intent.() -> Unit): ActivityScenario<T> {
    return androidx.test.core.app.launchActivity(Intent(context, T::class.java).apply(initIntent))
  }

  fun changeIdentityKey(recipient: Recipient, identityKey: IdentityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey) {
    ApplicationDependencies.getProtocolStore().aci().saveIdentity(SignalProtocolAddress(recipient.requireServiceId().toString(), 0), identityKey)
  }

  fun getIdentity(recipient: Recipient): IdentityKey {
    return ApplicationDependencies.getProtocolStore().aci().identities().getIdentity(SignalProtocolAddress(recipient.requireServiceId().toString(), 0))
  }

  fun setVerified(recipient: Recipient, status: IdentityDatabase.VerifiedStatus) {
    ApplicationDependencies.getProtocolStore().aci().identities().setVerified(recipient.id, getIdentity(recipient), IdentityDatabase.VerifiedStatus.VERIFIED)
  }
}
