package org.thoughtcrime.securesms.absbackup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.absbackup.backupables.KbsAuthTokens
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Uses the [Android Backup Service](https://developer.android.com/guide/topics/data/keyvaluebackup) and backs up everything in [items]
 */
class SignalBackupAgent : BackupAgent() {
  private val items: List<AndroidBackupItem> = listOf(
    KbsAuthTokens,
  )

  override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor) {
    val contentsHash = cumulativeHashCode()
    if (oldState == null) {
      performBackup(data)
    } else {
      val hash = try {
        DataInputStream(FileInputStream(oldState.fileDescriptor)).use { it.readInt() }
      } catch (e: IOException) {
        Log.w(TAG, "No old state, may be first backup request or bug with not writing to newState at end.", e)
      }
      if (hash != contentsHash) {
        performBackup(data)
      }
    }

    DataOutputStream(FileOutputStream(newState.fileDescriptor)).use { it.writeInt(contentsHash) }
  }

  private fun performBackup(data: BackupDataOutput) {
    items.forEach {
      val backupData = it.getDataForBackup()
      data.writeEntityHeader(it.getKey(), backupData.size)
      data.writeEntityData(backupData, backupData.size)
    }
  }

  override fun onRestore(dataInput: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor) {
    while (dataInput.readNextHeader()) {
      val buffer = ByteArray(dataInput.dataSize)
      dataInput.readEntityData(buffer, 0, dataInput.dataSize)
      items.find { dataInput.key == it.getKey() }?.restoreData(buffer)
    }
    DataOutputStream(FileOutputStream(newState.fileDescriptor)).use { it.writeInt(cumulativeHashCode()) }
  }

  private fun cumulativeHashCode(): Int {
    return items.fold("") { acc: String, androidBackupItem: AndroidBackupItem -> acc + androidBackupItem.getDataForBackup().decodeToString() }.hashCode()
  }

  companion object {
    private const val TAG = "SignalBackupAgent"
  }
}
