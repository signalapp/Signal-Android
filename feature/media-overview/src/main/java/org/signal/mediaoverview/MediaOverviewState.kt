/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview

import android.net.Uri

/**
 * Everything [MediaOverviewScreen] needs to render.
 */
data class MediaOverviewState(
  val isAllThreads: Boolean = false,
  /** The conversation's display name. Unused when [isAllThreads], which has a fixed title. */
  val title: String = "",
  val selectedTab: Tab = Tab.MEDIA,
  val sorting: Sorting = Sorting.NEWEST,
  /** Whether the [Tab.MEDIA] tab shows rows instead of a grid. Every other tab always shows rows. */
  val detailLayout: Boolean = false,
  /** The selected items, mapped to their sizes. */
  val selection: Map<MediaOverviewItem.Key, Long> = emptyMap(),
  /** The item whose long-press menu is open, if any. */
  val contextMenuItem: MediaOverviewItem? = null,
  val audioPlayback: AudioPlayback? = null,
  val dialog: Dialog = Dialog.None
) {

  val tabs: List<Tab>
    get() = if (isAllThreads) Tab.entries - Tab.LINKS else Tab.entries

  val selectedKeys: Set<MediaOverviewItem.Key>
    get() = selection.keys

  val selectedTotalSize: Long
    get() = selection.values.sum()

  val contextMenuKey: MediaOverviewItem.Key?
    get() = contextMenuItem?.key

  val isMultiSelect: Boolean
    get() = selection.isNotEmpty()

  val showFileSizes: Boolean
    get() = sorting == Sorting.LARGEST

  val isLayoutToggleVisible: Boolean
    get() = selectedTab == Tab.MEDIA

  fun isDetailLayout(tab: Tab): Boolean = tab != Tab.MEDIA || detailLayout

  override fun toString(): String {
    return "MediaOverviewState(isAllThreads=$isAllThreads, selectedTab=$selectedTab, sorting=$sorting, detailLayout=$detailLayout, selected=${selection.size}, contextMenuKey=$contextMenuKey, audioPlayback=$audioPlayback, dialog=$dialog)"
  }

  enum class Tab {
    MEDIA,
    FILES,
    AUDIO,
    LINKS,
    ALL
  }

  enum class Sorting {
    NEWEST,
    OLDEST,
    LARGEST
  }

  /** How a run of media is labelled. Resolved to text by the screen. */
  sealed interface SectionHeader {
    data object Today : SectionHeader
    data object Yesterday : SectionHeader
    data object ThisWeek : SectionHeader
    data object ThisMonth : SectionHeader

    /** [month] is zero-based, as in [java.util.Calendar]. */
    data class Month(val year: Int, val month: Int) : SectionHeader
    data object Small : SectionHeader
    data object Medium : SectionHeader
    data object Large : SectionHeader
  }

  /** The audio clip the voice note player is currently on. */
  data class AudioPlayback(
    val uri: Uri,
    val isPlaying: Boolean,
    /** Fraction of the clip played, from 0 to 1. */
    val progress: Float
  ) {
    override fun toString(): String = "AudioPlayback(isPlaying=$isPlaying, progress=$progress)"
  }

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    data object SortOrder : Dialog

    data class ConfirmDelete(val keys: Set<MediaOverviewItem.Key>) : Dialog

    data class DeleteProgress(val processed: Int, val total: Int) : Dialog

    /** Waiting on every item in the tab to be looked up before selecting all of them. */
    data object SelectAllLoading : Dialog

    /** None of the media to save is on the device. */
    data object AllOffloaded : Dialog

    /** Some of the media to save is not on the device. Confirming saves the rest. */
    data class PartiallyOffloaded(val saveableKeys: Set<MediaOverviewItem.Key>) : Dialog
  }
}
