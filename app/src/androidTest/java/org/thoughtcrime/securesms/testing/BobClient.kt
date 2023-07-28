package org.thoughtcrime.securesms.testing

import org.signal.core.util.readToSingleInt
import org.signal.core.util.select
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.database.OneTimePreKeyTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignedPreKeyTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.FakeClientHelpers.toEnvelope
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalSessionLock
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.crypto.SignalSessionBuilder
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import java.lang.UnsupportedOperationException
import java.util.Optional
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

/**
 * Welcome to Bob's Client.
 *
 * Bob is a "fake" client that can start a session with the Android instrumentation test user (Alice).
 *
 * Bob can create a new session using a prekey bundle created from Alice's prekeys, send a message, decrypt
 * a return message from Alice, and that'll start a standard Signal session with normal keys/ratcheting.
 */
class BobClient(val serviceId: ServiceId, val e164: String, val identityKeyPair: IdentityKeyPair, val trustRoot: ECKeyPair, val profileKey: ProfileKey) {

  private val serviceAddress = SignalServiceAddress(serviceId, e164)
  private val registrationId = KeyHelper.generateRegistrationId(false)
  private val aciStore = BobSignalServiceAccountDataStore(registrationId, identityKeyPair)
  private val senderCertificate = FakeClientHelpers.createCertificateFor(trustRoot, serviceId.rawUuid, e164, 1, identityKeyPair.publicKey.publicKey, 31337)
  private val sessionLock = object : SignalSessionLock {
    private val lock = ReentrantLock()

    override fun acquire(): SignalSessionLock.Lock {
      lock.lock()
      return SignalSessionLock.Lock { lock.unlock() }
    }
  }

  /** Inspired by SignalServiceMessageSender#getEncryptedMessage */
  fun encrypt(now: Long): SignalServiceProtos.Envelope {
    val envelopeContent = FakeClientHelpers.encryptedTextMessage(now)

    val cipher = SignalServiceCipher(serviceAddress, 1, aciStore, sessionLock, null)

    if (!aciStore.containsSession(getAliceProtocolAddress())) {
      val sessionBuilder = SignalSessionBuilder(sessionLock, SessionBuilder(aciStore, getAliceProtocolAddress()))
      sessionBuilder.process(getAlicePreKeyBundle())
    }

    return cipher.encrypt(getAliceProtocolAddress(), getAliceUnidentifiedAccess(), envelopeContent)
      .toEnvelope(envelopeContent.content.get().dataMessage.timestamp, getAliceServiceId())
  }

  fun decrypt(envelope: SignalServiceProtos.Envelope, serverDeliveredTimestamp: Long) {
    val cipher = SignalServiceCipher(serviceAddress, 1, aciStore, sessionLock, UnidentifiedAccessUtil.getCertificateValidator())
    cipher.decrypt(envelope, serverDeliveredTimestamp)
  }

  private fun getAliceServiceId(): ServiceId {
    return SignalStore.account().requireAci()
  }

  private fun getAlicePreKeyBundle(): PreKeyBundle {
    val selfPreKeyId = SignalDatabase.rawDatabase
      .select(OneTimePreKeyTable.KEY_ID)
      .from(OneTimePreKeyTable.TABLE_NAME)
      .where("${OneTimePreKeyTable.ACCOUNT_ID} = ?", getAliceServiceId().toString())
      .run()
      .readToSingleInt(-1)

    val selfPreKeyRecord = SignalDatabase.oneTimePreKeys.get(getAliceServiceId(), selfPreKeyId)!!

    val selfSignedPreKeyId = SignalDatabase.rawDatabase
      .select(SignedPreKeyTable.KEY_ID)
      .from(SignedPreKeyTable.TABLE_NAME)
      .where("${SignedPreKeyTable.ACCOUNT_ID} = ?", getAliceServiceId().toString())
      .run()
      .readToSingleInt(-1)

    val selfSignedPreKeyRecord = SignalDatabase.signedPreKeys.get(getAliceServiceId(), selfSignedPreKeyId)!!

    return PreKeyBundle(
      SignalStore.account().registrationId,
      1,
      selfPreKeyId,
      selfPreKeyRecord.keyPair.publicKey,
      selfSignedPreKeyId,
      selfSignedPreKeyRecord.keyPair.publicKey,
      selfSignedPreKeyRecord.signature,
      getAlicePublicKey()
    )
  }

