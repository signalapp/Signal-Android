package org.thoughtcrime.securesms.sharing

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DistributionListMultiShareTimestampProviderTest {

  @Test
  fun `When I ask for index 4, then I expect to fill 4 new items`() {
    val generator = mutableListOf(1L, 2, 3, 4, 5).map { it.seconds }.toMutableList()
    val testSubject = DistributionListMultiShareTimestampProvider(getCurrentTimeMillis = { generator.removeAt(0) }, sleepTimeout = 0.seconds)

    val actual = testSubject.getMillis(4).milliseconds
    assertEquals(5.seconds, actual)
  }
}
