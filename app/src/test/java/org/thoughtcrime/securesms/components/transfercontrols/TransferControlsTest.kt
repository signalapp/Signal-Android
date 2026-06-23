/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.transfercontrols

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream

class TransferControlsTest {

  @Before
  fun setUp() {
    mockkStatic(MediaUtil::class)
    every { MediaUtil.isInstantVideoSupported(any()) } returns false
  }

  @After
  fun tearDown() {
    unmockkStatic(MediaUtil::class)
  }

  @Test
  fun `empty slides is Gone`() {
    assertEquals(TransferControlsRenderState.Gone, TransferControls.deriveRenderState(stateOf(emptyList())))
  }

  @Test
  fun `all done is Gone`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_DONE), slide(AttachmentTable.TRANSFER_PROGRESS_DONE)))
    assertEquals(TransferControlsRenderState.Gone, TransferControls.deriveRenderState(state))
  }

  @Test
  fun `not visible is Gone`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_PENDING)), isVisible = false)
    assertEquals(TransferControlsRenderState.Gone, TransferControls.deriveRenderState(state))
  }

  @Test
  fun `awaiting primary single item is centered indeterminate non-cancelable`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_NEEDS_RESTORE)), awaitingPrimaryResponse = true)
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertNull(render.progress)
    assertEquals(TransferControls.Placement.CENTER, render.placement)
    assertFalse(render.cancelable)
    assertFalse(render.isUpload)
  }

  @Test
  fun `awaiting primary gallery is corner indeterminate`() {
    val state = stateOf(
      listOf(slide(AttachmentTable.TRANSFER_NEEDS_RESTORE), slide(AttachmentTable.TRANSFER_NEEDS_RESTORE)),
      awaitingPrimaryResponse = true
    )
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertNull(render.progress)
    assertEquals(TransferControls.Placement.CORNER, render.placement)
  }

  @Test
  fun `awaiting primary still Gone when not visible`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_NEEDS_RESTORE)), awaitingPrimaryResponse = true, isVisible = false)
    assertEquals(TransferControlsRenderState.Gone, TransferControls.deriveRenderState(state))
  }

  @Test
  fun `single image pending download is centered pending`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_PENDING)))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.Pending
    assertEquals(TransferControls.Placement.CENTER, render.placement)
    assertFalse(render.showPlayButton)
    assertNull(render.itemCount)
  }

  @Test
  fun `single image downloading is centered in-progress cancelable`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED)))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertEquals(TransferControls.Placement.CENTER, render.placement)
    assertTrue(render.cancelable)
    assertFalse(render.isUpload)
  }

  @Test
  fun `single image failed download is retry download`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_FAILED)))
    assertEquals(TransferControlsRenderState.Retry(isUpload = false), TransferControls.deriveRenderState(state))
  }

  @Test
  fun `single image uploading is corner in-progress upload`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED)), isUpload = true)
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertEquals(TransferControls.Placement.CORNER, render.placement)
    assertTrue(render.isUpload)
  }

  @Test
  fun `single image failed upload is retry upload`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_FAILED)), isUpload = true)
    assertEquals(TransferControlsRenderState.Retry(isUpload = true), TransferControls.deriveRenderState(state))
  }

  @Test
  fun `playable video pending download shows play button in corner`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_PENDING, hasVideo = true)), playableWhileDownloading = true)
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.Pending
    assertEquals(TransferControls.Placement.CORNER, render.placement)
    assertTrue(render.showPlayButton)
  }

  @Test
  fun `playable video downloading shows play button in corner`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED, hasVideo = true)), playableWhileDownloading = true)
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertEquals(TransferControls.Placement.CORNER, render.placement)
    assertTrue(render.showPlayButton)
  }

  @Test
  fun `gallery downloading is corner in-progress`() {
    val state = stateOf(
      listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED), slide(AttachmentTable.TRANSFER_PROGRESS_STARTED)),
      networkProgress = mapOf()
    ).let { base ->
      // give it some completed progress so it is cancelable
      val map = base.slides.associate { it.asAttachment() to TransferControlView.Progress(512L.bytes, 1024L.bytes) }
      base.copy(networkProgress = map)
    }
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertEquals(TransferControls.Placement.CORNER, render.placement)
    assertTrue(render.cancelable)
  }

  @Test
  fun `gallery downloading at zero progress is not cancelable`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED), slide(AttachmentTable.TRANSFER_PROGRESS_STARTED)))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertEquals(TransferControls.Placement.CORNER, render.placement)
    assertFalse(render.cancelable)
  }

  @Test
  fun `gallery pending non-playable is centered with item count`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_PENDING), slide(AttachmentTable.TRANSFER_PROGRESS_PENDING)))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.Pending
    assertEquals(TransferControls.Placement.CENTER, render.placement)
    assertEquals(2, render.itemCount)
  }

  @Test
  fun `gallery pending playable is corner without item count`() {
    every { MediaUtil.isInstantVideoSupported(any()) } returns true
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_PENDING), slide(AttachmentTable.TRANSFER_PROGRESS_PENDING)))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.Pending
    assertEquals(TransferControls.Placement.CORNER, render.placement)
    assertNull(render.itemCount)
  }

  @Test
  fun `gallery failed download is retry`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_FAILED), slide(AttachmentTable.TRANSFER_PROGRESS_FAILED)))
    assertEquals(TransferControlsRenderState.Retry(isUpload = false), TransferControls.deriveRenderState(state))
  }

  @Test
  fun `pending hides size text when showSecondaryText is false`() {
    val state = stateOf(listOf(slide(AttachmentTable.TRANSFER_PROGRESS_PENDING)), showSecondaryText = false)
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.Pending
    assertNull(render.sizeBytes)
  }

  @Test
  fun `download label denominator is ciphertext size derived from slide, not network total`() {
    val slides = listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED, size = 1000))
    // Completed is reported in ciphertext bytes, so the denominator must be the matching ciphertext length derived from the
    // slide's plaintext size. The network event's total (2000) is intentionally different to prove it is not the source.
    val state = stateOf(slides, networkProgress = progressOf(slides, completed = 500, total = 2000))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    val expectedTotal = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(1000))
    assertEquals(TransferControls.ProgressLabel.Bytes(500L.bytes, expectedTotal.bytes), render.label)
  }

  @Test
  fun `download label clamps completed to ciphertext total`() {
    val slides = listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED, size = 1000))
    val expectedTotal = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(1000))
    // Incremental-MAC overhead means transmitted bytes can edge just past the computed ciphertext length; clamp to total.
    val state = stateOf(slides, networkProgress = progressOf(slides, completed = expectedTotal + 100, total = expectedTotal + 100))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertEquals(TransferControls.ProgressLabel.Bytes(expectedTotal.bytes, expectedTotal.bytes), render.label)
  }

  @Test
  fun `upload with no bytes sent shows Processing`() {
    val slides = listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED, size = 1000))
    val state = stateOf(slides, isUpload = true, networkProgress = progressOf(slides, completed = 0, total = 1000))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertEquals(TransferControls.ProgressLabel.Processing, render.label)
  }

  @Test
  fun `upload while still compressing shows Processing`() {
    val slides = listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED, size = 1000))
    val state = stateOf(
      slides,
      isUpload = true,
      // Some bytes have been transmitted (so the zero-bytes branch does not apply)...
      networkProgress = progressOf(slides, completed = 500, total = 1000),
      // ...but compression is only halfway done, which should still read as Processing.
      compressionProgress = progressOf(slides, completed = 500, total = 1000)
    )
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertEquals(TransferControls.ProgressLabel.Processing, render.label)
  }

  @Test
  fun `upload that is transmitting with no pending compression shows Bytes`() {
    val slides = listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED, size = 1000))
    val state = stateOf(slides, isUpload = true, networkProgress = progressOf(slides, completed = 500, total = 1000))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertEquals(TransferControls.ProgressLabel.Bytes(500L.bytes, 1000L.bytes), render.label)
  }

  @Test
  fun `upload label uses network total, not pre-transcode slide size`() {
    val slides = listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED, size = 12_000_000))
    val state = stateOf(slides, isUpload = true, networkProgress = progressOf(slides, completed = 200_000, total = 400_000))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    assertEquals(TransferControls.ProgressLabel.Bytes(200_000L.bytes, 400_000L.bytes), render.label)
  }

  @Test
  fun `calculateProgress weights compression three to one against network`() {
    val slides = listOf(slide(AttachmentTable.TRANSFER_PROGRESS_STARTED, size = 1000))
    val state = stateOf(
      slides,
      isUpload = true,
      networkProgress = progressOf(slides, completed = 500, total = 1000),
      compressionProgress = progressOf(slides, completed = 1000, total = 1000)
    )
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.InProgress
    // weighted = (1 * 0.5) + (3 * 1.0) = 3.5; total weight = (1 * 1) + (3 * 1) = 4; 3.5 / 4 = 0.875
    assertEquals(0.875f, render.progress!!, 0.0001f)
  }

  @Test
  fun `gallery pending size is remaining network bytes`() {
    val slides = listOf(slide(AttachmentTable.TRANSFER_PROGRESS_PENDING), slide(AttachmentTable.TRANSFER_PROGRESS_PENDING))
    // Two slides, each 200/1000 complete -> remaining = sumTotal(2000) - sumCompleted(400) = 1600
    val state = stateOf(slides, networkProgress = progressOf(slides, completed = 200, total = 1000))
    val render = TransferControls.deriveRenderState(state) as TransferControlsRenderState.Pending
    assertEquals(1600L.bytes, render.sizeBytes)
  }

  @Test
  fun `getTransferState prefers pending over done`() {
    val slides = listOf(slide(AttachmentTable.TRANSFER_PROGRESS_DONE), slide(AttachmentTable.TRANSFER_PROGRESS_PENDING))
    assertEquals(AttachmentTable.TRANSFER_PROGRESS_PENDING, TransferControls.getTransferState(slides))
  }

  @Test
  fun `getTransferState all permanent failures is permanent failure`() {
    val slides = listOf(slide(AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE), slide(AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE))
    assertEquals(AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE, TransferControls.getTransferState(slides))
  }

  private fun slide(
    transferState: Int,
    hasVideo: Boolean = false,
    size: Long = 1024
  ): Slide {
    val attachment = mockk<Attachment>(relaxed = true)
    val slide = mockk<Slide>(relaxed = true)
    every { slide.transferState } returns transferState
    every { slide.hasVideo() } returns hasVideo
    every { slide.asAttachment() } returns attachment
    every { slide.fileSize } returns size
    return slide
  }

  private fun stateOf(
    slides: List<Slide>,
    isUpload: Boolean = false,
    playableWhileDownloading: Boolean = false,
    isVisible: Boolean = true,
    showSecondaryText: Boolean = true,
    awaitingPrimaryResponse: Boolean = false,
    networkProgress: Map<Attachment, TransferControlView.Progress> = slides.associate { it.asAttachment() to TransferControlView.Progress(0L.bytes, 1024L.bytes) },
    compressionProgress: Map<Attachment, TransferControlView.Progress> = emptyMap()
  ): TransferControlViewState {
    return TransferControlViewState(
      slides = slides,
      isUpload = isUpload,
      playableWhileDownloading = playableWhileDownloading,
      isVisible = isVisible,
      showSecondaryText = showSecondaryText,
      awaitingPrimaryResponse = awaitingPrimaryResponse,
      networkProgress = networkProgress,
      compressionProgress = compressionProgress
    )
  }

  private fun progressOf(slides: List<Slide>, completed: Long, total: Long): Map<Attachment, TransferControlView.Progress> {
    return slides.associate { it.asAttachment() to TransferControlView.Progress(completed.bytes, total.bytes) }
  }
}
