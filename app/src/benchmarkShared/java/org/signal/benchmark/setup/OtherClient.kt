/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.benchmark.setup

import org.signal.benchmark.setup.Generator.toEnvelope
import org.signal.core.models.ServiceId
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.IdentityKeyStore.IdentityChange
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalSessionLock
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.crypto.SignalSessionBuilder
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.Envelope
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * This is a "fake" client that can start a session with the running app's user, referred to as Alice in this
 * code.
 */
class OtherClient(val serviceId: ServiceId, val e164: String, val identityKeyPair: IdentityKeyPair, val profileKey: ProfileKey) {

  private val serviceAddress = SignalServiceAddress(serviceId, e164)
  private val registrationId = KeyHelper.generateRegistrationId(false)
  private val aciStore = BobSignalServiceAccountDataStore(registrationId, identityKeyPair)
  private val senderCertificate = Harness.createCertificateFor(serviceId.rawUuid, e164, 1, identityKeyPair.publicKey.publicKey, System.currentTimeMillis().milliseconds + 30.days)
  private val sessionLock = object : SignalSessionLock {
    private val lock = ReentrantLock()

    override fun acquire(): SignalSessionLock.Lock {
      lock.lock()
      return SignalSessionLock.Lock { lock.unlock() }
    }
  }

  /** Inspired by SignalServiceMessageSender#getEncryptedMessage */
  fun encrypt(envelopeContent: EnvelopeContent): Envelope {
    val cipher = SignalServiceCipher(serviceAddress, 1, aciStore, sessionLock, null)

    if (!aciStore.containsSession(getAliceProtocolAddress())) {
      val sessionBuilder = SignalSessionBuilder(sessionLock, SessionBuilder(aciStore, getAliceProtocolAddress()))
      sessionBuilder.process(getAlicePreKeyBundle())
    }

    return cipher.encrypt(getAliceProtocolAddress(), getAliceUnidentifiedAccess(), envelopeContent)
      .toEnvelope(envelopeContent.content.get().dataMessage!!.timestamp!!, getAliceServiceId())
  }

  fun generateInboundEnvelopes(count: Int): List<Envelope> {
    val envelopes = ArrayList<Envelope>(count)
    var now = System.currentTimeMillis()
    for (i in 0 until count) {
      envelopes += encrypt(Generator.encryptedTextMessage(now))
      now += 3
    }

    return envelopes
  }

  fun generateInboundGroupEnvelopes(count: Int, groupMasterKey: GroupMasterKey): List<Envelope> {
    val envelopes = ArrayList<Envelope>(count)
    var now = System.currentTimeMillis()
    for (i in 0 until count) {
      envelopes += encrypt(Generator.encryptedTextMessage(now, groupMasterKey = groupMasterKey))
      now += 3
    }

    return envelopes
  }

  private fun getAliceServiceId(): ServiceId {
    return SignalStore.account.requireAci()
  }

  private fun getAlicePreKeyBundle(): PreKeyBundle {
    val aliceSignedPreKeyRecord = SignalDatabase.signedPreKeys.getAll(getAliceServiceId()).first()

    val aliceSignedKyberPreKeyRecord = SignalDatabase.kyberPreKeys.getAllLastResort(getAliceServiceId()).first().record

    return PreKeyBundle(
      registrationId = SignalStore.account.registrationId,
      deviceId = 1,
      preKeyId = PreKeyBundle.NULL_PRE_KEY_ID,
      preKeyPublic = null,
      signedPreKeyId = aliceSignedPreKeyRecord.id,
      signedPreKeyPublic = aliceSignedPreKeyRecord.keyPair.publicKey,
      signedPreKeySignature = aliceSignedPreKeyRecord.signature,
      identityKey = getAlicePublicKey(),
      kyberPreKeyId = aliceSignedKyberPreKeyRecord.id,
      kyberPreKeyPublic = aliceSignedKyberPreKeyRecord.keyPair.publicKey,
      kyberPreKeySignature = aliceSignedKyberPreKeyRecord.signature
    )
  }

