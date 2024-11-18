/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import com.squareup.wire.FieldEncoding
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.IOException
import org.signal.core.util.isNotEmpty
import org.signal.libsignal.protocol.InvalidKeyException
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord
import org.whispersystems.signalservice.internal.storage.protos.ReadOperation
import org.whispersystems.signalservice.internal.storage.protos.StorageItem
import org.whispersystems.signalservice.internal.storage.protos.StorageItems
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord
import org.whispersystems.signalservice.internal.storage.protos.WriteOperation
import java.lang.Exception

/**
 * Collection of higher-level storage service operations. Each method tends to make multiple
 * calls to [StorageServiceApi], wrapping the responses in easier-to-use result types.
 */
class StorageServiceRepository(private val storageServiceApi: StorageServiceApi) {

  companion object {
    private const val STORAGE_READ_MAX_ITEMS: Int = 1000
  }

  /**
   * Fetches the version of the remote manifest.
   */
  fun getManifestVersion(): NetworkResult<Long> {
    return storageServiceApi
      .getAuth()
      .then { storageServiceApi.getStorageManifest(it) }
      .map { it.version }
  }

  /**
   * Fetches and returns the fully-decrypted [SignalStorageManifest], if possible.
   * Note: You should prefer using [getStorageManifestIfDifferentVersion] when possible.
   */
  fun getStorageManifest(storageKey: StorageKey): ManifestResult {
    val manifest: StorageManifest = storageServiceApi
      .getAuth()
      .then { storageServiceApi.getStorageManifest(it) }
      .let { result ->
        when (result) {
          is NetworkResult.Success -> result.result
          is NetworkResult.ApplicationError -> throw result.throwable
          is NetworkResult.NetworkError -> return ManifestResult.NetworkError(result.exception)
          is NetworkResult.StatusCodeError -> {
            return when (result.code) {
              404 -> ManifestResult.NotFoundError
              else -> ManifestResult.StatusCodeError(result.code, result.exception)
            }
          }
        }
      }

    return try {
      val decrypted = manifest.toLocal(storageKey)
      ManifestResult.Success(decrypted)
    } catch (e: InvalidKeyException) {
      ManifestResult.DecryptionError(e)
    }
  }

  /**
   * Fetches and returns the fully-decrypted [SignalStorageManifest] if the remote version is higher than the [manifestVersion] passed in.
   * The intent is that you only need the manifest if it's newer than what you already have.
   */
  fun getStorageManifestIfDifferentVersion(storageKey: StorageKey, manifestVersion: Long): ManifestIfDifferentVersionResult {
    val manifest = storageServiceApi
      .getAuth()
      .then { storageServiceApi.getStorageManifestIfDifferentVersion(it, manifestVersion) }
      .let { result ->
        when (result) {
          is NetworkResult.Success -> result.result
          is NetworkResult.ApplicationError -> throw result.throwable
          is NetworkResult.NetworkError -> return ManifestIfDifferentVersionResult.NetworkError(result.exception)
          is NetworkResult.StatusCodeError -> {
            return when (result.code) {
              204 -> ManifestIfDifferentVersionResult.SameVersion
              else -> ManifestIfDifferentVersionResult.StatusCodeError(result.code, result.exception)
            }
          }
        }
      }

    return try {
      val decrypted = manifest.toLocal(storageKey)
      ManifestIfDifferentVersionResult.DifferentVersion(decrypted)
    } catch (e: InvalidKeyException) {
      ManifestIfDifferentVersionResult.DecryptionError(e)
    }
  }

  /**
   * Fetches and returns the fully-decrypted [SignalStorageRecord]s matching the list of provided [storageIds]
   */
  fun readStorageRecords(storageKey: StorageKey, recordIkm: RecordIkm?, storageIds: List<StorageId>): StorageRecordResult {
    val auth: String = when (val result = storageServiceApi.getAuth()) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> return StorageRecordResult.NetworkError(result.exception)
      is NetworkResult.StatusCodeError -> return StorageRecordResult.StatusCodeError(result.code, result.exception)
    }

    val knownIds = storageIds.filterNot { it.isUnknown }
    val batches = knownIds.chunked(STORAGE_READ_MAX_ITEMS)
    val results = batches.map { batch ->
      readStorageRecordsBatch(auth, storageKey, recordIkm, batch)
    }

    results
      .firstOrNull { it !is StorageRecordResult.Success }
      ?.let { firstFailedResult ->
        return firstFailedResult
      }

