package org.thoughtcrime.securesms.giph.mp4

import org.junit.Assert
import org.junit.Test
import java.util.concurrent.TimeUnit

class GiphyMp4PlaybackPolicyEnforcerTest {

  @Test
  fun `Given a 1s video, then I expect as many loops as fit in 6s`() {
    assertLoops(mediaDuration = TimeUnit.SECONDS.toMillis(1), expectedLoops = 6)
  }

  @Test
  fun `Given a 1_5s video, then I expect as many loops as fit in 6s`() {
    assertLoops(mediaDuration = 1500, expectedLoops = 4)
  }

  @Test
  fun `Given a 3s video, then I expect the minimum of 3 loops`() {
    assertLoops(mediaDuration = TimeUnit.SECONDS.toMillis(3), expectedLoops = 3)
  }

  @Test
  fun `Given a 10s video, then I expect the minimum of 3 loops`() {
    assertLoops(mediaDuration = TimeUnit.SECONDS.toMillis(10), expectedLoops = 3)
  }

  @Test
  fun `Given an unknown duration, then I expect the minimum of 3 loops`() {
    assertLoops(mediaDuration = -1, expectedLoops = 3)
  }

  private fun assertLoops(mediaDuration: Long, expectedLoops: Int) {
    var ended = false
    val testSubject = GiphyMp4PlaybackPolicyEnforcer { ended = true }

    testSubject.setMediaDuration(mediaDuration)

    Assert.assertTrue((1 until expectedLoops).map { testSubject.endPlayback() }.all { !it })
    Assert.assertFalse(ended)
    Assert.assertTrue(testSubject.endPlayback())
    Assert.assertTrue(ended)
  }
}
