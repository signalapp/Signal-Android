/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

open class CodeEntryController(private val codeLength: Int = 6) {
  private val _codeState = MutableStateFlow(List(codeLength) { "" })
  val codeState: StateFlow<List<String>> = _codeState.asStateFlow()

  fun setDigit(idx: Int, digit: String) {
    _codeState.update { it.toMutableList().also { it[idx] = digit } }
  }

  fun clearDigit(idx: Int) {
    _codeState.update { it.toMutableList().also { it[idx] = "" } }
  }

  fun appendDigit(digit: String) {
    val idx = _codeState.value.indexOfFirst { it.isEmpty() }
    if (idx != -1) setDigit(idx, digit)
  }

  fun deleteLastDigit() {
    val idx = _codeState.value.indexOfLast { it.isNotEmpty() }
    if (idx != -1) clearDigit(idx)
  }

  fun clearAllDigits() {
    _codeState.update { List(codeLength) { "" } }
  }

  fun autofillCode(code: String) {
    if (code.length == codeLength && code.all { it.isDigit() }) {
      _codeState.update { code.map { it.toString() } }
    } else {
      clearAllDigits()
    }
  }
}

