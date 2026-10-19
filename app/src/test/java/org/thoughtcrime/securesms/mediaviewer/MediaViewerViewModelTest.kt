/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaviewer

import android.app.Application
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import arrow.core.left
import arrow.core.right
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.AttachmentId
import org.signal.core.models.recipients.RecipientId
import org.signal.mediaviewer.MediaViewerAction
import org.signal.mediaviewer.MediaViewerEvent
import org.signal.mediaviewer.MediaViewerItem
import org.signal.mediaviewer.MediaViewerState
import org.signal.paging.PagingController
import org.signal.paging.StateFlowPagedData
import org.signal.uicomponents.styledtext.StyleRange
import org.signal.uicomponents.styledtext.StyledString
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaViewerViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val repository = mockk<MediaViewerRepository>(relaxUnitFun = true)
  private val dataSource = mockk<MediaViewerPagedDataSource>()
  private val databaseObserver = mockk<DatabaseObserver>(relaxUnitFun = true)
  private val pagingController = mockk<PagingController<AttachmentId>>(relaxUnitFun = true)
  private val pagedFlow = MutableStateFlow<List<MediaViewerItem>>(emptyList())

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    coEvery { repository.attachmentExists(any()) } returns false
    coEvery { repository.getInitialItem(any(), any(), any()) } returns null
    coEvery { repository.resolveMessageBodies(any(), any()) } returns emptyMap()
    coEvery { repository.getDeleteValidity(any()) } returns MediaViewerRepository.DeleteValidity(canDeleteForEveryone = false, isNoteToSelf = false)
    coEvery { repository.getMediaForEdit(any(), any()) } returns null
    coEvery { repository.getMessagePositionIntent(any(), any()) } returns Intent()
    every { repository.isOptimizedStorageEnabled() } returns false
    every { dataSource.findDisplayIndex(any<AttachmentId>()) } returns 0
    every { dataSource.isStillViewable(any<AttachmentId>()) } returns true
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial item is applied while nothing is loaded`() = runTest(testDispatcher) {
    val item = createItem(id = 10)
    coEvery { repository.getInitialItem(any(), any(), any()) } returns item

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.items).containsExactly(item)
    assertThat(viewModel.state.value.loadState).isEqualTo(MediaViewerState.LoadState.DATA_LOADED)
  }

  @Test
  fun `first paged emission anchors the pager on the starting attachment`() = runTest(testDispatcher) {
    every { dataSource.findDisplayIndex(AttachmentId(10)) } returns 2

    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1), createItem(2), createItem(10), createItem(11))

    assertThat(viewModel.state.value.position).isEqualTo(2)
    assertThat(viewModel.state.value.scrollRequest).isNotNull()
    assertThat(viewModel.state.value.scrollRequest!!.position).isEqualTo(2)
    assertThat(viewModel.state.value.scrollRequest!!.animated).isFalse()
  }

  @Test
  fun `the first paged emission waits for the pager to go idle`() = runTest(testDispatcher) {
    coEvery { repository.getInitialItem(any(), any(), any()) } returns createItem(10)
    every { dataSource.findDisplayIndex(AttachmentId(10)) } returns 5

    val viewModel = createViewModel()
    viewModel.onEvent(MediaViewerEvent.PagerScrollStateChanged(inProgress = true))

    pagedFlow.value = (0L until 12L).map { createItem(it + 20) }

    assertThat(viewModel.state.value.items).containsExactly(createItem(10))

    viewModel.onEvent(MediaViewerEvent.PagerScrollStateChanged(inProgress = false))

    assertThat(viewModel.state.value.items.size).isEqualTo(12)
    assertThat(viewModel.state.value.position).isEqualTo(5)
    assertThat(viewModel.state.value.scrollRequest!!.position).isEqualTo(5)
  }

  @Test
  fun `page scrolls before the first paged emission do not drive the paging controller`() = runTest(testDispatcher) {
    coEvery { repository.getInitialItem(any(), any(), any()) } returns createItem(10)

    val viewModel = createViewModel()
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))
    viewModel.onEvent(MediaViewerEvent.PageScrolled(0))

    verify(exactly = 0) { pagingController.onDataNeededAroundIndex(any()) }

    pagedFlow.value = listOf(createItem(1), createItem(2), createItem(3))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))
    viewModel.onEvent(MediaViewerEvent.PageScrolled(1))

    verify { pagingController.onDataNeededAroundIndex(1) }
  }

  @Test
  fun `initial item can never replace an already-loaded paged snapshot`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1), createItem(2))

    viewModel.onEvent(MediaViewerEvent.InitialItemLoaded(createItem(10)))

    assertThat(viewModel.state.value.items.size).isEqualTo(2)
  }

  @Test
  fun `MEDIA_READY is never downgraded by later paged emissions`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(MediaViewerEvent.MediaReady)

    pagedFlow.value = listOf(createItem(1))

    assertThat(viewModel.state.value.loadState).isEqualTo(MediaViewerState.LoadState.MEDIA_READY)
  }

  @Test
  fun `an empty paged emission does not clear a loaded window`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1))

    pagedFlow.value = emptyList()

    assertThat(viewModel.state.value.items.size).isEqualTo(1)
  }

  @Test
  fun `invalidation refreshes the gallery when the current item survives`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    pagedFlow.value = listOf(createItem(1), createItem(2), createItem(3))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))
    viewModel.onEvent(MediaViewerEvent.PageSettled(1))

    every { dataSource.isStillViewable(AttachmentId(2)) } returns true
    viewModel.onEvent(MediaViewerEvent.DataInvalidated)

    assertThat(actions).doesNotContain(MediaViewerAction.Finish)
    assertThat(viewModel.state.value.position).isEqualTo(1)
    verify { pagingController.onDataInvalidated() }
  }

  @Test
  fun `the viewer closes when the current item is deleted`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    pagedFlow.value = listOf(createItem(1), createItem(2))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    every { dataSource.isStillViewable(AttachmentId(1)) } returns false
    viewModel.onEvent(MediaViewerEvent.DataInvalidated)

    assertThat(actions).containsExactly(MediaViewerAction.ShowToast(R.string.MediaPreviewActivity_media_no_longer_available), MediaViewerAction.Finish)
  }

  @Test
  fun `the viewer stays open when the current item drops out of the gallery but still exists`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    pagedFlow.value = listOf(createItem(1), createItem(2))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    every { dataSource.isStillViewable(AttachmentId(1)) } returns false
    coEvery { repository.attachmentExists(AttachmentId(1)) } returns true
    viewModel.onEvent(MediaViewerEvent.DataInvalidated)

    assertThat(actions).isEmpty()
    verify(exactly = 0) { pagingController.onDataInvalidated() }
  }

  @Test
  fun `the viewer closes when the initial media uri is not an attachment uri`() = runTest(testDispatcher) {
    val args = createArgs().copy(initialMediaUri = Uri.parse("content://media/external/images/media/1"))

    val viewModel = createViewModel(args = args)
    val actions = collectActions(viewModel.actions)

    assertThat(actions).containsExactly(MediaViewerAction.Finish)
  }

  @Test
  fun `the viewer closes with a toast when the opened media cannot be found`() = runTest(testDispatcher) {
    every { dataSource.findDisplayIndex(any<AttachmentId>()) } returns -1

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    assertThat(actions).containsExactly(MediaViewerAction.ShowToast(R.string.MediaPreviewActivity_media_no_longer_available), MediaViewerAction.Finish)
  }

  @Test
  fun `the opened media stays on screen when it is loadable but not in the gallery`() = runTest(testDispatcher) {
    coEvery { repository.getInitialItem(any(), any(), any()) } returns createItem(10)
    every { dataSource.findDisplayIndex(any<AttachmentId>()) } returns -1

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.items).containsExactly(createItem(10))
  }

  @Test
  fun `deleting media that is not in the gallery closes the viewer`() = runTest(testDispatcher) {
    coEvery { repository.getInitialItem(any(), any(), any()) } returns createItem(10)
    every { dataSource.findDisplayIndex(any<AttachmentId>()) } returns -1
    coEvery { repository.localDelete(AttachmentId(10)) } returns Unit.right()

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    viewModel.onEvent(MediaViewerEvent.DeleteConfirmed(deleteForEveryone = false))

    assertThat(actions).containsExactly(MediaViewerAction.Finish)
  }

  @Test
  fun `deleting from the viewer closes it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    pagedFlow.value = listOf(createItem(1), createItem(2))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    every { dataSource.isStillViewable(AttachmentId(1)) } returns false
    coEvery { repository.localDelete(AttachmentId(1)) } returns Unit.right()
    viewModel.onEvent(MediaViewerEvent.DeleteConfirmed(deleteForEveryone = false))

    coVerify { repository.localDelete(AttachmentId(1)) }
    assertThat(actions).containsExactly(MediaViewerAction.Finish)
  }

  @Test
  fun `ForwardClicked forwards from the current item's thread`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    val item = createItem(1, threadId = 7L)
    pagedFlow.value = listOf(item)
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    viewModel.onEvent(MediaViewerEvent.ForwardClicked)

    assertThat(actions).containsExactly(MediaViewerAction.ShowForwardSheet(7L, item.playbackUri!!, item.contentType))
  }

  @Test
  fun `a failed delete shows an error toast`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    pagedFlow.value = listOf(createItem(1), createItem(2))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    coEvery { repository.remoteDelete(AttachmentId(1)) } returns IllegalStateException().left()
    viewModel.onEvent(MediaViewerEvent.DeleteConfirmed(deleteForEveryone = true))

    assertThat(actions).contains(MediaViewerAction.ShowToast(R.string.MediaPreviewFragment_media_delete_error))
  }

  @Test
  fun `DeleteClicked resolves delete-for-everyone validity before showing the dialog`() = runTest(testDispatcher) {
    coEvery { repository.getDeleteValidity(any()) } returns MediaViewerRepository.DeleteValidity(canDeleteForEveryone = true, isNoteToSelf = false)

    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1))

    viewModel.onEvent(MediaViewerEvent.DeleteClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(MediaViewerState.Dialog.DeleteConfirmation(canDeleteForEveryone = true, isNoteToSelf = false))
  }

  @Test
  fun `saving offloaded media shows the offloaded dialog instead of saving`() = runTest(testDispatcher) {
    every { repository.isOptimizedStorageEnabled() } returns true

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    pagedFlow.value = listOf(createItem(1, hasData = false))

    viewModel.onEvent(MediaViewerEvent.SaveClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(MediaViewerState.Dialog.SaveOffloadedMedia)
    assertThat(actions).doesNotContain(MediaViewerAction.Finish)
  }

  @Test
  fun `SaveClicked emits a save action for downloaded media`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    pagedFlow.value = listOf(createItem(1))

    viewModel.onEvent(MediaViewerEvent.SaveClicked)

    assertThat(actions.last()).isInstanceOf(MediaViewerAction.SaveMedia::class)
  }

  @Test
  fun `ShareClicked shares the playback uri`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    val item = createItem(1)
    pagedFlow.value = listOf(item)

    viewModel.onEvent(MediaViewerEvent.ShareClicked)

    assertThat(actions.last()).isEqualTo(MediaViewerAction.ShareMedia(item.playbackUri!!, item.contentType))
  }

  @Test
  fun `MediaTapped toggles the chrome`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1))

    viewModel.onEvent(MediaViewerEvent.MediaTapped)
    assertThat(viewModel.state.value.chromeVisible).isFalse()

    viewModel.onEvent(MediaViewerEvent.MediaTapped)
    assertThat(viewModel.state.value.chromeVisible).isTrue()
  }

  @Test
  fun `video playback pauses voice notes and auto-hides the chrome`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    pagedFlow.value = listOf(createItem(1, contentType = "video/mp4"))

    viewModel.onEvent(MediaViewerEvent.VideoPlaybackStarted)

    assertThat(actions).contains(MediaViewerAction.PauseVoiceNotes)
    assertThat(viewModel.state.value.chromeVisible).isTrue()

    testDispatcher.scheduler.advanceTimeBy(MediaViewerViewModel.CHROME_AUTO_HIDE_DELAY_MS + 100)

    assertThat(viewModel.state.value.chromeVisible).isFalse()
  }

  @Test
  fun `stopping playback cancels the chrome auto-hide`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1, contentType = "video/mp4"))

    viewModel.onEvent(MediaViewerEvent.VideoPlaybackStarted)
    viewModel.onEvent(MediaViewerEvent.VideoPlaybackStopped)
    testDispatcher.scheduler.advanceTimeBy(MediaViewerViewModel.CHROME_AUTO_HIDE_DELAY_MS + 100)

    assertThat(viewModel.state.value.chromeVisible).isTrue()
  }

  @Test
  fun `gif playback does not pause voice notes`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    pagedFlow.value = listOf(createItem(1, contentType = "video/mp4", isVideoGif = true))

    viewModel.onEvent(MediaViewerEvent.VideoPlaybackStarted)

    assertThat(actions).doesNotContain(MediaViewerAction.PauseVoiceNotes)
  }

  @Test
  fun `rail taps mint an animated scroll request`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1), createItem(2), createItem(3))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    viewModel.onEvent(MediaViewerEvent.RailItemClicked(AttachmentId(3)))

    val request = viewModel.state.value.scrollRequest
    assertThat(request).isNotNull()
    assertThat(request!!.position).isEqualTo(2)
    assertThat(request.animated).isTrue()
  }

  @Test
  fun `page scrolls move the position without waiting for a settle`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1), createItem(2), createItem(3))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    viewModel.onEvent(MediaViewerEvent.PageScrolled(1))

    assertThat(viewModel.state.value.position).isEqualTo(1)
  }

  @Test
  fun `rail taps move the position immediately`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1), createItem(2), createItem(3))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    viewModel.onEvent(MediaViewerEvent.RailItemClicked(AttachmentId(3)))

    assertThat(viewModel.state.value.position).isEqualTo(2)
  }

  @Test
  fun `page scrolls during an in-flight scroll request hold the requested position`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1), createItem(2), createItem(3))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))
    viewModel.onEvent(MediaViewerEvent.RailItemClicked(AttachmentId(3)))

    viewModel.onEvent(MediaViewerEvent.PageScrolled(1))

    assertThat(viewModel.state.value.position).isEqualTo(2)
  }

  @Test
  fun `settling short of a started scroll request clears the request`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1), createItem(2), createItem(3))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))
    viewModel.onEvent(MediaViewerEvent.RailItemClicked(AttachmentId(3)))
    viewModel.onEvent(MediaViewerEvent.ScrollRequestStarted(viewModel.state.value.scrollRequest!!.id))

    viewModel.onEvent(MediaViewerEvent.PageSettled(1))

    assertThat(viewModel.state.value.scrollRequest).isNull()
    assertThat(viewModel.state.value.position).isEqualTo(1)
  }

  @Test
  fun `a settle from before a scroll request starts does not complete it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1), createItem(2), createItem(3))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))
    viewModel.onEvent(MediaViewerEvent.RailItemClicked(AttachmentId(3)))

    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    assertThat(viewModel.state.value.scrollRequest).isNotNull()
    assertThat(viewModel.state.value.position).isEqualTo(2)
  }

  @Test
  fun `the pager's initial settle does not hijack the initial scroll request`() = runTest(testDispatcher) {
    every { dataSource.findDisplayIndex(AttachmentId(10)) } returns 1

    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1), createItem(10), createItem(3))

    viewModel.onEvent(MediaViewerEvent.PageScrolled(0))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    assertThat(viewModel.state.value.position).isEqualTo(1)
    assertThat(viewModel.state.value.scrollRequest).isNotNull()
    assertThat(viewModel.state.value.scrollRequest!!.position).isEqualTo(1)
  }

  @Test
  fun `settling on a page clears its scroll request`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1), createItem(2), createItem(3))
    viewModel.onEvent(MediaViewerEvent.RailItemClicked(AttachmentId(3)))

    viewModel.onEvent(MediaViewerEvent.PageSettled(2))

    assertThat(viewModel.state.value.scrollRequest).isNull()
  }

  @Test
  fun `BackClicked finishes with the exit transition`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MediaViewerEvent.BackClicked)

    assertThat(actions.last()).isEqualTo(MediaViewerAction.FinishAfterTransition)
  }

  @Test
  fun `hdr renders only when detected on the current item outside the shared animation`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val item = createItem(10)
    pagedFlow.value = listOf(item)
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    assertThat(viewModel.state.value.shouldRenderHdr).isFalse()

    viewModel.onEvent(MediaViewerEvent.HdrDetected(item.uri))
    assertThat(viewModel.state.value.shouldRenderHdr).isFalse()

    viewModel.onEvent(MediaViewerEvent.SharedAnimationChanged(false))
    assertThat(viewModel.state.value.shouldRenderHdr).isTrue()

    viewModel.onEvent(MediaViewerEvent.SharedAnimationChanged(true))
    assertThat(viewModel.state.value.shouldRenderHdr).isFalse()
  }

  @Test
  fun `the initial message body is used as the caption fallback until bodies resolve`() = runTest(testDispatcher) {
    val resolvedBodies = CompletableDeferred<Map<Long, StyledString>>()
    coEvery { repository.resolveMessageBodies(any(), setOf(5L)) } coAnswers { resolvedBodies.await() }

    val viewModel = createViewModel(args = createArgs(initialMessageBody = "hello"))
    pagedFlow.value = listOf(createItem(1, messageId = 5))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    assertThat(viewModel.state.value.currentCaption).isEqualTo(StyledString("hello"))

    resolvedBodies.complete(mapOf(5L to StyledString("resolved")))

    assertThat(viewModel.state.value.currentCaption).isEqualTo(StyledString("resolved"))
  }

  @Test
  fun `the initial message body keeps its styles and hidden spoilers`() = runTest(testDispatcher) {
    val body = SpannableString("hello secret").apply {
      setSpan(StyleSpan(Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      setSpan(SpoilerAnnotation.spoilerAnnotation(1), 6, 12, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val viewModel = createViewModel(args = createArgs(initialMessageBody = body))
    pagedFlow.value = listOf(createItem(1, messageId = 5))
    viewModel.onEvent(MediaViewerEvent.PageSettled(0))

    val caption = viewModel.state.value.currentCaption
    assertThat(caption?.text).isEqualTo("hello secret")
    assertThat(caption?.styles.orEmpty()).containsExactlyInAnyOrder(
      StyleRange(0, 5, StyleRange.Style.BOLD),
      StyleRange(6, 12, StyleRange.Style.SPOILER)
    )
  }

  @Test
  fun `a slow repository call does not block later events`() = runTest(testDispatcher) {
    coEvery { repository.getDeleteValidity(any()) } coAnswers {
      delay(10_000)
      MediaViewerRepository.DeleteValidity(canDeleteForEveryone = false, isNoteToSelf = false)
    }

    val viewModel = createViewModel()
    pagedFlow.value = listOf(createItem(1))

    viewModel.onEvent(MediaViewerEvent.DeleteClicked)
    viewModel.onEvent(MediaViewerEvent.MediaTapped)

    assertThat(viewModel.state.value.chromeVisible).isFalse()
  }

  @Test
  fun `events and state never log caption or name text`() {
    val item = createItem(1)
    val state = MediaViewerState(items = listOf(item), messageBodies = mapOf(5L to StyledString("secret body")))

    assertThat(state.toString()).doesNotContain("secret body")
    assertThat(item.toString()).doesNotContain("Miles")
    assertThat(MediaViewerEvent.MessageBodiesResolved(mapOf(5L to StyledString("secret body"))).toString()).doesNotContain("secret body")
    assertThat(MediaViewerEvent.PagedDataUpdated(listOf(item)).toString()).doesNotContain("Miles")
  }

  private fun TestScope.collectActions(actions: Flow<MediaViewerAction>): List<MediaViewerAction> {
    val collected = mutableListOf<MediaViewerAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }

  private fun createViewModel(args: MediaIntentFactory.MediaPreviewArgs = createArgs()): MediaViewerViewModel {
    return MediaViewerViewModel(
      args = args,
      context = ApplicationProvider.getApplicationContext(),
      repository = repository,
      dataSourceFactory = { dataSource },
      databaseObserver = databaseObserver,
      pagedDataFactory = { _, _ -> StateFlowPagedData(pagedFlow, pagingController) }
    )
  }

  private fun createArgs(initialMessageBody: CharSequence? = null): MediaIntentFactory.MediaPreviewArgs {
    return MediaIntentFactory.MediaPreviewArgs(
      threadId = 1L,
      date = 1_000L,
      messageId = 5L,
      fromRecipientId = RecipientId.from(1),
      threadRecipientId = RecipientId.from(2),
      outgoing = false,
      initialMediaUri = PartAuthority.getAttachmentDataUri(AttachmentId(10)),
      initialMediaDataUri = null,
      initialMediaType = "image/jpeg",
      initialMediaSize = 100L,
      initialCaption = null,
      initialMessageBody = initialMessageBody,
      sorting = MediaTable.Sorting.Newest,
      isVideoGif = false,
      skipSharedElementTransition = false
    )
  }

  private fun createItem(
    id: Long,
    contentType: String = "image/jpeg",
    isVideoGif: Boolean = false,
    hasData: Boolean = true,
    messageId: Long = 5L,
    threadId: Long = 1L
  ): MediaViewerItem {
    return MediaViewerItem(
      attachmentId = AttachmentId(id),
      uri = PartAuthority.getAttachmentDataUri(AttachmentId(id)),
      playbackUri = PartAuthority.getAttachmentDataUri(AttachmentId(id)),
      contentType = contentType,
      size = 100L,
      isVideoGif = isVideoGif,
      hasData = hasData,
      transferNeeded = false,
      messageId = messageId,
      threadId = threadId,
      date = 1_000L,
      formattedDate = "1 hour ago",
      formattedDateContentDescription = "1 hour ago",
      isOutgoing = false,
      caption = null,
      fromDisplayName = "Miles",
      threadDisplayName = "Spider Friends",
      isThreadGroup = true,
      isNoteToSelf = false
    )
  }
}
