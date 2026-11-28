/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.remoteconfig

sealed interface InternalRemoteConfigEvent {
  data object Initialize : InternalRemoteConfigEvent
  data object BackClicked : InternalRemoteConfigEvent
  data class FilterChanged(val filter: String) : InternalRemoteConfigEvent

  /** Opens the editor for the config with this key. */
  data class ConfigClicked(val key: String) : InternalRemoteConfigEvent

  /** The value being typed (or picked) in the editor, before it's saved. */
  data class EditorValueChanged(val value: String) : InternalRemoteConfigEvent
  data object EditorSaveClicked : InternalRemoteConfigEvent
  data object EditorClearClicked : InternalRemoteConfigEvent
  data object EditorDismissed : InternalRemoteConfigEvent
  data object ClearAllClicked : InternalRemoteConfigEvent
  data object ClearAllConfirmed : InternalRemoteConfigEvent
  data object ClearAllDismissed : InternalRemoteConfigEvent
  data object RestartConfirmed : InternalRemoteConfigEvent
  data object RestartDismissed : InternalRemoteConfigEvent
}
