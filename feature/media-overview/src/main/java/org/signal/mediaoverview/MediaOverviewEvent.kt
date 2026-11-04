/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview

import android.net.Uri

/**
 * Events for [MediaOverviewScreen]. Every event is logged, so events must not carry file names or
 * display names.
 */
sealed interface MediaOverviewEvent {

  /** The user tapped the toolbar back arrow. */
  data object BackClicked : MediaOverviewEvent

  /** The pager settled on a tab, by swipe or by tapping the tab. */
  data class TabSelected(val tab: MediaOverviewState.Tab) : MediaOverviewEvent

  /** The user tapped the sort order label. */
  data object SortClicked : MediaOverviewEvent

  /** The user picked a sort order from the dialog. */
  data class SortSelected(val sorting: MediaOverviewState.Sorting) : MediaOverviewEvent

  /** The user tapped the grid/list toggle. */
  data object LayoutToggled : MediaOverviewEvent

  /** The user tapped an item. */
  data class ItemClicked(val item: MediaOverviewItem) : MediaOverviewEvent

  /** The user long-pressed an item. */
  data class ItemLongClicked(val item: MediaOverviewItem) : MediaOverviewEvent

  /** The user tapped the play/pause (or download) button on an audio row. */
  data class AudioButtonClicked(val item: MediaOverviewItem) : MediaOverviewEvent

  /** The long-press menu was dismissed without picking anything. */
  data object ContextMenuDismissed : MediaOverviewEvent

  data object ContextMenuForwardClicked : MediaOverviewEvent

  data object ContextMenuShareClicked : MediaOverviewEvent

  data object ContextMenuSaveClicked : MediaOverviewEvent

  data object ContextMenuDeleteClicked : MediaOverviewEvent

  /** The user chose to start a multi-selection with the long-pressed item. */
  data object ContextMenuSelectClicked : MediaOverviewEvent

  data object ContextMenuJumpToMessageClicked : MediaOverviewEvent

  /** The user tapped save on the multi-select bar. */
  data object SaveSelectedClicked : MediaOverviewEvent

  /** The user tapped select all on the multi-select bar. */
  data object SelectAllClicked : MediaOverviewEvent

  /** The user tapped delete on the multi-select bar. */
  data object DeleteSelectedClicked : MediaOverviewEvent

  /** The user closed multi-select, by the close icon or by pressing back. */
  data object SelectionCleared : MediaOverviewEvent

  /** The user confirmed deleting the media in [MediaOverviewState.Dialog.ConfirmDelete]. */
  data object DeleteConfirmed : MediaOverviewEvent

  /** The user chose to save what they can from [MediaOverviewState.Dialog.PartiallyOffloaded]. */
  data object SaveAvailableConfirmed : MediaOverviewEvent

  /** The user tapped the storage settings button on an offloaded-media dialog. */
  data object ViewStorageSettingsClicked : MediaOverviewEvent

  /** Dismisses whatever is in [MediaOverviewState.dialog]. */
  data object DialogDismissed : MediaOverviewEvent

  /** The host finished saving media from [MediaOverviewAction.SaveMedia]. */
  data object SaveFinished : MediaOverviewEvent

  /** The voice note player moved to a new clip or position. Null when nothing is loaded. */
  data class AudioPlaybackChanged(
    val uri: Uri?,
    val isPlaying: Boolean,
    val progress: Float
  ) : MediaOverviewEvent {
    override fun toString(): String = "AudioPlaybackChanged(hasUri=${uri != null}, isPlaying=$isPlaying, progress=$progress)"
  }
}
