/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.fragment.app.FragmentManager
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.flow.filter
import org.signal.core.ui.compose.LocalFragmentManager
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingEntryProvider

/**
 * A Compose-compatible wrapper view for the ContactSearch framework.
 *
 * Usage:
 * 1. Create a [ContactSearchViewModel] in the host fragment (via `viewModels { ... }` or
 *    `ViewModelProvider`).
 * 2. Declare `<ContactSearchView>` in your fragment's XML layout.
 * 3. Call [bind] from `onViewCreated`, passing the ViewModel and the Fragment.
 * 4. Call ViewModel methods directly for all operations, including query updates.
 */
class ContactSearchView : AbstractComposeView {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private var viewModel: ContactSearchViewModel? by mutableStateOf(null)
  private var currentFragmentManager: FragmentManager? = null
  private var currentDisplayOptions: ContactSearchAdapter.DisplayOptions? = null
  private var currentMapStateToConfiguration: ((ContactSearchState) -> ContactSearchConfiguration)? = null

  private var currentAdditionalEntries: MappingEntryProvider<Any> = persistentHashMapOf()
  private var lazyListState: LazyListState? = null

  private var currentCallbacks: ContactSearchCallbacks = ContactSearchCallbacks.Simple()

  private var currentClickCallbacks: ContactSearchAdapter.ClickCallbacks? = null
  private var currentLongClickCallbacks: ContactSearchAdapter.LongClickCallbacks? = null
  private var currentStoryContextMenuCallbacks: ContactSearchAdapter.StoryContextMenuCallbacks? = null
  private var currentCallButtonClickCallbacks: ContactSearchAdapter.CallButtonClickCallbacks? = null

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  /**
   * Configures and activates the contact search. Must be called exactly once from the host
   * fragment's `onViewCreated`. The [viewModel] must be created and held by the caller so it
   * can be accessed directly for selection queries and mutations.
   *
   * Pre-selected/fixed contacts (e.g. existing group members) are owned by the ViewModel and
   * passed via [ContactSearchViewModel.Factory].
   *
   * @param viewModel               The externally-created ViewModel. Fixed contacts are a
   *                                constructor parameter of [ContactSearchViewModel.Factory].
   * @param fragmentManager         Used for showing story-related dialogs. Pass
   *                                [childFragmentManager] from a Fragment or
   *                                [supportFragmentManager] from an Activity.
   * @param displayOptions          Controls checkbox and secondary-info visibility.
   * @param mapStateToConfiguration Maps the current [ContactSearchState] to the active
   *                                [ContactSearchConfiguration], re-evaluated on every state change.
   * @param callbacks               Hooks for filtering and reacting to selection changes.
   * @param additionalEntries       Extra [MappingEntryProvider] entries layered on top of the base
   *                                set from [ContactSearchModels.composeEntries]. The base set is
   *                                always applied; on key collisions the base entry wins.
   */
  fun bind(
    viewModel: ContactSearchViewModel,
    fragmentManager: FragmentManager,
    displayOptions: ContactSearchAdapter.DisplayOptions,
    mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
    callbacks: ContactSearchCallbacks = ContactSearchCallbacks.Simple(),
    additionalEntries: MappingEntryProvider<Any> = persistentHashMapOf(),
    clickCallbacks: ContactSearchAdapter.ClickCallbacks? = null,
    longClickCallbacks: ContactSearchAdapter.LongClickCallbacks? = null,
    storyContextMenuCallbacks: ContactSearchAdapter.StoryContextMenuCallbacks? = null,
    callButtonClickCallbacks: ContactSearchAdapter.CallButtonClickCallbacks? = null
  ) {
    check(this.viewModel == null) { "ContactSearchView.bind() may only be called once" }
    currentFragmentManager = fragmentManager
    currentDisplayOptions = displayOptions
    currentMapStateToConfiguration = mapStateToConfiguration
    currentCallbacks = callbacks
    currentAdditionalEntries = additionalEntries

    if (clickCallbacks != null) {
      currentClickCallbacks = clickCallbacks
    }

    if (longClickCallbacks != null) {
      currentLongClickCallbacks = longClickCallbacks
    }

    if (storyContextMenuCallbacks != null) {
      currentStoryContextMenuCallbacks = storyContextMenuCallbacks
    }

    if (callButtonClickCallbacks != null) {
      currentCallButtonClickCallbacks = callButtonClickCallbacks
    }

    this.viewModel = viewModel // triggers recomposition
  }

  override fun canScrollVertically(direction: Int): Boolean {
    return lazyListState?.canScrollVertically(direction) ?: super.canScrollVertically(direction)
  }

  @Composable
  override fun Content() {
    val vm = viewModel ?: return
    val displayOptions = currentDisplayOptions ?: return
    val mapStateToConfiguration = currentMapStateToConfiguration ?: return

    lazyListState = rememberLazyListState()

    val view = LocalView.current
    val context = LocalContext.current
    LaunchedEffect(lazyListState) {
      snapshotFlow { lazyListState!!.isScrollInProgress }
        .filter { it }
        .collect {
          ViewUtil.hideKeyboard(context, view)
        }
    }

    CompositionLocalProvider(LocalFragmentManager provides currentFragmentManager) {
      ContactSearch(
        viewModel = vm,
        mapStateToConfiguration = mapStateToConfiguration,
        displayOptions = displayOptions,
        lazyListState = lazyListState ?: rememberLazyListState(),
        callbacks = currentCallbacks,
        onListCommitted = { currentCallbacks.onAdapterListCommitted(it) },
        additionalEntries = currentAdditionalEntries,
        clickCallbacks = currentClickCallbacks ?: rememberDefaultContactSearchItemClickCallbacks(vm, currentCallbacks),
        longClickCallbacks = currentLongClickCallbacks ?: rememberDefaultContactSearchItemLongClickCallbacks(),
        storyContextMenuCallbacks = currentStoryContextMenuCallbacks ?: rememberDefaultContactSearchItemStoryContextMenuCallbacks(vm),
        callButtonClickCallbacks = currentCallButtonClickCallbacks ?: rememberDefaultContactSearchItemCallButtonClickCallbacks(),
        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()).fillMaxSize()
      )
    }
  }
}

private fun LazyListState.canScrollVertically(direction: Int): Boolean {
  return when {
    direction < 0 -> canScrollBackward
    else -> canScrollForward
  }
}