    val unknownRecordPlaceholders = storageIds
      .filter { it.isUnknown }
      .map { SignalStorageRecord.forUnknown(it) }

    val allResults = results
      .map { (it as StorageRecordResult.Success).records }
      .flatten() + unknownRecordPlaceholders

    return StorageRecordResult.Success(allResults)
  }

  /**
   * Writes the provided data to storage service.
   */
  fun writeStorageRecords(
    storageKey: StorageKey,
    signalManifest: SignalStorageManifest,
    insertItems: List<SignalStorageRecord>,
    deleteRawIds: List<ByteArray>
  ): WriteStorageRecordsResult {
    return writeStorageRecords(storageKey, signalManifest, insertItems, deleteRawIds, clearAllExisting = false)
  }

  /**
   * Writes the provided data to storage service, overwriting _all other data_ in the process.
   * Reserved for very specific circumstances! (Like fixing undecryptable data).
   */
  fun resetAndWriteStorageRecords(
    storageKey: StorageKey,
    manifest: SignalStorageManifest,
    insertItems: List<SignalStorageRecord>
  ): WriteStorageRecordsResult {
    return writeStorageRecords(storageKey, manifest, insertItems, emptyList(), clearAllExisting = true)
  }

  /**
   * Writes the current manifest with no insertions or deletes. Intended to be done after rotating your AEP.
   */
  fun writeUnchangedManifest(storageKey: StorageKey, manifest: SignalStorageManifest): WriteStorageRecordsResult {
    return writeStorageRecords(storageKey, manifest, emptyList(), emptyList(), clearAllExisting = false)
  }

  private fun writeStorageRecords(
    storageKey: StorageKey,
    signalManifest: SignalStorageManifest,
    insertItems: List<SignalStorageRecord>,
    deleteRawIds: List<ByteArray>,
    clearAllExisting: Boolean
  ): WriteStorageRecordsResult {
    val manifestIds = signalManifest.storageIds.map { id ->
      val builder = ManifestRecord.Identifier.Builder()
      builder.raw = id.raw.toByteString()
      if (id.isUnknown) {
        builder.type = ManifestRecord.Identifier.Type.UNKNOWN
        builder.addUnknownField(2, FieldEncoding.VARINT, id.type)
      } else {
        builder.type(ManifestRecord.Identifier.Type.fromValue(id.type)!!)
      }
      builder.build()
    }

    val manifestRecord = ManifestRecord(
      sourceDevice = signalManifest.sourceDeviceId,
      version = signalManifest.version,
      identifiers = manifestIds,
      recordIkm = signalManifest.recordIkm?.value?.toByteString() ?: ByteString.EMPTY
    )

    val manifestKey = storageKey.deriveManifestKey(signalManifest.version)

    val encryptedManifest = StorageManifest(
      version = manifestRecord.version,
      value_ = SignalStorageCipher.encrypt(manifestKey, manifestRecord.encode()).toByteString()
    )

    val writeOperation = WriteOperation.Builder().apply {
      manifest = encryptedManifest
      insertItem = insertItems.map { it.toRemote(storageKey, signalManifest.recordIkm) }

      if (clearAllExisting) {
        clearAll = true
      } else {
        deleteKey = deleteRawIds.map { it.toByteString() }
      }
    }.build()

    val result = storageServiceApi
      .getAuth()
      .then { auth -> storageServiceApi.writeStorageItems(auth, writeOperation) }

    return when (result) {
      is NetworkResult.Success -> WriteStorageRecordsResult.Success
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> WriteStorageRecordsResult.NetworkError(result.exception)
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          409 -> WriteStorageRecordsResult.ConflictError
          else -> WriteStorageRecordsResult.StatusCodeError(result.code, result.exception)
        }
      }
    }
  }

  private fun readStorageRecordsBatch(auth: String, storageKey: StorageKey, recordIkm: RecordIkm?, storageIds: List<StorageId>): StorageRecordResult {
    check(storageIds.size <= STORAGE_READ_MAX_ITEMS)
    check(storageIds.none { it.isUnknown })

    val typesByKey: Map<ByteString, Int> = storageIds.associate { it.raw.toByteString() to it.type }

    val readOperation = ReadOperation(
      readKey = storageIds.map { it.raw.toByteString() }
    )

    val storageItems: StorageItems = storageServiceApi
      .readStorageItems(auth, readOperation)
      .let { itemResult ->
        when (itemResult) {
          is NetworkResult.Success -> itemResult.result
          is NetworkResult.ApplicationError -> throw itemResult.throwable
          is NetworkResult.NetworkError -> return StorageRecordResult.NetworkError(itemResult.exception)
          is NetworkResult.StatusCodeError -> return StorageRecordResult.StatusCodeError(itemResult.code, itemResult.exception)
        }
      }

    return try {
      val items = storageItems.items.map { item ->
        val type = typesByKey[item.key]!!
        item.toLocal(type, storageKey, recordIkm)
      }
      StorageRecordResult.Success(items)
    } catch (e: InvalidKeyException) {
      StorageRecordResult.DecryptionError(e)
    }
  }

  @Throws(IOException::class, InvalidKeyException::class)
  private fun StorageManifest.toLocal(storageKey: StorageKey): SignalStorageManifest {
    val rawRecord = SignalStorageCipher.decrypt(storageKey.deriveManifestKey(this.version), this.value_.toByteArray())
    val manifestRecord = ManifestRecord.ADAPTER.decode(rawRecord)
    val ids: List<StorageId> = manifestRecord.identifiers.map { id ->
      StorageId.forType(id.raw.toByteArray(), id.typeValue)
    }

    return SignalStorageManifest(
      version = manifestRecord.version,
      sourceDeviceId = manifestRecord.sourceDevice,
      recordIkm = manifestRecord.recordIkm.takeIf { it.isNotEmpty() }?.toByteArray()?.let { RecordIkm(it) },
      storageIds = ids
    )
  }

  @Throws(IOException::class, InvalidKeyException::class)
  private fun StorageItem.toLocal(type: Int, storageKey: StorageKey, recordIkm: RecordIkm?): SignalStorageRecord {
    val rawId = this.key.toByteArray()
    val key = recordIkm?.deriveStorageItemKey(rawId) ?: storageKey.deriveItemKey(rawId)
    val rawRecord = SignalStorageCipher.decrypt(key, this.value_.toByteArray())
    val record = StorageRecord.ADAPTER.decode(rawRecord)
    val id = StorageId.forType(rawId, type)

    return SignalStorageRecord(id, record)
  }

  private fun SignalStorageRecord.toRemote(storageKey: StorageKey, recordIkm: RecordIkm?): StorageItem {
    val key = recordIkm?.deriveStorageItemKey(this.id.raw) ?: storageKey.deriveItemKey(this.id.raw)
    val encryptedRecord = SignalStorageCipher.encrypt(key, this.proto.encode())

    return StorageItem.Builder()
      .key(this.id.raw.toByteString())
      .value_(encryptedRecord.toByteString())
      .build()
  }

  sealed interface WriteStorageRecordsResult {
    data object Success : WriteStorageRecordsResult
    data class NetworkError(val exception: IOException) : WriteStorageRecordsResult
    data object ConflictError : WriteStorageRecordsResult
    data class StatusCodeError(val code: Int, val exception: NonSuccessfulResponseCodeException) : WriteStorageRecordsResult
  }

  sealed interface ManifestResult {
    data class Success(val manifest: SignalStorageManifest) : ManifestResult
    data class NetworkError(val exception: IOException) : ManifestResult
    data class DecryptionError(val exception: Exception) : ManifestResult
    data object NotFoundError : ManifestResult
    data class StatusCodeError(val code: Int, val exception: NonSuccessfulResponseCodeException) : ManifestResult
  }

  sealed interface ManifestIfDifferentVersionResult {
    data class DifferentVersion(val manifest: SignalStorageManifest) : ManifestIfDifferentVersionResult
    data object SameVersion : ManifestIfDifferentVersionResult
    data class NetworkError(val exception: IOException) : ManifestIfDifferentVersionResult
    data class DecryptionError(val exception: Exception) : ManifestIfDifferentVersionResult
    data class StatusCodeError(val code: Int, val exception: NonSuccessfulResponseCodeException) : ManifestIfDifferentVersionResult
  }

  sealed interface StorageRecordResult {
    data class Success(val records: List<SignalStorageRecord>) : StorageRecordResult
    data class NetworkError(val exception: IOException) : StorageRecordResult
    data class DecryptionError(val exception: Exception) : StorageRecordResult
    data class StatusCodeError(val code: Int, val exception: NonSuccessfulResponseCodeException) : StorageRecordResult
  }
}
