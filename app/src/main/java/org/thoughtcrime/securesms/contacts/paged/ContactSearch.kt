/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchView.RecyclerViewReadyCallback
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.stories.settings.custom.PrivateStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.my.MyStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.privacy.ChooseInitialMyStoryMembershipBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter
import org.signal.core.ui.R as CoreUiR

/**
 * A composable that displays a paged, selectable contact list driven by a [ContactSearchViewModel].
 *
 * Intended for use in two ways:
 * 1. Directly inside a Compose layout — the caller creates and holds a [ContactSearchViewModel]
 *    via `viewModel()` or a parent composable and passes it in.
 * 2. Via [ContactSearchView] in XML/View-based layouts — [ContactSearchView] creates the ViewModel
 *    and delegates its `Content()` to this function.
 *
 * The [PagingMappingAdapter] is created internally via `remember` and re-created if
 * [displayOptions] or [adapterFactory] change.
 *
 * @param viewModel               Drives the list — managed by the caller.
 * @param mapStateToConfiguration Maps the current [ContactSearchState] to the active
 *                                [ContactSearchConfiguration], re-evaluated whenever state changes.
 * @param modifier                Modifier applied to the composable root.
 * @param displayOptions          Controls checkbox and secondary-info visibility.
 * @param callbacks               Hooks for filtering and reacting to selection changes.
 * @param storyFragmentManager    [FragmentManager] used to show story-related dialogs.
 *                                Pass `null` to disable story context menus and dialogs.
 * @param onListCommitted         Called after each list commit with the committed item count.
 * @param itemDecorations         [RecyclerView.ItemDecoration]s added to the internal list.
 * @param contentBottomPadding    Extra bottom padding so last items scroll above overlaid UI.
 *                                Automatically disables `clipToPadding` when non-zero.
 * @param adapterFactory          Factory for the adapter — swap for custom adapters (e.g.
 *                                [ContactSelectionListAdapter]).
 * @param scrollListeners         [RecyclerView.OnScrollListener]s attached to the inner list.
 * @param onRecyclerViewReady     Called once with the inner [RecyclerView] after first composition.
 *                                Useful for attaching fast-scrollers or custom item animators.
 */
@Composable
fun ContactSearch(
  viewModel: ContactSearchViewModel,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  modifier: Modifier = Modifier,
  displayOptions: ContactSearchAdapter.DisplayOptions = ContactSearchAdapter.DisplayOptions(),
  callbacks: ContactSearchCallbacks = remember { ContactSearchCallbacks.Simple() },
  storyFragmentManager: FragmentManager? = null,
  onListCommitted: (Int) -> Unit = {},
  itemDecorations: List<RecyclerView.ItemDecoration> = emptyList(),
  contentBottomPadding: Dp = 0.dp,
  adapterFactory: ContactSearchAdapter.AdapterFactory = ContactSearchAdapter.DefaultAdapterFactory,
  scrollListeners: List<RecyclerView.OnScrollListener> = emptyList(),
  onRecyclerViewReady: RecyclerViewReadyCallback? = null
) {
  val mappingModels by viewModel.mappingModels.collectAsStateWithLifecycle()
  val controller by viewModel.controller.collectAsStateWithLifecycle()
  val configState by viewModel.configurationState.collectAsStateWithLifecycle()

  val currentMapStateToConfiguration by rememberUpdatedState(mapStateToConfiguration)
  val currentOnListCommitted by rememberUpdatedState(onListCommitted)
  // Held as State references (not delegated) so click-callback lambdas captured inside
  // remember() always read the latest value without recreating the adapter.
  val currentCallbacks = rememberUpdatedState(callbacks)
  val currentStoryFragmentManager = rememberUpdatedState(storyFragmentManager)

  val context = LocalContext.current
  val contextState = rememberUpdatedState(context)

  val adapter = remember(viewModel.fixedContacts, displayOptions, adapterFactory) {
    adapterFactory.create(
      context = context,
      fixedContacts = viewModel.fixedContacts,
      displayOptions = displayOptions,
      callbacks = DefaultClickCallbacks(viewModel, currentCallbacks, currentStoryFragmentManager),
      longClickCallbacks = ContactSearchAdapter.LongClickCallbacksAdapter(),
      storyContextMenuCallbacks = DefaultStoryContextMenuCallbacks(viewModel, currentStoryFragmentManager, contextState),
      callButtonClickCallbacks = ContactSearchAdapter.EmptyCallButtonClickCallbacks
    )
  }

  LaunchedEffect(mappingModels) {
    adapter.submitList(mappingModels) {
      currentOnListCommitted(mappingModels.size)
    }
  }

  LaunchedEffect(controller) {
    controller?.let { adapter.setPagingController(it) }
  }

  LaunchedEffect(configState) {
    viewModel.setConfiguration(currentMapStateToConfiguration(configState))
  }

  val recyclerView = remember(context) {
    RecyclerView(context).apply {
      layoutManager = LinearLayoutManager(context)
    }
  }

  DisposableEffect(recyclerView, itemDecorations) {
    itemDecorations.forEach { recyclerView.addItemDecoration(it) }
    onDispose {
      itemDecorations.forEach { recyclerView.removeItemDecoration(it) }
    }
  }

  DisposableEffect(recyclerView, scrollListeners) {
    scrollListeners.forEach { recyclerView.addOnScrollListener(it) }
    onDispose {
      scrollListeners.forEach { recyclerView.removeOnScrollListener(it) }
    }
  }

  val bottomPaddingPx = with(LocalDensity.current) { contentBottomPadding.roundToPx() }

  LaunchedEffect(recyclerView) {
    onRecyclerViewReady?.onRecyclerViewReady(recyclerView)
  }

  AndroidView(
    factory = { recyclerView },
    update = { rv ->
      if (rv.adapter !== adapter) {
        rv.adapter = adapter
      }
      rv.setPadding(0, 0, 0, bottomPaddingPx)
      rv.clipToPadding = bottomPaddingPx == 0
      rv.clipChildren = bottomPaddingPx == 0
    },
    modifier = modifier.fillMaxSize()
  )
}

