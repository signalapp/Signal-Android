package org.thoughtcrime.securesms.storage

import org.whispersystems.signalservice.api.storage.SignalRecord
import java.io.IOException

/**
 * Handles processing a remote record, which involves applying any local changes that need to be
 * made based on the remote records.
 */
interface StorageRecordProcessor<E : SignalRecord<*>> {
  @Throws(IOException::class)
  fun process(remoteRecords: Collection<E>, keyGenerator: StorageKeyGenerator)
}