  private fun getAliceProtocolAddress(): SignalProtocolAddress {
    return SignalProtocolAddress(SignalStore.account.requireAci().toString(), 1)
  }

  private fun getAlicePublicKey(): IdentityKey {
    return SignalStore.account.aciIdentityKey.publicKey
  }

  private fun getAliceProfileKey(): ProfileKey {
    return ProfileKeyUtil.getSelfProfileKey()
  }

  private fun getAliceUnidentifiedAccess(): SealedSenderAccess? {
    val theirProfileKey = getAliceProfileKey()
    val themUnidentifiedAccessKey = UnidentifiedAccess(UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey), senderCertificate.serialized, false)

    return SealedSenderAccess.forIndividual(themUnidentifiedAccessKey)
  }

  private class BobSignalServiceAccountDataStore(private val registrationId: Int, private val identityKeyPair: IdentityKeyPair) : SignalServiceAccountDataStore {
    private var aliceSessionRecord: SessionRecord? = null

    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair

    override fun getLocalRegistrationId(): Int = registrationId
    override fun isTrustedIdentity(address: SignalProtocolAddress?, identityKey: IdentityKey?, direction: IdentityKeyStore.Direction?): Boolean = true
    override fun loadSession(address: SignalProtocolAddress?): SessionRecord = aliceSessionRecord ?: SessionRecord()
    override fun saveIdentity(address: SignalProtocolAddress?, identityKey: IdentityKey?): IdentityChange = IdentityChange.NEW_OR_UNCHANGED
    override fun storeSession(address: SignalProtocolAddress?, record: SessionRecord?) {
      aliceSessionRecord = record
    }
    override fun getSubDeviceSessions(name: String?): List<Int> = emptyList()
    override fun containsSession(address: SignalProtocolAddress?): Boolean = aliceSessionRecord != null
    override fun getIdentity(address: SignalProtocolAddress?): IdentityKey = SignalStore.account.aciIdentityKey.publicKey
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
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) = throw UnsupportedOperationException()
    override fun deleteAllStaleOneTimeEcPreKeys(threshold: Long, minCount: Int) = throw UnsupportedOperationException()
    override fun markAllOneTimeEcPreKeysStaleIfNecessary(staleTime: Long) = throw UnsupportedOperationException()
    override fun storeSenderKey(sender: SignalProtocolAddress?, distributionId: UUID?, record: SenderKeyRecord?) = throw UnsupportedOperationException()
    override fun loadSenderKey(sender: SignalProtocolAddress?, distributionId: UUID?): SenderKeyRecord = throw UnsupportedOperationException()
    override fun archiveSession(address: SignalProtocolAddress?) = throw UnsupportedOperationException()
    override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>?): MutableMap<SignalProtocolAddress, SessionRecord> = throw UnsupportedOperationException()
    override fun getSenderKeySharedWith(distributionId: DistributionId?): MutableSet<SignalProtocolAddress> = throw UnsupportedOperationException()
    override fun markSenderKeySharedWith(distributionId: DistributionId?, addresses: MutableCollection<SignalProtocolAddress>?) = throw UnsupportedOperationException()
    override fun clearSenderKeySharedWith(addresses: MutableCollection<SignalProtocolAddress>?) = throw UnsupportedOperationException()
    override fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord) = throw UnsupportedOperationException()
    override fun removeKyberPreKey(kyberPreKeyId: Int) = throw UnsupportedOperationException()
    override fun markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime: Long) = throw UnsupportedOperationException()
    override fun deleteAllStaleOneTimeKyberPreKeys(threshold: Long, minCount: Int) = throw UnsupportedOperationException()
    override fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord> = throw UnsupportedOperationException()
    override fun isMultiDevice(): Boolean = throw UnsupportedOperationException()
  }
}
