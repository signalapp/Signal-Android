package org.thoughtcrime.securesms.messages.protocol

import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.thoughtcrime.securesms.database.SignalDatabase
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalServiceSessionStore
import org.whispersystems.signalservice.api.push.ServiceId
import kotlin.jvm.Throws

/**
 * An in-memory session store that is intended to be used temporarily while decrypting messages.
 */
class BufferedSessionStore(private val selfServiceId: ServiceId) : SignalServiceSessionStore {

  private val store: MutableMap<SignalProtocolAddress, SessionRecord> = HashMap()

  /** All of the sessions that have been created or updated during operation. */
  private val updatedSessions: MutableMap<SignalProtocolAddress, SessionRecord> = mutableMapOf()

  /** All of the sessions that have deleted during operation. */
  private val deletedSessions: MutableSet<SignalProtocolAddress> = mutableSetOf()

  override fun loadSession(address: SignalProtocolAddress): SessionRecord {
    val session: SessionRecord = store[address]
      ?: SignalDatabase.sessions.load(selfServiceId, address)
      ?: SessionRecord()

    store[address] = session

    return session
  }

  @Throws(NoSessionException::class)
  override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): List<SessionRecord> {
    val found: MutableList<SessionRecord?> = ArrayList(addresses.size)
    val needsDatabaseLookup: MutableList<Pair<Int, SignalProtocolAddress>> = mutableListOf()

    addresses.forEachIndexed { index, address ->
      val cached: SessionRecord? = store[address]

      if (cached != null) {
        found[index] = cached
      } else {
        needsDatabaseLookup += (index to address)
      }
    }

    if (needsDatabaseLookup.isNotEmpty()) {
      val databaseRecords: List<SessionRecord?> = SignalDatabase.sessions.load(selfServiceId, needsDatabaseLookup.map { (_, address) -> address })
      needsDatabaseLookup.forEachIndexed { databaseLookupIndex, (addressIndex, _) ->
        found[addressIndex] = databaseRecords[databaseLookupIndex]
      }
    }

    val cachedAndLoaded = found.filterNotNull()

    if (cachedAndLoaded.size != addresses.size) {
      throw NoSessionException("Failed to find one or more sessions.")
    }

    return cachedAndLoaded
  }

  override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
    store[address] = record
    updatedSessions[address] = record
  }

  override fun containsSession(address: SignalProtocolAddress): Boolean {
    return if (store.containsKey(address)) {
      true
    } else {
      val fromDatabase: SessionRecord? = SignalDatabase.sessions.load(selfServiceId, address)

      if (fromDatabase != null) {
        store[address] = fromDatabase
        return fromDatabase.hasSenderChain()
      } else {
        false
      }
    }
  }

  override fun deleteSession(address: SignalProtocolAddress) {
    store.remove(address)
    deletedSessions += address
  }

  override fun getSubDeviceSessions(name: String): MutableList<Int> {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun deleteAllSessions(name: String) {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun archiveSession(address: SignalProtocolAddress?) {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>): Map<SignalProtocolAddress, SessionRecord> {
    error("Should not happen during the intended usage pattern of this class")
  }

  fun flushToDisk(persistentStore: SignalServiceAccountDataStore) {
    for ((address, record) in updatedSessions) {
      persistentStore.storeSession(address, record)
    }

    for (address in deletedSessions) {
      persistentStore.deleteSession(address)
    }

    updatedSessions.clear()
    deletedSessions.clear()
  }
}
