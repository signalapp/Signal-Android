/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class EnterBackupKeyViewModel : ViewModel() {

  companion object {
    private val INVALID_CHARACTERS = Regex("[^0-9a-zA-Z]")
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
    return backupKey.length == length
  }

  private fun String.removeIllegalCharacters(): String {
    return this.replace(INVALID_CHARACTERS, "")
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
