/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.apng

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class ApngDrawableTest {

  private lateinit var drawable: ApngDrawable
  private lateinit var canvas: Canvas

  @Before
  fun setUp() {
    drawable = ApngDrawable(ApngDecoder.create { javaClass.classLoader!!.getResourceAsStream("apng/ball.png") })
    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    canvas = Canvas(Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888))
  }

  @Test
  fun `play stops after the requested number of loops`() {
    drawable.play(2)

    drawFrames(drawable.frameCount * 2)
    assertTrue("Still running until the frame after the last loop is due", drawable.isRunning)

    drawFrames(1)
    assertFalse(drawable.isRunning)
  }

  @Test
  fun `start does not resume a finished playback`() {
    drawable.play(1)
    drawFrames(drawable.frameCount + 1)

    drawable.start()

    assertFalse(drawable.isRunning)
  }

  @Test
  fun `start resumes a paused playback`() {
    drawable.play(1)
    drawFrames(1)
    drawable.stop()

    drawable.start()

    assertTrue(drawable.isRunning)
  }

  @Test
  fun `stopAtStart rests on the first frame and ignores start`() {
    drawable.play(1)
    drawFrames(3)

    drawable.stopAtStart()
    drawable.start()
    drawFrames(1)

    assertFalse(drawable.isRunning)
    assertEquals(0, drawable.position)
  }

  @Test
  fun `play restarts a playback stopped at start`() {
    drawable.stopAtStart()

    drawable.play(1)
    drawFrames(1)

    assertTrue(drawable.isRunning)
    assertEquals(1, drawable.position)
  }

  /**
   * Draws [count] times, advancing the clock past any frame delay so each draw advances one frame.
   */
  private fun drawFrames(count: Int) {
    repeat(count) {
      drawable.draw(canvas)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
    }
  }
}
