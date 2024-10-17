package org.thoughtcrime.securesms.testing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.rules.ExternalResource
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.thoughtcrime.securesms.SignalInstrumentationApplicationContext
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.data.LocalRegistrationMetadataUtil
import org.thoughtcrime.securesms.registration.data.RegistrationData
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.testing.GroupTestingUtils.asMember
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.util.UUID

/**
 * Test rule to use that sets up the application in a mostly registered state. Enough so that most
 * activities should be launchable directly.
 *
 * To use: `@get:Rule val harness = SignalActivityRule()`
 */
class SignalActivityRule(private val othersCount: Int = 4, private val createGroup: Boolean = false) : ExternalResource() {

  val application: Application = AppDependencies.application
  private val TEST_E164 = "+15555550101"

  lateinit var context: Context
    private set
  lateinit var self: Recipient
    private set
  lateinit var others: List<RecipientId>
    private set
  lateinit var othersKeys: List<IdentityKeyPair>

  var group: GroupTestingUtils.TestGroupInfo? = null
    private set

  val inMemoryLogger: InMemoryLogger
    get() = (application as SignalInstrumentationApplicationContext).inMemoryLogger

  override fun before() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    self = setupSelf()

    val setupOthers = setupOthers()
    others = setupOthers.first
    othersKeys = setupOthers.second

    if (createGroup && others.size >= 2) {
      group = GroupTestingUtils.insertGroup(
        revision = 0,
        self.asMember(),
        others[0].asMember(),
        others[1].asMember()
      )
    }

    InstrumentationApplicationDependencyProvider.clearHandlers()
  }

  private fun setupSelf(): Recipient {
    PreferenceManager.getDefaultSharedPreferences(application).edit().putBoolean("pref_prompted_push_registration", true).commit()
    val masterSecret = MasterSecretUtil.generateMasterSecret(application, MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
    MasterSecretUtil.generateAsymmetricMasterSecret(application, masterSecret)
    val preferences: SharedPreferences = application.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0)
    preferences.edit().putBoolean("passphrase_initialized", true).commit()

    SignalStore.account.generateAciIdentityKeyIfNecessary()
    SignalStore.account.generatePniIdentityKeyIfNecessary()

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(Put("/v2/keys") { MockResponse().success() })
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

    SignalStore.settings.isMessageNotificationsEnabled = false

    return Recipient.self()
  }

  private fun setupOthers(): Pair<List<RecipientId>, List<IdentityKeyPair>> {
    val others = mutableListOf<RecipientId>()
    val othersKeys = mutableListOf<IdentityKeyPair>()

    if (othersCount !in 0 until 1000) {
      throw IllegalArgumentException("$othersCount must be between 0 and 1000")
    }

    for (i in 0 until othersCount) {
      val aci = ACI.from(UUID.randomUUID())
      val recipientId = RecipientId.from(SignalServiceAddress(aci, "+15555551%03d".format(i)))
      SignalDatabase.recipients.setProfileName(recipientId, ProfileName.fromParts("Buddy", "#$i"))
      SignalDatabase.recipients.setProfileKeyIfAbsent(recipientId, ProfileKeyUtil.createNew())
      SignalDatabase.recipients.setCapabilities(recipientId, SignalServiceProfile.Capabilities(true, false, true))
      SignalDatabase.recipients.setProfileSharing(recipientId, true)
      SignalDatabase.recipients.markRegistered(recipientId, aci)
      val otherIdentity = IdentityKeyUtil.generateIdentityKeyPair()
      AppDependencies.protocolStore.aci().saveIdentity(SignalProtocolAddress(aci.toString(), 0), otherIdentity.publicKey)
      others += recipientId
      othersKeys += otherIdentity
    }

    return others to othersKeys
  }

  inline fun <reified T : Activity> launchActivity(initIntent: Intent.() -> Unit = {}): ActivityScenario<T> {
    return androidx.test.core.app.launchActivity(Intent(context, T::class.java).apply(initIntent))
  }

  fun changeIdentityKey(recipient: Recipient, identityKey: IdentityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey) {
    AppDependencies.protocolStore.aci().saveIdentity(SignalProtocolAddress(recipient.requireServiceId().toString(), 0), identityKey)
  }

  fun getIdentity(recipient: Recipient): IdentityKey {
    return AppDependencies.protocolStore.aci().identities().getIdentity(SignalProtocolAddress(recipient.requireServiceId().toString(), 0))
  }

  fun setVerified(recipient: Recipient, status: IdentityTable.VerifiedStatus) {
    AppDependencies.protocolStore.aci().identities().setVerified(recipient.id, getIdentity(recipient), IdentityTable.VerifiedStatus.VERIFIED)
  }
}
