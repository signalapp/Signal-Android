package org.signal.smsexporter.internal.mms

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.signal.smsexporter.TestUtils

@RunWith(RobolectricTestRunner::class)
class GetOrCreateMmsThreadIdsUseCaseTest {

  @Before
  fun setUp() {
    TestUtils.setUpMmsContentProviderAndResolver()
  }

  @Test
  fun `Given a message, when I execute, then I update the cache with the thread id`() {
    // GIVEN
    val mms = TestUtils.generateMmsMessage()
    val threadCache = mutableMapOf<Set<String>, Long>()

    // WHEN
    val result = GetOrCreateMmsThreadIdsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      mms,
      threadCache
    )

    // THEN
    result.either(
      onSuccess = {
        assertEquals(threadCache[mms.addresses], it.threadId)
      },
      onFailure = {
        throw it
      }
    )
  }
}
