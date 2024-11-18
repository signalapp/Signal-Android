package org.whispersystems.signalservice.api.messages.multidevice

import org.whispersystems.signalservice.api.AccountEntropyPool
import org.whispersystems.signalservice.api.backup.MediaRootBackupKey
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.storage.StorageKey

data class KeysMessage(
  val storageService: StorageKey?,
  val master: MasterKey?,
  val accountEntropyPool: AccountEntropyPool?,
  val mediaRootBackupKey: MediaRootBackupKey?
)
