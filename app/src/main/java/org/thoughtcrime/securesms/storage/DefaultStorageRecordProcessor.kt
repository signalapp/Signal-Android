package org.thoughtcrime.securesms.storage

import org.signal.core.util.logging.Log
import org.whispersystems.signalservice.api.storage.SignalRecord
import java.io.IOException
import java.util.Optional
import java.util.TreeSet

/**
 * An implementation of [StorageRecordProcessor] that solidifies a pattern and reduces
 * duplicate code in individual implementations.
 *
 * Concerning the implementation of [.compare], it's purpose is to detect if
 * two items would map to the same logical entity (i.e. they would correspond to the same record in
 * our local store). We use it for a [TreeSet], so mainly it's just important that the '0'
 * case is correct. Other cases are whatever, just make it something stable.
 */
abstract class DefaultStorageRecordProcessor<E : SignalRecord<*>> : StorageRecordProcessor<E>, Comparator<E> {
  companion object {
    private val TAG = Log.tag(DefaultStorageRecordProcessor::class.java)
  }

  /**
   * One type of invalid remote data this handles is two records mapping to the same local data. We
   * have to trim this bad data out, because if we don't, we'll upload an ID set that only has one
   * of the IDs in it, but won't properly delete the dupes, which will then fail our validation
   * checks.
   *
   * This is a bit tricky -- as we process records, ID's are written back to the local store, so we
   * can't easily be like "oh multiple records are mapping to the same local storage ID". And in
   * general we rely on SignalRecords to implement an equals() that includes the StorageId, so using
   * a regular set is out. Instead, we use a [TreeSet], which allows us to define a custom
   * comparator for checking equality. Then we delegate to the subclass to tell us if two items are
   * the same based on their actual data (i.e. two contacts having the same UUID, or two groups
   * having the same MasterKey).
   */
  @Throws(IOException::class)
  override fun process(remoteRecords: Collection<E>, keyGenerator: StorageKeyGenerator) {
    val matchedRecords: MutableSet<E> = TreeSet(this)

    for ((i, remote) in remoteRecords.withIndex()) {
      if (isInvalid(remote)) {
        warn(i, remote, "Found invalid key! Ignoring it.")
      } else {
        val local = getMatching(remote, keyGenerator)

        if (local.isPresent) {
          val merged: E = merge(remote, local.get(), keyGenerator)

          if (matchedRecords.contains(local.get())) {
            warn(i, remote, "Multiple remote records map to the same local record! Ignoring this one.")
          } else {
            matchedRecords.add(local.get())

            if (merged != remote) {
              info(i, remote, "[Remote Update] " + remote.describeDiff(merged))
            }

            if (merged != local.get()) {
              val update = StorageRecordUpdate(local.get(), merged)
              info(i, remote, "[Local Update] $update")
              updateLocal(update)
            }
          }
        } else {
          info(i, remote, "No matching local record. Inserting.")
          insertLocal(remote)
        }
      }
    }
  }

  fun doParamsMatch(base: E, test: E): Boolean {
    return base.serializedUnknowns.contentEquals(test.serializedUnknowns) && base.proto == test.proto
  }

  private fun info(i: Int, record: E, message: String) {
    Log.i(TAG, "[$i][${record.javaClass.getSimpleName()}] $message")
  }

  private fun warn(i: Int, record: E, message: String) {
    Log.w(TAG, "[$i][${record.javaClass.getSimpleName()}] $message")
  }

  /**
   * @return True if the record is invalid and should be removed from storage service, otherwise false.
   */
  abstract fun isInvalid(remote: E): Boolean

  /**
   * Only records that pass the validity check (i.e. return false from [.isInvalid]
   * make it to here, so you can assume all records are valid.
   */
  abstract fun getMatching(remote: E, keyGenerator: StorageKeyGenerator): Optional<E>

  abstract fun merge(remote: E, local: E, keyGenerator: StorageKeyGenerator): E

  @Throws(IOException::class)
  abstract fun insertLocal(record: E)
  abstract fun updateLocal(update: StorageRecordUpdate<E>)
}
