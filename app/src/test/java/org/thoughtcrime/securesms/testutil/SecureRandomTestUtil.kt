package org.thoughtcrime.securesms.testutil

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import java.security.SecureRandom

object SecureRandomTestUtil {
  /**
   * Creates a [SecureRandom] that returns exactly the {@param returnValue} the first time
   * its [SecureRandom.nextBytes]} method is called.
   *
   * Any attempt to call with the incorrect length, or a second time will fail.
   */
  @JvmStatic
  fun mockRandom(returnValue: ByteArray): SecureRandom {
    return mockk<SecureRandom> {
      var count = 0
      val slot = slot<ByteArray>()
      every {
        nextBytes(capture(slot))
      } answers {
        assertEquals("SecureRandom Mock: nextBytes only expected to be called once", 1, ++count)
        val output = slot.captured
        assertEquals("SecureRandom Mock: nextBytes byte[] length requested does not match byte[] setup", returnValue.size, output.size)
        System.arraycopy(returnValue, 0, output, 0, returnValue.size)
      }
    }
  }
}
