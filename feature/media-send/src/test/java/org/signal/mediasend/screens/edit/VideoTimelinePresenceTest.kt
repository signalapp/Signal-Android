/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class VideoTimelinePresenceTest {

  @Test
  fun `Given the pager rests on a video, then its timeline is fully shown`() {
    assertThat(VideoTimelinePresence.at(2f) { it == 2 }).isEqualTo(VideoTimelinePresence(page = 2, alpha = 1f))
  }

  @Test
  fun `Given the pager rests on a non-video, then no timeline is shown`() {
    assertThat(VideoTimelinePresence.at(2f) { it != 2 }).isNull()
  }

  @Test
  fun `Given a swipe from a video to a non-video, then the timeline fades with the video`() {
    assertThat(VideoTimelinePresence.at(1.25f) { it == 1 }).isEqualTo(VideoTimelinePresence(page = 1, alpha = 0.75f))
  }

  @Test
  fun `Given a swipe from a non-video to a video, then the timeline fades in with the video`() {
    assertThat(VideoTimelinePresence.at(1.25f) { it == 2 }).isEqualTo(VideoTimelinePresence(page = 2, alpha = 0.25f))
  }

  @Test
  fun `Given a swipe between two videos, then the nearer one is shown, faded by how close the other is`() {
    assertThat(VideoTimelinePresence.at(1.25f) { true }).isEqualTo(VideoTimelinePresence(page = 1, alpha = 0.5f))
    assertThat(VideoTimelinePresence.at(1.75f) { true }).isEqualTo(VideoTimelinePresence(page = 2, alpha = 0.5f))
  }

  @Test
  fun `Given a swipe exactly halfway between two videos, then the timeline is invisible`() {
    assertThat(VideoTimelinePresence.at(1.5f) { true }).isEqualTo(VideoTimelinePresence(page = 1, alpha = 0f))
  }

  @Test
  fun `Given the pager rests on a page, then the next page is not consulted`() {
    assertThat(VideoTimelinePresence.at(0f) { it == 1 }).isNull()
  }
}
