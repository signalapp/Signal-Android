/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.absbackup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import org.signal.core.util.logging.Log
import org.signal.registration.NetworkController
import org.signal.registration.sample.storage.RegistrationPreferences
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Uses the [Android Backup Service](https://developer.android.com/guide/topics/data/keyvaluebackup) to back up SVR2 credentials.
 * These credentials can be combined with a PIN to prove ownership of a phone number in order to complete the registration process.
 */
class RegistrationBackupAgent : BackupAgent() {

  override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor) {
    Log.i(TAG, "Performing backup to Android Backup Service.")
    val contentsHash = cumulativeHashCode()
    if (oldState == null || !hashMatches(oldState, contentsHash)) {
      val backupData = getDataForBackup()
      data.writeEntityHeader(BACKUP_KEY, backupData.size)
      data.writeEntityData(backupData, backupData.size)
    }

    DataOutputStream(FileOutputStream(newState.fileDescriptor)).use { it.writeInt(contentsHash) }
    Log.i(TAG, "Backup finished.")
  }

  override fun onRestore(dataInput: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor) {
    Log.i(TAG, "Restoring from Android Backup Service.")
    while (dataInput.readNextHeader()) {
      if (dataInput.key == BACKUP_KEY) {
        val buffer = ByteArray(dataInput.dataSize)
        dataInput.readEntityData(buffer, 0, dataInput.dataSize)
        restoreData(buffer)
      }
    }
    DataOutputStream(FileOutputStream(newState.fileDescriptor)).use { it.writeInt(cumulativeHashCode()) }
    Log.i(TAG, "Android Backup Service restore complete.")
  }

  private fun cumulativeHashCode(): Int {
    return getDataForBackup().decodeToString().hashCode()
  }

  private fun hashMatches(oldState: ParcelFileDescriptor, expected: Int): Boolean {
    return try {
      val hash = DataInputStream(FileInputStream(oldState.fileDescriptor)).use { it.readInt() }
      hash == expected
    } catch (e: IOException) {
      false
    }
  }

  private fun getDataForBackup(): ByteArray {
    val credentials = RegistrationPreferences.restoredSvr2Credentials
    val byteArrayOutputStream = ByteArrayOutputStream()
    DataOutputStream(byteArrayOutputStream).use { output ->
      output.writeInt(credentials.size)
      credentials.forEach { credential ->
        output.writeUTF(credential.username)
        output.writeUTF(credential.password)
      }
    }
    return byteArrayOutputStream.toByteArray()
  }

  private fun restoreData(data: ByteArray) {
    // Only restore if we don't already have credentials
    if (RegistrationPreferences.restoredSvr2Credentials.isNotEmpty()) {
      return
    }

    try {
      val byteArrayInputStream = ByteArrayInputStream(data)
      val credentials = mutableListOf<NetworkController.SvrCredentials>()
      DataInputStream(byteArrayInputStream).use { input ->
        val count = input.readInt()
        repeat(count) {
          val username = input.readUTF()
          val password = input.readUTF()
          credentials.add(NetworkController.SvrCredentials(username = username, password = password))
        }
      }
      RegistrationPreferences.restoredSvr2Credentials = credentials
      Log.i(TAG, "Successfully restored ${credentials.size} SVR2 credentials from backup service.")
    } catch (e: IOException) {
      Log.w(TAG, "Cannot restore SVR2 credentials from backup service.", e)
    }
  }

  companion object {
    private val TAG = Log.tag(RegistrationBackupAgent::class)
    private const val BACKUP_KEY = "Svr2Credentials"
  }
}
