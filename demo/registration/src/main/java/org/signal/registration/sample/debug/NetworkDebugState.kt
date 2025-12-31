/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton state manager for network debug overrides.
 * Tracks which methods have forced responses set.
 */
object NetworkDebugState {

  /**
   * Map of method name to the selected option name (e.g., "createSession" -> "success")
   * A value of "unset" or absence means no override is active.
   */
  private val _overrideSelections = MutableStateFlow<Map<String, String>>(emptyMap())
  val overrideSelections: StateFlow<Map<String, String>> = _overrideSelections.asStateFlow()

  /**
   * Map of method name to the actual result object to return.
   * This is populated when setOverride is called.
   */
  private val _overrideResults = MutableStateFlow<Map<String, Any>>(emptyMap())
  val overrideResults: StateFlow<Map<String, Any>> = _overrideResults.asStateFlow()

  /**
   * Set an override for a specific method.
   *
   * @param methodName The name of the NetworkController method
   * @param optionName The name of the selected option (e.g., "success", "rate_limited")
   * @param result The actual result object to return, or null to clear the override
   */
  fun setOverride(methodName: String, optionName: String, result: Any?) {
    if (optionName == "unset" || result == null || result == Unit) {
      clearOverride(methodName)
    } else {
      _overrideSelections.update { it + (methodName to optionName) }
      _overrideResults.update { it + (methodName to result) }
    }
  }

  /**
   * Clear the override for a specific method.
   */
  fun clearOverride(methodName: String) {
    _overrideSelections.update { it - methodName }
    _overrideResults.update { it - methodName }
  }

  /**
   * Clear all overrides.
   */
  fun clearAllOverrides() {
    _overrideSelections.value = emptyMap()
    _overrideResults.value = emptyMap()
  }

  /**
   * Get the current override result for a method, if any.
   */
  @Suppress("UNCHECKED_CAST")
  fun <T> getOverride(methodName: String): T? {
    return _overrideResults.value[methodName] as? T
  }

  /**
   * Check if a method has an active override.
   */
  fun hasOverride(methodName: String): Boolean {
    return _overrideResults.value.containsKey(methodName)
  }

  /**
   * Get the selected option name for a method.
   */
  fun getSelectedOption(methodName: String): String {
    return _overrideSelections.value[methodName] ?: "unset"
  }
}
