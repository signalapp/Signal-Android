/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.collections.immutable.persistentHashMapOf
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.FastScrollerState
import org.signal.core.ui.compose.LazyColumnFastScroller
import org.signal.core.ui.compose.LocalFragmentManager
import org.signal.core.ui.compose.Previews
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.Emojifier
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.stories.settings.custom.PrivateStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.my.MyStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.privacy.ChooseInitialMyStoryMembershipBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingEntryProvider
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingLazyColumn
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingLazyListController
import org.thoughtcrime.securesms.util.adapter.mapping.compose.rememberMappingEntryProvider
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
 * @param viewModel               Drives the list — managed by the caller.
 * @param mapStateToConfiguration Maps the current [ContactSearchState] to the active
 *                                [ContactSearchConfiguration], re-evaluated whenever state changes.
 * @param modifier                Modifier applied to the composable root.
 * @param displayOptions          Controls checkbox and secondary-info visibility.
 * @param callbacks               Hooks for filtering and reacting to selection changes.
 * @param onListCommitted         Called after each list commit with the committed item count.
 * @param additionalEntries       Extra [MappingEntryProvider] entries layered on top of the base
 *                                set from [ContactSearchModels.composeEntries]. The base set is
 *                                always applied; on key collisions the base entry wins.
 */
