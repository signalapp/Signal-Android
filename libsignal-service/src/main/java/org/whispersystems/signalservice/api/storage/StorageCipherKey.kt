package org.whispersystems.signalservice.api.storage

interface StorageCipherKey {
  fun serialize(): ByteArray
}
