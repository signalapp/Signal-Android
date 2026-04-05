package org.whispersystems.signalservice.api.messages.multidevice

import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.storageservice.StorageKey

data class KeysMessage(
  val storageService: StorageKey?,
  val accountEntropyPool: AccountEntropyPool?,
  val mediaRootBackupKey: MediaRootBackupKey?
)
