package org.thoughtcrime.securesms.conversationlist.chatfilter

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ProgressVelocityTrackerTest {

  @Test
  fun `When I calculate velocity, then I expect 0f`() {
    val testSubject = ProgressVelocityTracker(3)
    val actual = testSubject.calculateVelocity()
    assertEquals("Velocity of an empty tracker should be 0f", 0f, actual)
  }

  @Test
  fun `Given a single entry, when I calculate velocity, then I expect 0f`() {
    val testSubject = ProgressVelocityTracker(3)
    testSubject.submitProgress(0f, 0.milliseconds)
    val actual = testSubject.calculateVelocity()
    assertEquals("Velocity of a tracker with a single element should be 0f", 0f, actual)
  }

  @Test
  fun `Given 0f to 1f in 1 second, when I calculate velocity, then I expect a rate of 100 percent per second`() {
    val testSubject = ProgressVelocityTracker(3)
    testSubject.submitProgress(0f, 0.milliseconds)
    testSubject.submitProgress(1f, 1.seconds)
    val actual = testSubject.calculateVelocity()
    assertEquals("If we complete the progress in 1 second, then we should have a rate of 1 per thousand milliseconds", 1f, actual)
  }

  @Test
  fun `Given 5 entries, when I calculate velocity, then I expect a rate based off the last 3 entries`() {
    val testSubject = ProgressVelocityTracker(3)
    val entries = listOf(
      0.0f to 0.seconds,
      0.1f to 10.milliseconds,
      0.2f to 20.milliseconds,
      0.3f to 40.milliseconds,
      0.4f to 80.milliseconds
    )

    entries.forEach { (progress, duration) -> testSubject.submitProgress(progress, duration) }

    val velocityA = testSubject.calculateVelocity()

    testSubject.clear()

    entries.drop(2).forEach { (progress, duration) -> testSubject.submitProgress(progress, duration) }

    val velocityB = testSubject.calculateVelocity()

    assertEquals("Expected the velocities to match.", velocityA, velocityB)
  }
}
