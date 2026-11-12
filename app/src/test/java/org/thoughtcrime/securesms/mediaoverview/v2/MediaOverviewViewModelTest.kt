/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview.v2

import android.content.Intent
import android.net.Uri
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.models.database.AttachmentId
import org.signal.mediaoverview.MediaOverviewAction
import org.signal.mediaoverview.MediaOverviewEvent
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewState.Dialog
import org.signal.mediaoverview.MediaOverviewState.Sorting
import org.signal.mediaoverview.MediaOverviewState.Tab
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class MediaOverviewViewModelTest {

  companion object {
    private const val THREAD_ID = 7L
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val repository = mockk<MediaOverviewRepository>(relaxUnitFun = true)
  private val mediaChanges = MutableSharedFlow<Unit>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.observeTitle(any()) } returns flowOf("Book Club")
    every { repository.mediaChanges() } returns mediaChanges
    every { repository.observeMediaChanges(any()) } returns {}
    coEvery { repository.getCount(any(), any()) } returns 0
    coEvery { repository.getPageRecords(any(), any(), any(), any(), any()) } returns emptyList()
    coEvery { repository.resolveRecipients(any()) } returns emptyMap()
    coEvery { repository.toItems(any(), any()) } returns emptyList()
    every { repository.isOptimizeStorageEnabled() } returns false
    coEvery { repository.createPreviewIntent(any(), any(), any()) } returns mockk<Intent>()
    coEvery { repository.getSaveableMedia(any()) } answers {
      firstArg<Collection<MediaOverviewItem.Key>>().map { MediaOverviewAction.SaveableMedia(uri = mockk(), contentType = "image/jpeg", date = 0, fileName = null) }
    }
    coEvery { repository.createConversationIntent(any()) } returns mockk<Intent>()
    coEvery { repository.delete(any(), any()) } returns Unit
    coEvery { repository.download(any()) } returns Unit
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `a single conversation starts on the media tab, newest first, in a grid`() = runTest(testDispatcher) {
    val state = createViewModel().state.value

    assertThat(state.isAllThreads).isFalse()
    assertThat(state.selectedTab).isEqualTo(Tab.MEDIA)
    assertThat(state.sorting).isEqualTo(Sorting.NEWEST)
    assertThat(state.detailLayout).isFalse()
    assertThat(state.title).isEqualTo("Book Club")
  }

  @Test
  fun `all storage starts on the all tab, largest first, as a list`() = runTest(testDispatcher) {
    val state = createViewModel(threadId = MediaTable.ALL_THREADS.toLong()).state.value

    assertThat(state.isAllThreads).isTrue()
    assertThat(state.selectedTab).isEqualTo(Tab.ALL)
    assertThat(state.sorting).isEqualTo(Sorting.LARGEST)
    assertThat(state.detailLayout).isTrue()
    verify(exactly = 0) { repository.observeTitle(any()) }
  }

  @Test
  fun `SortSelected applies the new order and closes the dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(MediaOverviewEvent.SortClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.SortOrder)

    viewModel.onEvent(MediaOverviewEvent.SortSelected(Sorting.OLDEST))

    assertThat(viewModel.state.value.sorting).isEqualTo(Sorting.OLDEST)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `only the tabs the screen shows get paged media`() = runTest(testDispatcher) {
    assertThat(createViewModel().media.keys).isEqualTo(Tab.entries.toSet())
    assertThat(createViewModel(threadId = MediaTable.ALL_THREADS.toLong()).media.keys).isEqualTo(Tab.entries.toSet() - Tab.LINKS)
  }

  @Test
  fun `TabSelected switches tabs and closes the menu`() = runTest(testDispatcher) {
    val image = image(1)
    val viewModel = createViewModel()
    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(image))

    viewModel.onEvent(MediaOverviewEvent.TabSelected(Tab.FILES))

    assertThat(viewModel.state.value.selectedTab).isEqualTo(Tab.FILES)
    assertThat(viewModel.state.value.contextMenuItem).isNull()
  }

  @Test
  fun `LayoutToggled flips between grid and list`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(MediaOverviewEvent.LayoutToggled)

    assertThat(viewModel.state.value.detailLayout).isTrue()
  }

  @Test
  fun `ItemClicked on a downloaded image opens the media viewer`() = runTest(testDispatcher) {
    val image = image(1)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MediaOverviewEvent.ItemClicked(image))

    assertThat(actions.single()).isInstanceOf<MediaOverviewAction.OpenMediaPreview>()
    coVerify { repository.createPreviewIntent(image.key, Sorting.NEWEST, false) }
  }

  @Test
  fun `ItemClicked on an image that has not finished sending says so`() = runTest(testDispatcher) {
    val image = image(1, transferState = MediaOverviewItem.TransferState.IN_PROGRESS)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MediaOverviewEvent.ItemClicked(image))

    assertThat(actions).containsExactly(MediaOverviewAction.ShowMediaNotSentYet)
  }

  @Test
  fun `ItemClicked on offloaded media with nothing to show downloads it`() = runTest(testDispatcher) {
    val image = image(1, transferState = MediaOverviewItem.TransferState.OFFLOADED, hasDisplayUri = false)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MediaOverviewEvent.ItemClicked(image))

    assertThat(actions).isEmpty()
    coVerify { repository.download(image.key) }
  }

  @Test
  fun `ItemClicked on a link opens it`() = runTest(testDispatcher) {
    val link = item(1, MediaOverviewItem.Kind.LINK, key = MediaOverviewItem.Key.Message(1), linkUrl = "https://signal.org")
    val viewModel = createViewModel(tab = Tab.LINKS)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MediaOverviewEvent.ItemClicked(link))

    assertThat(actions).containsExactly(MediaOverviewAction.OpenLink("https://signal.org"))
  }

  @Test
  fun `ItemClicked on a document opens it in another app`() = runTest(testDispatcher) {
    val document = item(1, MediaOverviewItem.Kind.DOCUMENT)
    val viewModel = createViewModel(tab = Tab.FILES)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MediaOverviewEvent.ItemClicked(document))

    assertThat(actions).containsExactly(MediaOverviewAction.OpenFileExternally(document.uri!!, document.contentType))
  }

  @Test
  fun `ItemLongClicked opens the menu, and choosing select starts a selection`() = runTest(testDispatcher) {
    val image = image(1)
    val viewModel = createViewModel()

    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(image))
    assertThat(viewModel.state.value.contextMenuKey).isEqualTo(image.key)

    viewModel.onEvent(MediaOverviewEvent.ContextMenuSelectClicked)
    assertThat(viewModel.state.value.contextMenuKey).isNull()
    assertThat(viewModel.state.value.selectedKeys).isEqualTo(setOf(image.key))
    assertThat(viewModel.state.value.selectedTotalSize).isEqualTo(image.size)
  }

  @Test
  fun `ItemClicked during a selection toggles the item, and unselecting the last one ends it`() = runTest(testDispatcher) {
    val first = image(1)
    val second = image(2)
    val viewModel = createViewModel()
    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(first))
    viewModel.onEvent(MediaOverviewEvent.ContextMenuSelectClicked)

    viewModel.onEvent(MediaOverviewEvent.ItemClicked(second))
    assertThat(viewModel.state.value.selectedKeys).isEqualTo(setOf(first.key, second.key))

    viewModel.onEvent(MediaOverviewEvent.ItemClicked(first))
    viewModel.onEvent(MediaOverviewEvent.ItemClicked(second))
    assertThat(viewModel.state.value.isMultiSelect).isFalse()
  }

  @Test
  fun `ContextMenuJumpToMessageClicked opens the conversation`() = runTest(testDispatcher) {
    val image = image(1)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(image))
    viewModel.onEvent(MediaOverviewEvent.ContextMenuJumpToMessageClicked)

    assertThat(actions.single()).isInstanceOf<MediaOverviewAction.OpenConversation>()
  }

  @Test
  fun `deleting the selection asks first, then deletes and clears the selection`() = runTest(testDispatcher) {
    val image = image(1)
    val viewModel = createViewModel()
    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(image))
    viewModel.onEvent(MediaOverviewEvent.ContextMenuSelectClicked)

    viewModel.onEvent(MediaOverviewEvent.DeleteSelectedClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmDelete(setOf(image.key)))
    coVerify(exactly = 0) { repository.delete(any(), any()) }

    viewModel.onEvent(MediaOverviewEvent.DeleteConfirmed)
    coVerify { repository.delete(setOf(image.key), any()) }
    assertThat(viewModel.state.value.selectedKeys).isEmpty()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `cancelling a delete keeps the selection`() = runTest(testDispatcher) {
    val image = image(1)
    val viewModel = createViewModel()
    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(image))
    viewModel.onEvent(MediaOverviewEvent.ContextMenuSelectClicked)

    viewModel.onEvent(MediaOverviewEvent.DeleteSelectedClicked)
    viewModel.onEvent(MediaOverviewEvent.DialogDismissed)

    assertThat(viewModel.state.value.selectedKeys).isEqualTo(setOf(image.key))
    coVerify(exactly = 0) { repository.delete(any(), any()) }
  }

  @Test
  fun `saving the selection hands it to the host, and the selection clears once it is saved`() = runTest(testDispatcher) {
    val image = image(1)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(image))
    viewModel.onEvent(MediaOverviewEvent.ContextMenuSelectClicked)

    viewModel.onEvent(MediaOverviewEvent.SaveSelectedClicked)
    assertThat((actions.single() as MediaOverviewAction.SaveMedia).media.size).isEqualTo(1)
    assertThat(viewModel.state.value.isMultiSelect).isTrue()

    viewModel.onEvent(MediaOverviewEvent.SaveFinished)
    assertThat(viewModel.state.value.isMultiSelect).isFalse()
  }

  @Test
  fun `saving only offloaded media explains that it can't`() = runTest(testDispatcher) {
    every { repository.isOptimizeStorageEnabled() } returns true
    val image = image(1, transferState = MediaOverviewItem.TransferState.OFFLOADED)
    coEvery { repository.getItems(setOf(image.key)) } returns listOf(image)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(image))
    viewModel.onEvent(MediaOverviewEvent.ContextMenuSaveClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.AllOffloaded)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `saving partly offloaded media saves what is on the device once confirmed`() = runTest(testDispatcher) {
    every { repository.isOptimizeStorageEnabled() } returns true
    val onDevice = image(1)
    val offloaded = image(2, transferState = MediaOverviewItem.TransferState.OFFLOADED)
    coEvery { repository.getItems(setOf(onDevice.key, offloaded.key)) } returns listOf(onDevice, offloaded)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(onDevice))
    viewModel.onEvent(MediaOverviewEvent.ContextMenuSelectClicked)
    viewModel.onEvent(MediaOverviewEvent.ItemClicked(offloaded))

    viewModel.onEvent(MediaOverviewEvent.SaveSelectedClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.PartiallyOffloaded(setOf(onDevice.key)))

    viewModel.onEvent(MediaOverviewEvent.SaveAvailableConfirmed)
    assertThat((actions.single() as MediaOverviewAction.SaveMedia).media.size).isEqualTo(1)
    coVerify(exactly = 1) { repository.getSaveableMedia(setOf(onDevice.key)) }
  }

  @Test
  fun `ViewStorageSettingsClicked opens storage settings`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MediaOverviewEvent.ViewStorageSettingsClicked)

    assertThat(actions).containsExactly(MediaOverviewAction.OpenStorageSettings)
  }

  @Test
  fun `AudioButtonClicked plays a paused clip and pauses a playing one`() = runTest(testDispatcher) {
    val audio = item(1, MediaOverviewItem.Kind.AUDIO)
    val viewModel = createViewModel(tab = Tab.AUDIO)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MediaOverviewEvent.AudioButtonClicked(audio))
    assertThat(actions.last()).isEqualTo(MediaOverviewAction.PlayAudio(audio.uri!!, audio.messageId, 0.0))

    viewModel.onEvent(MediaOverviewEvent.AudioPlaybackChanged(audio.uri, isPlaying = true, progress = 0.5f))
    viewModel.onEvent(MediaOverviewEvent.AudioButtonClicked(audio))
    assertThat(actions.last()).isEqualTo(MediaOverviewAction.PauseAudio(audio.uri!!))
  }

  @Test
  fun `AudioButtonClicked on audio that is not downloaded downloads it`() = runTest(testDispatcher) {
    val audio = item(1, MediaOverviewItem.Kind.AUDIO, hasUri = false)
    val viewModel = createViewModel(tab = Tab.AUDIO)

    viewModel.onEvent(MediaOverviewEvent.AudioButtonClicked(audio))

    coVerify { repository.download(audio.key) }
  }

  @Test
  fun `SelectAllClicked selects every item in the tab, not just the loaded ones`() = runTest(testDispatcher) {
    val first = image(1)
    val everything = mapOf(first.key to 100L, MediaOverviewItem.Key.Attachment(AttachmentId(2)) to 250L)
    coEvery { repository.getAllKeysWithSizes(THREAD_ID, Tab.MEDIA, Sorting.NEWEST) } returns everything
    val viewModel = createViewModel()
    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(first))
    viewModel.onEvent(MediaOverviewEvent.ContextMenuSelectClicked)

    viewModel.onEvent(MediaOverviewEvent.SelectAllClicked)

    assertThat(viewModel.state.value.selection).isEqualTo(everything)
    assertThat(viewModel.state.value.selectedTotalSize).isEqualTo(350L)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `media deleted elsewhere drops out of the selection`() = runTest(testDispatcher) {
    val first = image(1)
    val second = image(2)
    coEvery { repository.getExistingKeys(setOf(first.key, second.key)) } returns setOf(second.key)
    val viewModel = createViewModel()
    viewModel.onEvent(MediaOverviewEvent.ItemLongClicked(first))
    viewModel.onEvent(MediaOverviewEvent.ContextMenuSelectClicked)
    viewModel.onEvent(MediaOverviewEvent.ItemClicked(second))

    mediaChanges.emit(Unit)

    assertThat(viewModel.state.value.selectedKeys).isEqualTo(setOf(second.key))
  }

  @Test
  fun `media changes with nothing selected skip the lookup`() = runTest(testDispatcher) {
    createViewModel()

    mediaChanges.emit(Unit)

    coVerify(exactly = 0) { repository.getExistingKeys(any()) }
  }

  private fun createViewModel(threadId: Long = THREAD_ID, tab: Tab? = null): MediaOverviewViewModel {
    val viewModel = MediaOverviewViewModel(threadId, repository)
    tab?.let { viewModel.onEvent(MediaOverviewEvent.TabSelected(it)) }
    return viewModel
  }

  private fun image(
    id: Long,
    transferState: MediaOverviewItem.TransferState = MediaOverviewItem.TransferState.DONE,
    hasDisplayUri: Boolean = true
  ): MediaOverviewItem {
    return item(id, MediaOverviewItem.Kind.IMAGE, transferState = transferState, hasDisplayUri = hasDisplayUri)
  }

  private fun item(
    id: Long,
    kind: MediaOverviewItem.Kind,
    key: MediaOverviewItem.Key = MediaOverviewItem.Key.Attachment(AttachmentId(id)),
    transferState: MediaOverviewItem.TransferState = MediaOverviewItem.TransferState.DONE,
    hasUri: Boolean = true,
    hasDisplayUri: Boolean = hasUri,
    linkUrl: String? = null
  ): MediaOverviewItem {
    val uri = if (hasUri) mockk<Uri>() else null
    return MediaOverviewItem(
      key = key,
      kind = kind,
      messageId = id,
      threadId = THREAD_ID,
      date = id,
      formattedDate = "",
      size = 100,
      contentType = "image/jpeg",
      uri = uri,
      displayUri = if (hasDisplayUri) uri ?: mockk() else null,
      transferState = transferState,
      linkUrl = linkUrl
    )
  }

  private fun TestScope.collectActions(actions: Flow<MediaOverviewAction>): List<MediaOverviewAction> {
    val collected = mutableListOf<MediaOverviewAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
