package org.signal.util

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
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
  private val oneTimeEcPreKeys: MutableMap<Int, PreKeyRecord> = mutableMapOf()
  private val signedPreKeys: MutableMap<Int, SignedPreKeyRecord> = mutableMapOf()
  private var sessions: MutableMap<SignalProtocolAddress, SessionRecord> = mutableMapOf()
  private val senderKeys: MutableMap<SenderKeyLocator, SenderKeyRecord> = mutableMapOf()
  private val kyberPreKeys: MutableMap<Int, KyberPreKeyRecord> = mutableMapOf()

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
    return oneTimeEcPreKeys[preKeyId]!!
  }

  override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
    oneTimeEcPreKeys[preKeyId] = record
  }

  override fun containsPreKey(preKeyId: Int): Boolean {
    return oneTimeEcPreKeys.containsKey(preKeyId)
  }

  override fun removePreKey(preKeyId: Int) {
    oneTimeEcPreKeys.remove(preKeyId)
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

  override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? {
    return senderKeys[SenderKeyLocator(sender, distributionId)]
  }

  override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
    return kyberPreKeys[kyberPreKeyId]!!
  }

  override fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
    return kyberPreKeys.values.toList()
  }

  override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord?) {
    error("Not used")
  }

  override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
    return kyberPreKeys.containsKey(kyberPreKeyId)
  }

  override fun markKyberPreKeyUsed(kyberPreKeyId: Int) {
    kyberPreKeys.remove(kyberPreKeyId)
  }

  override fun deleteAllStaleOneTimeEcPreKeys(threshold: Long, minCount: Int) {
    error("Not used")
  }

  override fun markAllOneTimeEcPreKeysStaleIfNecessary(staleTime: Long) {
    error("Not used")
  }

  override fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord) {
    error("Not used")
  }

  override fun removeKyberPreKey(kyberPreKeyId: Int) {
    error("Not used")
  }

  override fun markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime: Long) {
    error("Not used")
  }

  override fun deleteAllStaleOneTimeKyberPreKeys(threshold: Long, minCount: Int) {
    error("Not used")
  }

  override fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord> {
    error("Not used")
  }

  override fun archiveSession(address: SignalProtocolAddress) {
    sessions[address]!!.archiveCurrentState()
  }

  override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>): MutableMap<SignalProtocolAddress, SessionRecord> {
    return sessions
      .filter { it.key.name in addressNames }
      .filter { it.value.isValid() }
      .toMutableMap()
  }

  override fun getSenderKeySharedWith(distributionId: DistributionId): Set<SignalProtocolAddress> {
    error("Not used")
  }

  override fun markSenderKeySharedWith(distributionId: DistributionId, addresses: Collection<SignalProtocolAddress>) {
    // Called, but not needed
  }

  override fun clearSenderKeySharedWith(addresses: Collection<SignalProtocolAddress>) {
    // Called, but not needed
  }

  override fun isMultiDevice(): Boolean {
    return false
  }

  private fun SessionRecord.isValid(): Boolean {
    return this.hasSenderChain()
  }

  private data class SenderKeyLocator(val address: SignalProtocolAddress, val distributionId: UUID)
}
