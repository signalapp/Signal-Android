package org.thoughtcrime.securesms.preferences

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BackupUtil

class BackupsPreferenceViewModel : ViewModel() {

  private val internalBackupsEnabled = MutableLiveData<Boolean>()
  val backupsEnabled: LiveData<Boolean> = internalBackupsEnabled

  fun refreshBackupStatus() {
    viewModelScope.launch {
      val enabled = withContext(Dispatchers.IO) {
        val context = AppDependencies.application

        if (SignalStore.settings.isBackupEnabled) {
          if (BackupUtil.canUserAccessBackupDirectory(context)) {
            true
          } else {
            Log.w(TAG, "Cannot access backup directory. Disabling backups.")
            BackupUtil.disableBackups(context)
            false
          }
        } else {
          false
        }
      }

      internalBackupsEnabled.value = enabled
    }
  }

  companion object {
    private val TAG = Log.tag(BackupsPreferenceViewModel::class.java)
  }
}
