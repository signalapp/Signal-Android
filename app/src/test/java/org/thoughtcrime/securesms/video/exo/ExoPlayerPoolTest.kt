package org.thoughtcrime.securesms.video.exo

import com.google.android.exoplayer2.ExoPlayer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock

@RunWith(JUnit4::class)
class ExoPlayerPoolTest {

  @Test
  fun `Given an empty pool, when I require a player, then I expect a player`() {
    // GIVEN
    val testSubject = createTestSubject(1, 1)

    // WHEN
    val player = testSubject.require()

    // THEN
    assertNotNull(player)
  }

  @Test(expected = IllegalStateException::class)
  fun `Given a pool without available players, when I require a player, then I expect an exception`() {
    // GIVEN
    val testSubject = createTestSubject(1, 0)

    // WHEN
    testSubject.require()

    // THEN
    fail("Expected an IllegalStateException")
  }

  @Test
  fun `Given a pool that allows 10 unreserved items, when I ask for 20, then I expect 10 items and 10 nulls`() {
    // GIVEN
    val testSubject = createTestSubject(0, 10)

    // WHEN
    val players = (1..10).map { testSubject.get() }
    val nulls = (1..10).map { testSubject.get() }

    // THEN
    assertTrue(players.all { it != null })
    assertTrue(nulls.all { it == null })
  }

  @Test
  fun `Given a pool that allows 10 items and has all items checked out, when I return then check them all out again, then I expect 10 non null players`() {
    // GIVEN
    val testSubject = createTestSubject(0, 10)
    val players = (1..10).map { testSubject.get() }

    // WHEN
    players.filterNotNull().forEach { testSubject.pool(it) }
    val morePlayers = (1..10).map { testSubject.get() }

    assertTrue(morePlayers.all { it != null })
  }

  @Test(expected = IllegalArgumentException::class)
  fun `Given an ExoPlayer not in the pool, when I pool it, then I expect an IllegalArgumentException`() {
    // GIVEN
    val player = mock(ExoPlayer::class.java)
    val pool = createTestSubject(1, 10)

    // WHEN
    pool.pool(player)

    // THEN
    fail("Expected an IllegalArgumentException to be thrown")
  }

  private fun createTestSubject(
    maximumReservedPlayers: Int,
    maximumSimultaneousPlayback: Int
  ): ExoPlayerPool<ExoPlayer> {
    return object : ExoPlayerPool<ExoPlayer>(maximumReservedPlayers) {
      override fun createPlayer(): ExoPlayer {
        return mock(ExoPlayer::class.java)
      }

      override fun getMaxSimultaneousPlayback(): Int {
        return maximumSimultaneousPlayback
      }
    }
  }
}
