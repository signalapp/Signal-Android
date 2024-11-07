/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.signal.core.util.Hex
import java.io.IOException

class EnterBackupKeyViewModel : ViewModel() {

  companion object {
    // TODO [backups] Set actual valid characters for key input
    private val VALID_CHARACTERS = setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
  }

  private val _state = mutableStateOf(
    EnterBackupKeyState(
      backupKey = "",
      length = 64,
      chunkLength = 4
    )
  )

  val state: State<EnterBackupKeyState> = _state

  fun updateBackupKey(key: String) {
    _state.update {
      val newKey = key.removeIllegalCharacters().take(length)
      copy(backupKey = newKey, backupKeyValid = validate(length, newKey))
    }
  }

  private fun validate(length: Int, backupKey: String): Boolean {
    if (backupKey.length != length) {
      return false
    }

    try {
      // TODO [backups] Actually validate key with requirements instead of just hex
      Hex.fromStringCondensed(backupKey)
    } catch (e: IOException) {
      return false
    }

    return true
  }

  private fun String.removeIllegalCharacters(): String {
    return filter { VALID_CHARACTERS.contains(it) }
  }

  private inline fun <T> MutableState<T>.update(update: T.() -> T) {
    this.value = this.value.update()
  }

  data class EnterBackupKeyState(
    val backupKey: String = "",
    val backupKeyValid: Boolean = false,
    val length: Int,
    val chunkLength: Int
  )
}
