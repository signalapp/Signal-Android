package org.thoughtcrime.securesms.components.settings.app.data

import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.components.settings.app.storage.StorageUsageRepository

class DataAndStorageSettingsRepository(
  private val storageUsageRepository: StorageUsageRepository = StorageUsageRepository()
) {

  fun getTotalStorageUse(consumer: (Long) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      consumer(storageUsageRepository.getStorageUsage().total)
    }
  }
}
