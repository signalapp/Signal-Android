package org.thoughtcrime.securesms.messages.protocol

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.UUID

/**
 * The wrapper around all of the Buffered protocol stores. Designed to perform operations in memory,
 * then [flushToDisk] at set intervals.
 */
class BufferedSignalServiceAccountDataStore(selfServiceId: ServiceId) : SignalServiceAccountDataStore {

  private val identityStore: BufferedIdentityKeyStore = if (selfServiceId == SignalStore.account().pni) {
    BufferedIdentityKeyStore(selfServiceId, SignalStore.account().pniIdentityKey, SignalStore.account().pniRegistrationId)
  } else {
    BufferedIdentityKeyStore(selfServiceId, SignalStore.account().aciIdentityKey, SignalStore.account().registrationId)
  }

  private val oneTimePreKeyStore: BufferedOneTimePreKeyStore = BufferedOneTimePreKeyStore(selfServiceId)
  private val signedPreKeyStore: BufferedSignedPreKeyStore = BufferedSignedPreKeyStore(selfServiceId)
  private val sessionStore: BufferedSessionStore = BufferedSessionStore(selfServiceId)
  private val senderKeyStore: BufferedSenderKeyStore = BufferedSenderKeyStore()

  override fun getIdentityKeyPair(): IdentityKeyPair {
    return identityStore.identityKeyPair
  }

  override fun getLocalRegistrationId(): Int {
    return identityStore.localRegistrationId
  }

  override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
    return identityStore.saveIdentity(address, identityKey)
  }

  override fun isTrustedIdentity(address: SignalProtocolAddress, identityKey: IdentityKey, direction: IdentityKeyStore.Direction): Boolean {
    return identityStore.isTrustedIdentity(address, identityKey, direction)
  }

  override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
    return identityStore.getIdentity(address)
  }

  override fun loadPreKey(preKeyId: Int): PreKeyRecord {
    return oneTimePreKeyStore.loadPreKey(preKeyId)
  }

  override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
    return oneTimePreKeyStore.storePreKey(preKeyId, record)
  }

  override fun containsPreKey(preKeyId: Int): Boolean {
    return oneTimePreKeyStore.containsPreKey(preKeyId)
  }

  override fun removePreKey(preKeyId: Int) {
    oneTimePreKeyStore.removePreKey(preKeyId)
  }

  override fun loadSession(address: SignalProtocolAddress): SessionRecord {
    return sessionStore.loadSession(address)
  }

  override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): List<SessionRecord> {
    return sessionStore.loadExistingSessions(addresses)
  }

  override fun getSubDeviceSessions(name: String): MutableList<Int> {
    return sessionStore.getSubDeviceSessions(name)
  }

  override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
    sessionStore.storeSession(address, record)
  }

  override fun containsSession(address: SignalProtocolAddress): Boolean {
    return sessionStore.containsSession(address)
  }

  override fun deleteSession(address: SignalProtocolAddress) {
    return sessionStore.deleteSession(address)
  }

  override fun deleteAllSessions(name: String) {
    sessionStore.deleteAllSessions(name)
  }

  override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
    return signedPreKeyStore.loadSignedPreKey(signedPreKeyId)
  }

  override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
    return signedPreKeyStore.loadSignedPreKeys()
  }

  override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
    signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record)
  }

  override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
    return signedPreKeyStore.containsSignedPreKey(signedPreKeyId)
  }

  override fun removeSignedPreKey(signedPreKeyId: Int) {
    signedPreKeyStore.removeSignedPreKey(signedPreKeyId)
  }

  override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
    senderKeyStore.storeSenderKey(sender, distributionId, record)
  }

  override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? {
    return senderKeyStore.loadSenderKey(sender, distributionId)
  }

  override fun archiveSession(address: SignalProtocolAddress?) {
    sessionStore.archiveSession(address)
  }

  override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>): Set<SignalProtocolAddress> {
    return sessionStore.getAllAddressesWithActiveSessions(addressNames)
  }

  override fun getSenderKeySharedWith(distributionId: DistributionId?): MutableSet<SignalProtocolAddress> {
    return senderKeyStore.getSenderKeySharedWith(distributionId)
  }

  override fun markSenderKeySharedWith(distributionId: DistributionId, addresses: MutableCollection<SignalProtocolAddress>) {
    senderKeyStore.markSenderKeySharedWith(distributionId, addresses)
  }

  override fun clearSenderKeySharedWith(addresses: MutableCollection<SignalProtocolAddress>) {
    senderKeyStore.clearSenderKeySharedWith(addresses)
  }

  override fun isMultiDevice(): Boolean {
    error("Should not happen during the intended usage pattern of this class")
  }

  fun flushToDisk(persistentStore: SignalServiceAccountDataStore) {
    identityStore.flushToDisk(persistentStore)
    oneTimePreKeyStore.flushToDisk(persistentStore)
    signedPreKeyStore.flushToDisk(persistentStore)
    sessionStore.flushToDisk(persistentStore)
    senderKeyStore.flushToDisk(persistentStore)
  }
}