private class DefaultClickCallbacks(
  private val viewModel: ContactSearchViewModel,
  private val callbacks: State<ContactSearchCallbacks>,
  private val fragmentManager: State<FragmentManager?>
) : ContactSearchAdapter.ClickCallbacks {

  companion object {
    private val TAG = Log.tag(DefaultClickCallbacks::class.java)
  }

  override fun onStoryClicked(view: View, story: ContactSearchData.Story, isSelected: Boolean) {
    Log.d(TAG, "onStoryClicked()")
    if (story.recipient.isMyStory && !SignalStore.story.userHasBeenNotifiedAboutStories) {
      fragmentManager.value?.let { ChooseInitialMyStoryMembershipBottomSheetDialogFragment.show(it) }
    } else {
      toggle(view, story, isSelected)
    }
  }

  override fun onKnownRecipientClicked(view: View, knownRecipient: ContactSearchData.KnownRecipient, isSelected: Boolean) {
    Log.d(TAG, "onKnownRecipientClicked()")
    toggle(view, knownRecipient, isSelected)
  }

  override fun onExpandClicked(expand: ContactSearchData.Expand) {
    Log.d(TAG, "onExpandClicked()")
    viewModel.expandSection(expand.sectionKey)
  }

  override fun onChatTypeClicked(view: View, chatTypeRow: ContactSearchData.ChatTypeRow, isSelected: Boolean) {
    Log.d(TAG, "onChatTypeClicked()")
    if (isSelected) {
      viewModel.setKeysNotSelected(setOf(chatTypeRow.contactSearchKey))
    } else {
      viewModel.setKeysSelected(callbacks.value.onBeforeContactsSelected(view, setOf(chatTypeRow.contactSearchKey)))
    }
  }

  private fun toggle(view: View, data: ContactSearchData, isSelected: Boolean) {
    if (isSelected) {
      Log.d(TAG, "toggle(OFF) ${data.contactSearchKey}")
      callbacks.value.onContactDeselected(view, data.contactSearchKey)
      viewModel.setKeysNotSelected(setOf(data.contactSearchKey))
    } else {
      Log.d(TAG, "toggle(ON) ${data.contactSearchKey}")
      viewModel.setKeysSelected(callbacks.value.onBeforeContactsSelected(view, setOf(data.contactSearchKey)))
    }
  }
}

private class DefaultStoryContextMenuCallbacks(
  private val viewModel: ContactSearchViewModel,
  private val fragmentManager: State<FragmentManager?>,
  private val context: State<Context>
) : ContactSearchAdapter.StoryContextMenuCallbacks {

  override fun onOpenStorySettings(story: ContactSearchData.Story) {
    val fm = fragmentManager.value ?: return
    if (story.recipient.isMyStory) {
      MyStorySettingsFragment.createAsDialog().show(fm, null)
    } else {
      PrivateStorySettingsFragment.createAsDialog(story.recipient.requireDistributionListId()).show(fm, null)
    }
  }

  override fun onRemoveGroupStory(story: ContactSearchData.Story, isSelected: Boolean) {
    fragmentManager.value ?: return
    MaterialAlertDialogBuilder(context.value)
      .setTitle(R.string.ContactSearchMediator__remove_group_story)
      .setMessage(R.string.ContactSearchMediator__this_will_remove)
      .setPositiveButton(R.string.ContactSearchMediator__remove) { _, _ -> viewModel.removeGroupStory(story) }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
  }

  override fun onDeletePrivateStory(story: ContactSearchData.Story, isSelected: Boolean) {
    fragmentManager.value ?: return
    val ctx = context.value
    MaterialAlertDialogBuilder(ctx)
      .setTitle(R.string.ContactSearchMediator__delete_story)
      .setMessage(ctx.getString(R.string.ContactSearchMediator__delete_the_custom, story.recipient.getDisplayName(ctx)))
      .setPositiveButton(SpanUtil.color(ContextCompat.getColor(ctx, CoreUiR.color.signal_colorError), ctx.getString(R.string.ContactSearchMediator__delete))) { _, _ -> viewModel.deletePrivateStory(story) }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
  }
}

@DayNightPreviews
@Composable
private fun ContactSearchPreview() {
  Previews.Preview {
    Box(modifier = Modifier.fillMaxSize())
  }
}