  private fun getAliceProtocolAddress(): SignalProtocolAddress {
    return SignalProtocolAddress(SignalStore.account().requireAci().toString(), 1)
  }

  private fun getAlicePublicKey(): IdentityKey {
    return SignalStore.account().aciIdentityKey.publicKey
  }

  private fun getAliceProfileKey(): ProfileKey {
    return ProfileKeyUtil.getSelfProfileKey()
  }

  private fun getAliceUnidentifiedAccess(): Optional<UnidentifiedAccess> {
    return FakeClientHelpers.getTargetUnidentifiedAccess(profileKey, getAliceProfileKey(), senderCertificate)
  }

  private class BobSignalServiceAccountDataStore(private val registrationId: Int, private val identityKeyPair: IdentityKeyPair) : SignalServiceAccountDataStore {
    private var aliceSessionRecord: SessionRecord? = null

    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair

    override fun getLocalRegistrationId(): Int = registrationId
    override fun isTrustedIdentity(address: SignalProtocolAddress?, identityKey: IdentityKey?, direction: IdentityKeyStore.Direction?): Boolean = true
    override fun loadSession(address: SignalProtocolAddress?): SessionRecord = aliceSessionRecord ?: SessionRecord()
    override fun saveIdentity(address: SignalProtocolAddress?, identityKey: IdentityKey?): Boolean = false
    override fun storeSession(address: SignalProtocolAddress?, record: SessionRecord?) { aliceSessionRecord = record }
    override fun getSubDeviceSessions(name: String?): List<Int> = emptyList()
    override fun containsSession(address: SignalProtocolAddress?): Boolean = aliceSessionRecord != null
    override fun getIdentity(address: SignalProtocolAddress?): IdentityKey = SignalStore.account().aciIdentityKey.publicKey

    override fun loadPreKey(preKeyId: Int): PreKeyRecord = throw UnsupportedOperationException()
    override fun storePreKey(preKeyId: Int, record: PreKeyRecord?) = throw UnsupportedOperationException()
    override fun containsPreKey(preKeyId: Int): Boolean = throw UnsupportedOperationException()
    override fun removePreKey(preKeyId: Int) = throw UnsupportedOperationException()
    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>?): MutableList<SessionRecord> = throw UnsupportedOperationException()
    override fun deleteSession(address: SignalProtocolAddress?) = throw UnsupportedOperationException()
    override fun deleteAllSessions(name: String?) = throw UnsupportedOperationException()
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord = throw UnsupportedOperationException()
    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = throw UnsupportedOperationException()
    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord?) = throw UnsupportedOperationException()
    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = throw UnsupportedOperationException()
    override fun removeSignedPreKey(signedPreKeyId: Int) = throw UnsupportedOperationException()
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord = throw UnsupportedOperationException()
    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> = throw UnsupportedOperationException()
    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord?) = throw UnsupportedOperationException()
    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = throw UnsupportedOperationException()
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int) = throw UnsupportedOperationException()
    override fun storeSenderKey(sender: SignalProtocolAddress?, distributionId: UUID?, record: SenderKeyRecord?) = throw UnsupportedOperationException()
    override fun loadSenderKey(sender: SignalProtocolAddress?, distributionId: UUID?): SenderKeyRecord = throw UnsupportedOperationException()
    override fun archiveSession(address: SignalProtocolAddress?) = throw UnsupportedOperationException()
    override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>?): MutableSet<SignalProtocolAddress> = throw UnsupportedOperationException()
    override fun getSenderKeySharedWith(distributionId: DistributionId?): MutableSet<SignalProtocolAddress> = throw UnsupportedOperationException()
    override fun markSenderKeySharedWith(distributionId: DistributionId?, addresses: MutableCollection<SignalProtocolAddress>?) = throw UnsupportedOperationException()
    override fun clearSenderKeySharedWith(addresses: MutableCollection<SignalProtocolAddress>?) = throw UnsupportedOperationException()
    override fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord) = throw UnsupportedOperationException()
    override fun removeKyberPreKey(kyberPreKeyId: Int) = throw UnsupportedOperationException()
    override fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord> = throw UnsupportedOperationException()

    override fun isMultiDevice(): Boolean = throw UnsupportedOperationException()
  }
}
