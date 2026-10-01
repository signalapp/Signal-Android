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
import assertk.assertions.isNull
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
 * Covers what the view-once toggle does to the message the user already typed: a view-once send cannot carry a body, so
 * the message is held aside while view-once is on and put back when it is turned off again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaSendFlowViewModelViewOnceTest {

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
  fun `when toggling view once on then off, then message is restored`() {
    val viewModel = createViewModel()

    viewModel.setMessage("a caption")

    viewModel.toggleViewOnce()
    assertThat(viewModel.state.value.isViewOnceEnabled).isTrue()
    assertThat(viewModel.state.value.message?.toString()).isNull()

    viewModel.toggleViewOnce()
    assertThat(viewModel.state.value.isViewOnceEnabled).isFalse()
    assertThat(viewModel.state.value.message?.toString()).isEqualTo("a caption")
    assertThat(viewModel.state.value.viewOnceStashedMessage?.toString()).isNull()
  }

  @Test
  fun `when toggling view once on with no message, then nothing is restored`() {
    val viewModel = createViewModel()

    viewModel.toggleViewOnce()
    viewModel.toggleViewOnce()

    assertThat(viewModel.state.value.message?.toString()).isNull()
    assertThat(viewModel.state.value.viewOnceStashedMessage?.toString()).isNull()
  }

  @Test
  fun `when setting a message after turning view once off, then the restored message is replaced`() {
    val viewModel = createViewModel()

    viewModel.setMessage("first")
    viewModel.toggleViewOnce()
    viewModel.toggleViewOnce()
    viewModel.setMessage("second")

    assertThat(viewModel.state.value.message?.toString()).isEqualTo("second")
  }

  private fun createViewModel(): MediaSendFlowViewModel {
    val media = Media(
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

    // Seeded directly rather than through Args.initialMedia, so the selection view-once needs is in place without
    // waiting on the asynchronous validation that adding media goes through.
    val savedStateHandle = SavedStateHandle(
      mapOf(
        "media_send_vm_args" to MediaSendFlowActivityContract.Args(),
        "media_send_vm_identity_changes_since" to 0L,
        "media_send_vm_state" to MediaSendFlowState(selectedMedia = listOf(media), focusedMedia = media)
      )
    )

    return MediaSendFlowViewModel(
      savedStateHandle = savedStateHandle,
      repository = mediaSendDependenciesRule.mediaSendRepository,
      preUploadController = PreUploadController(),
      isMeteredFlow = emptyFlow()
    )
  }
}