@Composable
fun ContactSearch(
  viewModel: ContactSearchViewModel,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  modifier: Modifier = Modifier,
  displayOptions: ContactSearchAdapter.DisplayOptions = ContactSearchAdapter.DisplayOptions(),
  onListCommitted: (Int) -> Unit = {},
  additionalEntries: MappingEntryProvider<Any> = persistentHashMapOf(),
  lazyListState: LazyListState = rememberLazyListState(),
  callbacks: ContactSearchCallbacks = remember { ContactSearchCallbacks.Simple() },
  clickCallbacks: ContactSearchAdapter.ClickCallbacks = rememberDefaultContactSearchItemClickCallbacks(viewModel, callbacks),
  longClickCallbacks: ContactSearchAdapter.LongClickCallbacks = rememberDefaultContactSearchItemLongClickCallbacks(),
  storyContextMenuCallbacks: ContactSearchAdapter.StoryContextMenuCallbacks = rememberDefaultContactSearchItemStoryContextMenuCallbacks(viewModel),
  callButtonClickCallbacks: ContactSearchAdapter.CallButtonClickCallbacks = rememberDefaultContactSearchItemCallButtonClickCallbacks()
) {
  val mappingModels by viewModel.mappingModels.collectAsStateWithLifecycle()
  val controller by viewModel.controller.collectAsStateWithLifecycle()
  val configState by viewModel.configurationState.collectAsStateWithLifecycle()
  val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
  val fastScrollerEnabled by viewModel.fastScrollerEnabled.collectAsStateWithLifecycle()
  val isDisplayingContextMenu by viewModel.isDisplayingContextMenu.collectAsStateWithLifecycle()

  val currentMapStateToConfiguration by rememberUpdatedState(mapStateToConfiguration)
  val currentOnListCommitted by rememberUpdatedState(onListCommitted)

  LaunchedEffect(configState) {
    viewModel.setConfiguration(currentMapStateToConfiguration(configState))
  }

  LaunchedEffect(lazyListState) {
    viewModel.scrollRequests.collect {
      lazyListState.requestScrollToItem(0)
    }
  }

  val baseProvider = rememberContactSearchMappingEntryProvider(
    fixedContacts = viewModel.fixedContacts,
    displayOptions = displayOptions,
    callbacks = clickCallbacks,
    longClickCallbacks = longClickCallbacks,
    storyContextMenuCallbacks = storyContextMenuCallbacks,
    callButtonClickCallbacks = callButtonClickCallbacks
  )

  val provider = remember(baseProvider, additionalEntries) {
    additionalEntries.putAll(baseProvider)
  }

  val mappingCtrl = remember {
    MappingLazyListController(
      entryProvider = provider
    )
  }

  LaunchedEffect(controller) {
    controller?.run {
      mappingCtrl.pagingController = this
    }
  }

  LaunchedEffect(mappingModels) {
    mappingCtrl.items = mappingModels
    currentOnListCommitted(mappingModels.size)
  }

  val fastScrollerState = remember(mappingModels, totalCount) {
    FastScrollerState(items = mappingModels, totalCount = totalCount)
  }

  LazyColumnFastScroller(
    enabled = fastScrollerEnabled,
    userScrollEnabled = !isDisplayingContextMenu,
    fastScrollerState = fastScrollerState,
    lazyListState = lazyListState,
    modifier = modifier,
    letterContent = {
      Emojifier(text = it.toString()) { annotatedText, inlineContent ->
        Text(
          text = annotatedText,
          inlineContent = inlineContent,
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }
    }
  ) {
    MappingLazyColumn(
      userScrollEnabled = !isDisplayingContextMenu,
      controller = mappingCtrl,
      lazyListState = it,
      modifier = modifier
    )
  }
}

@Composable
private fun rememberContactSearchMappingEntryProvider(
  fixedContacts: Set<ContactSearchKey>,
  displayOptions: ContactSearchAdapter.DisplayOptions,
  callbacks: ContactSearchAdapter.ClickCallbacks,
  longClickCallbacks: ContactSearchAdapter.LongClickCallbacks,
  storyContextMenuCallbacks: ContactSearchAdapter.StoryContextMenuCallbacks?,
  callButtonClickCallbacks: ContactSearchAdapter.CallButtonClickCallbacks
): MappingEntryProvider<Any> {
  return rememberMappingEntryProvider {
    // Subclass-registered models (Message, Thread, Empty, GroupWithMembers) and
    // ArbitraryRepository-backed models are handled separately.
    provider<Any>(
      ContactSearchModels.composeEntries(
        fixedContacts = fixedContacts,
        displayOptions = displayOptions,
        callbacks = callbacks,
        longClickCallbacks = longClickCallbacks,
        storyContextMenuCallbacks = storyContextMenuCallbacks,
        callButtonClickCallbacks = callButtonClickCallbacks
      )
    )
  }
}

@Composable
fun rememberDefaultContactSearchItemClickCallbacks(viewModel: ContactSearchViewModel, callbacks: ContactSearchCallbacks): ContactSearchAdapter.ClickCallbacks {
  val fragmentManager = LocalFragmentManager.current

  return remember(callbacks) {
    DefaultClickCallbacks(viewModel, callbacks, fragmentManager)
  }
}

@Composable
fun rememberDefaultContactSearchItemLongClickCallbacks(): ContactSearchAdapter.LongClickCallbacks {
  return remember { ContactSearchAdapter.LongClickCallbacksAdapter() }
}

@Composable
fun rememberDefaultContactSearchItemStoryContextMenuCallbacks(viewModel: ContactSearchViewModel): ContactSearchAdapter.StoryContextMenuCallbacks {
  val context = LocalContext.current
  val fragmentManager = LocalFragmentManager.current

  return remember { DefaultStoryContextMenuCallbacks(viewModel, fragmentManager, context) }
}

@Composable
fun rememberDefaultContactSearchItemCallButtonClickCallbacks(): ContactSearchAdapter.CallButtonClickCallbacks {
  return remember { ContactSearchAdapter.EmptyCallButtonClickCallbacks }
}

private class DefaultClickCallbacks(
  private val viewModel: ContactSearchViewModel,
  private val callbacks: ContactSearchCallbacks,
  private val fragmentManager: FragmentManager?
) : ContactSearchAdapter.ClickCallbacks {

  companion object {
    private val TAG = Log.tag(DefaultClickCallbacks::class.java)
  }

  override fun onStoryClicked(view: View, story: ContactSearchData.Story, isSelected: Boolean) {
    Log.d(TAG, "onStoryClicked()")
    if (story.recipient.isMyStory && !SignalStore.story.userHasBeenNotifiedAboutStories) {
      fragmentManager?.let { ChooseInitialMyStoryMembershipBottomSheetDialogFragment.show(it) }
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
      viewModel.setKeysSelected(callbacks.onBeforeContactsSelected(view, setOf(chatTypeRow.contactSearchKey)))
    }
  }

  private fun toggle(view: View, data: ContactSearchData, isSelected: Boolean) {
    if (isSelected) {
      Log.d(TAG, "toggle(OFF) ${data.contactSearchKey}")
      callbacks.onContactDeselected(view, data.contactSearchKey)
      viewModel.setKeysNotSelected(setOf(data.contactSearchKey))
    } else {
      Log.d(TAG, "toggle(ON) ${data.contactSearchKey}")
      viewModel.setKeysSelected(callbacks.onBeforeContactsSelected(view, setOf(data.contactSearchKey)))
    }
  }
}

private class DefaultStoryContextMenuCallbacks(
  private val viewModel: ContactSearchViewModel,
  private val fragmentManager: FragmentManager?,
  private val context: Context
) : ContactSearchAdapter.StoryContextMenuCallbacks {

  override fun onOpenStorySettings(story: ContactSearchData.Story) {
    val fm = fragmentManager ?: return
    if (story.recipient.isMyStory) {
      MyStorySettingsFragment.createAsDialog().show(fm, null)
    } else {
      PrivateStorySettingsFragment.createAsDialog(story.recipient.requireDistributionListId()).show(fm, null)
    }
  }

  override fun onRemoveGroupStory(story: ContactSearchData.Story, isSelected: Boolean) {
    fragmentManager ?: return
    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.ContactSearchMediator__remove_group_story)
      .setMessage(R.string.ContactSearchMediator__this_will_remove)
      .setPositiveButton(R.string.ContactSearchMediator__remove) { _, _ -> viewModel.removeGroupStory(story) }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
  }

  override fun onDeletePrivateStory(story: ContactSearchData.Story, isSelected: Boolean) {
    fragmentManager ?: return
    val ctx = context
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
