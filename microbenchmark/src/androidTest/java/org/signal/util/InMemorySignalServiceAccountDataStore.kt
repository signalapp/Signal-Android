package org.signal.util

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.push.DistributionId
import java.util.UUID

/**
 * An in-memory datastore specifically designed for tests.
 */
class InMemorySignalServiceAccountDataStore : SignalServiceAccountDataStore {

  private val identityKey: IdentityKeyPair = IdentityKeyPair.generate()
  private val identities: MutableMap<SignalProtocolAddress, IdentityKey> = mutableMapOf()
  private val oneTimePreKeys: MutableMap<Int, PreKeyRecord> = mutableMapOf()
  private val signedPreKeys: MutableMap<Int, SignedPreKeyRecord> = mutableMapOf()
  private var sessions: MutableMap<SignalProtocolAddress, SessionRecord> = mutableMapOf()
  private val senderKeys: MutableMap<SenderKeyLocator, SenderKeyRecord> = mutableMapOf()

  override fun getIdentityKeyPair(): IdentityKeyPair {
    return identityKey
  }

  override fun getLocalRegistrationId(): Int {
    return 1
  }

  override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
    val hadPrevious = identities.containsKey(address)
    identities[address] = identityKey
    return hadPrevious
  }

  override fun isTrustedIdentity(address: SignalProtocolAddress?, identityKey: IdentityKey?, direction: IdentityKeyStore.Direction?): Boolean {
    return true
  }

  override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
    return identities[address]
  }

  override fun loadPreKey(preKeyId: Int): PreKeyRecord {
    return oneTimePreKeys[preKeyId]!!
  }

  override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
    oneTimePreKeys[preKeyId] = record
  }

  override fun containsPreKey(preKeyId: Int): Boolean {
    return oneTimePreKeys.containsKey(preKeyId)
  }

  override fun removePreKey(preKeyId: Int) {
    oneTimePreKeys.remove(preKeyId)
  }

  override fun loadSession(address: SignalProtocolAddress): SessionRecord {
    return sessions.getOrPut(address) { SessionRecord() }
  }

  override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> {
    return addresses.map { sessions[it]!! }
  }

  override fun getSubDeviceSessions(name: String): List<Int> {
    return sessions
      .filter { it.key.name == name && it.key.deviceId != 1 && it.value.isValid() }
      .map { it.key.deviceId }
  }

  override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
    sessions[address] = record
  }

  override fun containsSession(address: SignalProtocolAddress): Boolean {
    return sessions[address]?.isValid() ?: false
  }

  override fun deleteSession(address: SignalProtocolAddress) {
    sessions -= address
  }

  override fun deleteAllSessions(name: String) {
    sessions = sessions.filter { it.key.name == name }.toMutableMap()
  }

  override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
    return signedPreKeys[signedPreKeyId]!!
  }

  override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
    return signedPreKeys.values.toList()
  }

  override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
    signedPreKeys[signedPreKeyId] = record
  }

  override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
    return signedPreKeys.containsKey(signedPreKeyId)
  }

  override fun removeSignedPreKey(signedPreKeyId: Int) {
    signedPreKeys -= signedPreKeyId
  }

  override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
    senderKeys[SenderKeyLocator(sender, distributionId)] = record
  }

  override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord {
    return senderKeys[SenderKeyLocator(sender, distributionId)]!!
  }

  override fun archiveSession(address: SignalProtocolAddress) {
    sessions[address]!!.archiveCurrentState()
  }

  override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>): Set<SignalProtocolAddress> {
    return sessions
      .filter { it.key.name in addressNames }
      .filter { it.value.isValid() }
      .map { it.key }
      .toSet()
  }

  override fun getSenderKeySharedWith(distributionId: DistributionId): Set<SignalProtocolAddress> {
    error("Not used")
  }

  override fun markSenderKeySharedWith(distributionId: DistributionId, addresses: Collection<SignalProtocolAddress>) {
    // Not used
  }

  override fun clearSenderKeySharedWith(addresses: Collection<SignalProtocolAddress>) {
    // Not used
  }

  override fun isMultiDevice(): Boolean {
    return false
  }

  private fun SessionRecord.isValid(): Boolean {
    return this.hasSenderChain() && this.sessionVersion == CiphertextMessage.CURRENT_VERSION
  }

  private data class SenderKeyLocator(val address: SignalProtocolAddress, val distributionId: UUID)
}
