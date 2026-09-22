/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.remoteconfig

/** State for the remote config override screen. */
data class InternalRemoteConfigState(
  /** Whether the configs have been read at least once, so an empty list means the filter matched nothing. */
  val loaded: Boolean = false,

  /** Filtered by [filter] and ordered for display: overridden flags first, then alphabetical. */
  val configs: List<RemoteConfigListItem> = emptyList(),
  val filter: String = "",
  val overrideCount: Int = 0,
  val editor: Editor? = null,
  val showClearAllDialog: Boolean = false,

  /** Keys just changed that aren't hot-swappable, so a restart is needed for them to fully apply. */
  val restartPromptKeys: List<String> = emptyList()
) {
  val showEmptyState: Boolean
    get() = loaded && configs.isEmpty()

  val showRestartDialog: Boolean
    get() = restartPromptKeys.isNotEmpty()

  /** The open editor and the value being edited in it, before it's saved. */
  data class Editor(
    val config: RemoteConfigListItem,
    val value: String
  )

  /** The list is every config in the app, so it's summarized rather than dumped into the log. */
  override fun toString(): String {
    return "InternalRemoteConfigState(loaded=$loaded, configs=${configs.size}, filterLength=${filter.length}, overrideCount=$overrideCount, editor=${editor?.config?.key}, showClearAllDialog=$showClearAllDialog, restartPromptKeys=$restartPromptKeys)"
  }
}

/** A single remote config flag, as rendered in the list. */
data class RemoteConfigListItem(
  /** The key used to identify the config on the service. */
  val key: String,

  /** What the app currently reads, taking any override into account. */
  val effectiveValue: String,

  /** What the app would read if the service sent us nothing. */
  val defaultValue: String,

  /** The raw value that produces [defaultValue], since [defaultValue] has already been transformed. */
  val rawDefaultValue: String,

  /** The raw value from the service, if any. */
  val remoteValue: String?,

  /** The raw override in effect, if any. */
  val overrideValue: String?,

  /** Booleans get a simpler editor than a text field. */
  val isBoolean: Boolean,

  val hotSwappable: Boolean,
  val active: Boolean
) {
  val isOverridden: Boolean = overrideValue != null

  /** What the editor starts with: the override if there is one, otherwise the service's value. */
  val initialEditorValue: String = overrideValue ?: remoteValue ?: rawDefaultValue
}
