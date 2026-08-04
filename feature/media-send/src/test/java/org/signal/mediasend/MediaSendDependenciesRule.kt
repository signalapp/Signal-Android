/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import org.junit.rules.ExternalResource
import org.signal.camera.CameraDependencies
import org.signal.mediasend.screens.edit.image.BrushWidths
import kotlin.time.Duration.Companion.seconds

/**
 * Stands up the module dependency graph that [MediaSendFlowState] reaches into for its own defaults, so tests can build a
 * state without an app around them.
 */
class MediaSendDependenciesRule(private val application: Application) : ExternalResource() {

  val mediaSendRepository: MediaSendRepository = mockk(relaxed = true) {
    every { sentMediaQuality } returns SentMediaQuality.STANDARD
    every { getMediaConstraints() } returns PreviewMediaConstraints
    every { storyMaxVideoDuration } returns 30.seconds
    every { brushWidths } returns BrushWidths()
  }

  override fun before() {
    CameraDependencies.init(application, mockk(relaxed = true))
    MediaSendDependencies.init(
      application,
      mockk(relaxed = true) {
        every { provideMediaSendRepository() } returns mediaSendRepository
      }
    )
  }
}
