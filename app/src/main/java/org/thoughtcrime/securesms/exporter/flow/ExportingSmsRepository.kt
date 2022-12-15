package org.thoughtcrime.securesms.exporter.flow

import android.app.Application
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import java.io.File

class ExportingSmsRepository(private val context: Application = ApplicationDependencies.getApplication()) {

  @Suppress("UsePropertyAccessSyntax")
  fun getSmsExportSizeEstimations(): Single<SmsExportSizeEstimations> {
    return Single.fromCallable {
      val internalStorageFile = if (Build.VERSION.SDK_INT < 24) {
        File(context.applicationInfo.dataDir)
      } else {
        context.dataDir
      }

      val internalFreeSpace: Long = if (Build.VERSION.SDK_INT < 26) {
        internalStorageFile.usableSpace
      } else {
        val storageManagerFreeSpace = ContextCompat.getSystemService(context, StorageManager::class.java)?.let { storageManager ->
          storageManager.getAllocatableBytes(storageManager.getUuidForPath(internalStorageFile))
        }
        storageManagerFreeSpace ?: internalStorageFile.usableSpace
      }

      SmsExportSizeEstimations(internalFreeSpace, SignalDatabase.messages.getUnexportedInsecureMessagesEstimatedSize() + SignalDatabase.messages.getUnexportedInsecureMessagesEstimatedSize())
    }.subscribeOn(Schedulers.io())
  }

  data class SmsExportSizeEstimations(val estimatedInternalFreeSpace: Long, val estimatedRequiredSpace: Long)
}
