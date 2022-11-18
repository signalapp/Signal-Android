package org.thoughtcrime.securesms.giph.mp4

import org.junit.Assert
import org.junit.Test
import java.util.concurrent.TimeUnit

class GiphyMp4PlaybackPolicyEnforcerTest {

  @Test
  fun `Given a 1s video, when I have a max time of 8s and max repeats of 4, then I expect 4 loops`() {
    val mediaDuration = TimeUnit.SECONDS.toMillis(1)
    val maxDuration = TimeUnit.SECONDS.toMillis(8)
    val maxRepeats = 4L
    var ended = false
    val testSubject = GiphyMp4PlaybackPolicyEnforcer({ ended = true }, maxDuration, maxRepeats)

    testSubject.setMediaDuration(mediaDuration)

    Assert.assertTrue((0..2).map { testSubject.endPlayback() }.all { !it })
    Assert.assertFalse(ended)
    Assert.assertTrue(testSubject.endPlayback())
    Assert.assertTrue(ended)
  }

  @Test
  fun `Given a 3s video, when I have a max time of 8s and max repeats of 4, then I expect 2 loops`() {
    val mediaDuration = TimeUnit.SECONDS.toMillis(3)
    val maxDuration = TimeUnit.SECONDS.toMillis(8)
    val maxRepeats = 4L
    var ended = false
    val testSubject = GiphyMp4PlaybackPolicyEnforcer({ ended = true }, maxDuration, maxRepeats)

    testSubject.setMediaDuration(mediaDuration)

    Assert.assertFalse(testSubject.endPlayback())
    Assert.assertFalse(ended)
    Assert.assertTrue(testSubject.endPlayback())
    Assert.assertTrue(ended)
  }

  @Test
  fun `Given a 10s video, when I have a max time of 8s and max repeats of 4, then I expect 1 loop`() {
    val mediaDuration = TimeUnit.SECONDS.toMillis(10)
    val maxDuration = TimeUnit.SECONDS.toMillis(8)
    val maxRepeats = 4L
    var ended = false
    val testSubject = GiphyMp4PlaybackPolicyEnforcer({ ended = true }, maxDuration, maxRepeats)

    testSubject.setMediaDuration(mediaDuration)

    Assert.assertTrue(testSubject.endPlayback())
    Assert.assertTrue(ended)
  }
}
