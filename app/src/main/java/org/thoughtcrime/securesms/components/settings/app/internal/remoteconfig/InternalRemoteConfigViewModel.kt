/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.remoteconfig

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig

/** Lets internal users override any [RemoteConfig] value. */
class InternalRemoteConfigViewModel : EventDrivenViewModel<InternalRemoteConfigEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(InternalRemoteConfigViewModel::class)

    /** Overridden flags first, alphabetical within each group. */
    private val DISPLAY_ORDER = compareByDescending<RemoteConfigListItem> { it.isOverridden }.thenBy { it.key }
  }

  private val _state = MutableStateFlow(InternalRemoteConfigState())
  val state: StateFlow<InternalRemoteConfigState> = _state.asStateFlow()

  private val _actions = Channel<InternalRemoteConfigAction>(Channel.BUFFERED)
  val actions: Flow<InternalRemoteConfigAction> = _actions.receiveAsFlow()

  /** Every config, unfiltered. The state holds this narrowed by the filter. */
  private var allConfigs: List<RemoteConfigListItem> = emptyList()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    onEvent(InternalRemoteConfigEvent.Initialize)
  }

  override suspend fun processEvent(event: InternalRemoteConfigEvent) {
    when (event) {
      InternalRemoteConfigEvent.Initialize -> {
        reload()
      }

      InternalRemoteConfigEvent.BackClicked -> {
        _actions.send(InternalRemoteConfigAction.Exit)
      }

      is InternalRemoteConfigEvent.FilterChanged -> {
        _state.update { it.copy(filter = event.filter).withVisibleConfigs() }
      }

      is InternalRemoteConfigEvent.ConfigClicked -> {
        val config = allConfigs.firstOrNull { it.key == event.key }

        if (config == null) {
          Log.w(TAG, "Tried to edit a config we don't know about! (key: ${event.key})")
          return
        }

        _state.update { it.copy(editor = InternalRemoteConfigState.Editor(config = config, value = config.initialEditorValue)) }
      }

      is InternalRemoteConfigEvent.EditorValueChanged -> {
        _state.update { it.copy(editor = it.editor?.copy(value = event.value)) }
      }

      InternalRemoteConfigEvent.EditorSaveClicked -> {
        val editor = _state.value.editor ?: return
        val changed = editor.value != editor.config.overrideValue

        applyOverrides(RemoteConfig.overrides + (editor.config.key to editor.value))

        _state.update { it.copy(editor = null) }
        reload()

        if (changed) {
          promptRestartFor(listOf(editor.config))
        }
      }

      InternalRemoteConfigEvent.EditorClearClicked -> {
        val editor = _state.value.editor ?: return

        applyOverrides(RemoteConfig.overrides - editor.config.key)

        _state.update { it.copy(editor = null) }
        reload()

        promptRestartFor(listOf(editor.config))
      }

      InternalRemoteConfigEvent.EditorDismissed -> {
        _state.update { it.copy(editor = null) }
      }

      InternalRemoteConfigEvent.ClearAllClicked -> {
        _state.update { it.copy(showClearAllDialog = true) }
      }

      InternalRemoteConfigEvent.ClearAllConfirmed -> {
        val cleared = allConfigs.filter { it.isOverridden }

        applyOverrides(emptyMap())

        _state.update { it.copy(showClearAllDialog = false) }
        reload()

        promptRestartFor(cleared)
      }

      InternalRemoteConfigEvent.ClearAllDismissed -> {
        _state.update { it.copy(showClearAllDialog = false) }
      }

      InternalRemoteConfigEvent.RestartConfirmed -> {
        _state.update { it.copy(restartPromptKeys = emptyList()) }
        _actions.send(InternalRemoteConfigAction.RestartApp)
      }

      InternalRemoteConfigEvent.RestartDismissed -> {
        _state.update { it.copy(restartPromptKeys = emptyList()) }
      }
    }
  }

  /** Non-hot-swappable flags are only meant to change between sessions, so anything that already read one still holds the old value. */
  private fun promptRestartFor(changed: List<RemoteConfigListItem>) {
    val needsRestart = changed.filterNot { it.hotSwappable }.map { it.key }

    if (needsRestart.isNotEmpty()) {
      _state.update { it.copy(restartPromptKeys = needsRestart) }
    }
  }

  /** Persists what [RemoteConfig] accepted rather than what was asked for, so disk and memory can't drift. */
  private suspend fun applyOverrides(updated: Map<String, String>) {
    withContext(Dispatchers.Default) {
      RemoteConfig.overrides = updated
      SignalStore.internal.remoteConfigOverrides = RemoteConfig.overrides
    }
  }

  private suspend fun reload() {
    allConfigs = withContext(Dispatchers.Default) { readConfigs() }
    _state.update { it.withVisibleConfigs() }
  }

  /** Reads every config through its own transformer, so what's rendered is what the app reads. */
  private fun readConfigs(): List<RemoteConfigListItem> {
    val remoteValues = RemoteConfig.memoryValues
    val overrides = RemoteConfig.overrides

    return RemoteConfig.overridableConfigs.map { (key, config) ->
      val default = config.resolveDefault()

      RemoteConfigListItem(
        key = key,
        effectiveValue = config.resolve().toDisplayString(),
        defaultValue = default.toDisplayString(),
        rawDefaultValue = if (default is Boolean) config.rawBooleanFor(default) else "",
        remoteValue = remoteValues[key]?.toString(),
        overrideValue = overrides[key],
        isBoolean = default is Boolean,
        hotSwappable = config.hotSwappable,
        active = config.active
      )
    }
  }

  /** Inverted flags like kill switches turn "false" into true, so the raw value has to be found through the transformer. */
  private fun RemoteConfig.Config<*>.rawBooleanFor(value: Boolean): String {
    return if (transformer("true") == value) "true" else "false"
  }

  private fun Any?.toDisplayString(): String = this?.toString() ?: "null"

  private fun InternalRemoteConfigState.withVisibleConfigs(): InternalRemoteConfigState {
    val query = filter.trim()
    val visible = if (query.isEmpty()) {
      allConfigs
    } else {
      allConfigs.filter { it.key.contains(query, ignoreCase = true) || it.effectiveValue.contains(query, ignoreCase = true) }
    }

    return copy(
      loaded = true,
      configs = visible.sortedWith(DISPLAY_ORDER),
      overrideCount = allConfigs.count { it.isOverridden }
    )
  }
}
