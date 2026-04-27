/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView

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

  /**
   * Called once with the inner [RecyclerView] after first composition.
   * Java callers may implement this as a lambda: `rv -> fastScroller.setRecyclerView(rv)`.
   */
  fun interface RecyclerViewReadyCallback {
    fun onRecyclerViewReady(recyclerView: RecyclerView)
  }

  private var viewModel: ContactSearchViewModel? by mutableStateOf(null)
  private var currentFragmentManager: FragmentManager? = null
  private var currentDisplayOptions: ContactSearchAdapter.DisplayOptions? = null
  private var currentMapStateToConfiguration: ((ContactSearchState) -> ContactSearchConfiguration)? = null
  private var currentCallbacks: ContactSearchCallbacks = ContactSearchCallbacks.Simple()
  private var currentItemDecorations: List<RecyclerView.ItemDecoration> = emptyList()
  private var currentContentBottomPadding: Dp = 0.dp
  private var currentAdapterFactory: ContactSearchAdapter.AdapterFactory = ContactSearchAdapter.DefaultAdapterFactory
  private var currentScrollListeners: List<RecyclerView.OnScrollListener> = emptyList()
  private var recyclerView: RecyclerView? = null
  private var currentOnRecyclerViewReady: RecyclerViewReadyCallback? = null

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
   * @param itemDecorations         [RecyclerView.ItemDecoration]s added to the internal list.
   * @param contentBottomPaddingDp  Extra bottom padding (in dp) so last items scroll above overlaid
   *                                UI. Java callers pass a plain `float`.
   * @param adapterFactory          Factory for the adapter — swap for custom adapters.
   * @param scrollListeners         [RecyclerView.OnScrollListener]s attached to the inner list.
   * @param onRecyclerViewReady     Called once with the inner [RecyclerView] after first composition.
   *                                Useful for attaching fast-scrollers or custom item animators.
   */
  fun bind(
    viewModel: ContactSearchViewModel,
    fragmentManager: FragmentManager,
    displayOptions: ContactSearchAdapter.DisplayOptions,
    mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
    callbacks: ContactSearchCallbacks = ContactSearchCallbacks.Simple(),
    itemDecorations: List<RecyclerView.ItemDecoration> = emptyList(),
    contentBottomPaddingDp: Float = 0f,
    adapterFactory: ContactSearchAdapter.AdapterFactory = ContactSearchAdapter.DefaultAdapterFactory,
    scrollListeners: List<RecyclerView.OnScrollListener> = emptyList(),
    onRecyclerViewReady: RecyclerViewReadyCallback? = null
  ) {
    check(this.viewModel == null) { "ContactSearchView.bind() may only be called once" }
    currentFragmentManager = fragmentManager
    currentDisplayOptions = displayOptions
    currentMapStateToConfiguration = mapStateToConfiguration
    currentCallbacks = callbacks
    currentItemDecorations = itemDecorations
    currentContentBottomPadding = contentBottomPaddingDp.dp
    currentAdapterFactory = adapterFactory
    currentScrollListeners = scrollListeners
    currentOnRecyclerViewReady = onRecyclerViewReady
    this.viewModel = viewModel // triggers recomposition
  }

  override fun canScrollVertically(direction: Int): Boolean {
    return recyclerView?.canScrollVertically(direction) ?: super.canScrollVertically(direction)
  }

  @Composable
  override fun Content() {
    val vm = viewModel ?: return
    val displayOptions = currentDisplayOptions ?: return
    val mapStateToConfiguration = currentMapStateToConfiguration ?: return

    ContactSearch(
      viewModel = vm,
      mapStateToConfiguration = mapStateToConfiguration,
      displayOptions = displayOptions,
      callbacks = currentCallbacks,
      storyFragmentManager = currentFragmentManager,
      onListCommitted = { currentCallbacks.onAdapterListCommitted(it) },
      itemDecorations = currentItemDecorations,
      contentBottomPadding = currentContentBottomPadding,
      adapterFactory = currentAdapterFactory,
      scrollListeners = currentScrollListeners,
      onRecyclerViewReady = RecyclerViewReadyCallback { recyclerView ->
        this@ContactSearchView.recyclerView = recyclerView
        currentOnRecyclerViewReady?.onRecyclerViewReady(recyclerView)
      }
    )
  }
}
