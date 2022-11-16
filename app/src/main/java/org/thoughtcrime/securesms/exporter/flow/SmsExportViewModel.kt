package org.thoughtcrime.securesms.exporter.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Hold shared state for the SMS export flow.
 *
 * Note: Will be expanded on eventually to support different behavior when entering via megaphone.
 */
class SmsExportViewModel(val isReExport: Boolean) : ViewModel() {
  class Factory(private val isReExport: Boolean) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(SmsExportViewModel(isReExport)))
    }
  }
}
