/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.media.Media
import org.signal.mediasend.preupload.PreUploadController

/**
 * Covers whether a capture takes the user on to the editor, which multi-capture holds back so they can keep shooting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaSendFlowViewModelMultiCaptureTest {

  @get:Rule
  val mediaSendDependenciesRule = MediaSendDependenciesRule(ApplicationProvider.getApplicationContext())

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `Given multi-capture is off, when toggled, then it is on`() {
    val viewModel = createViewModel()

    viewModel.onEvent(MediaSendFlowEvent.ToggleMultiCapture)

    assertThat(viewModel.state.value.isMultiCaptureEnabled).isTrue()
  }

  @Test
  fun `Given multi-capture is on, when toggled, then it is off`() {
    val viewModel = createViewModel()

    viewModel.onEvent(MediaSendFlowEvent.ToggleMultiCapture)
    viewModel.onEvent(MediaSendFlowEvent.ToggleMultiCapture)

    assertThat(viewModel.state.value.isMultiCaptureEnabled).isFalse()
  }

  @Test
  fun `Given multi-capture is off, when media is captured, then the editor opens`() {
    val viewModel = createViewModel()

    viewModel.onEvent(MediaSendFlowEvent.MediaCaptured(MEDIA))

    assertThat(viewModel.backStack.last()).isEqualTo(MediaSendRoute.Edit)
  }

  @Test
  fun `Given multi-capture is on, when media is captured, then the camera stays open`() {
    val viewModel = createViewModel()

    viewModel.onEvent(MediaSendFlowEvent.ToggleMultiCapture)
    viewModel.onEvent(MediaSendFlowEvent.MediaCaptured(MEDIA))

    assertThat(viewModel.backStack.last()).isEqualTo(MediaSendRoute.Capture.Camera)
  }

  private fun createViewModel(): MediaSendFlowViewModel {
    val savedStateHandle = SavedStateHandle(
      mapOf(
        "media_send_vm_args" to MediaSendFlowActivityContract.Args(isCameraFirst = true),
        "media_send_vm_identity_changes_since" to 0L
      )
    )

    return MediaSendFlowViewModel(
      savedStateHandle = savedStateHandle,
      repository = mediaSendDependenciesRule.mediaSendRepository,
      preUploadController = PreUploadController(),
      isMeteredFlow = emptyFlow()
    )
  }

  companion object {
    private val MEDIA = Media(
      uri = "content://media/1".toUri(),
      contentType = "image/jpeg",
      date = 0,
      width = 100,
      height = 100,
      size = 1024,
      duration = 0,
      isBorderless = false,
      isVideoGif = false,
      bucketId = null,
      caption = null,
      transformProperties = null,
      fileName = null
    )
  }
}
