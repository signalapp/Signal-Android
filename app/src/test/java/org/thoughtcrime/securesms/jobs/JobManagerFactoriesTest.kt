package org.thoughtcrime.securesms.jobs

import android.app.Application
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class JobManagerFactoriesTest {
  @Test
  fun test_PushContentReceiveJob_is_retired() {
    val factories = JobManagerFactories.getJobFactories(mockk<Application>())

    assertTrue(factories["PushContentReceiveJob"] is FailingJob.Factory)
  }

  @Test
  fun test_AttachmentUploadJob_is_retired() {
    val factories = JobManagerFactories.getJobFactories(mockk<Application>())

    assertTrue(factories["AttachmentUploadJob"] is FailingJob.Factory)
  }

  @Test
  fun test_MmsSendJob_is_retired() {
    val factories = JobManagerFactories.getJobFactories(mockk<Application>())

    assertTrue(factories["MmsSendJob"] is FailingJob.Factory)
  }
}
