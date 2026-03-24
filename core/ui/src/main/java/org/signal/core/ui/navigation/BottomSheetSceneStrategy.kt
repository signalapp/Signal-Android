/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.navigation.BottomSheetSceneStrategy.Companion.bottomSheet

/**
 * A [CompositionLocal] that provides a dismiss callback for the current bottom sheet.
 * When invoked, the sheet will animate its hide transition and then pop the backstack.
 *
 * This is the preferred way for bottom sheet content to programmatically dismiss itself,
 * as directly manipulating the backstack or using the Activity's back press dispatcher
 * will skip the sheet's exit animation.
 */
val LocalBottomSheetDismiss = staticCompositionLocalOf<() -> Unit> { {} }

/** An [OverlayScene] that renders an [entry] within a [ModalBottomSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetScene<T : Any>(
  override val key: T,
  override val previousEntries: List<NavEntry<T>>,
  override val overlaidEntries: List<NavEntry<T>>,
  private val entry: NavEntry<T>,
  private val modalBottomSheetProperties: ModalBottomSheetProperties,
  private val onBack: () -> Unit
) : OverlayScene<T> {

  override val entries: List<NavEntry<T>> = listOf(entry)

  override val content: @Composable (() -> Unit) = {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val animatedDismiss: () -> Unit = {
      scope.launch { sheetState.hide() }.invokeOnCompletion {
        if (!sheetState.isVisible) {
          onBack()
        }
      }
    }

    CompositionLocalProvider(LocalBottomSheetDismiss provides animatedDismiss) {
      BottomSheets.BottomSheet(
        onDismissRequest = onBack,
        sheetState = sheetState,
        properties = modalBottomSheetProperties
      ) {
        entry.Content()
      }
    }
  }
}

/**
 * A [SceneStrategy] that displays entries that have added [bottomSheet] to their [NavEntry.metadata]
 * within a [ModalBottomSheet] instance.
 *
 * This strategy should always be added before any non-overlay scene strategies.
 */
@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

  override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
    val lastEntry = entries.lastOrNull()
    val bottomSheetProperties = lastEntry?.metadata?.get(BOTTOM_SHEET_KEY) as? ModalBottomSheetProperties
    return bottomSheetProperties?.let { properties ->
      @Suppress("UNCHECKED_CAST")
      BottomSheetScene(
        key = lastEntry.contentKey as T,
        previousEntries = entries.dropLast(1),
        overlaidEntries = entries.dropLast(1),
        entry = lastEntry,
        modalBottomSheetProperties = properties,
        onBack = onBack
      )
    }
  }

  companion object {
    /**
     * Function to be called on the [NavEntry.metadata] to mark this entry as something that
     * should be displayed within a [ModalBottomSheet].
     *
     * @param modalBottomSheetProperties properties that should be passed to the containing
     * [ModalBottomSheet].
     */
    @OptIn(ExperimentalMaterial3Api::class)
    fun bottomSheet(
      modalBottomSheetProperties: ModalBottomSheetProperties = ModalBottomSheetProperties()
    ): Map<String, Any> = mapOf(BOTTOM_SHEET_KEY to modalBottomSheetProperties)

    internal const val BOTTOM_SHEET_KEY = "bottomsheet"
  }
}
