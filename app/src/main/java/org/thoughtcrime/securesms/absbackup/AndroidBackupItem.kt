package org.thoughtcrime.securesms.absbackup

/**
 * Abstracts away the implementation of pieces of data we want to hand off to various backup services.
 * Here we can control precisely which data gets backed up and more importantly, what does not.
 */
interface AndroidBackupItem {
  fun getKey(): String
  fun getDataForBackup(): ByteArray
  fun restoreData(data: ByteArray)
}
