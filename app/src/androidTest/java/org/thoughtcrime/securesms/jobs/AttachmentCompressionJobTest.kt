/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.StreamUtil
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.UriAttachmentBuilder
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.util.MediaUtil
import java.util.Optional
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class AttachmentCompressionJobTest {

  @get:Rule
  val harness = SignalActivityRule()

  @Test
  fun testCompressionJobsWithDifferentTransformPropertiesCompleteSuccessfully() {
    val imageBytes: ByteArray = InstrumentationRegistry.getInstrumentation().context.resources.assets.open("images/sample_image.png").use {
      StreamUtil.readFully(it)
    }

    val blob = BlobProvider.getInstance().forData(imageBytes).createForSingleSessionOnDisk(AppDependencies.application)

    val firstPreUpload = createAttachment(1, blob, AttachmentTable.TransformProperties.empty())
    val firstDatabaseAttachment = SignalDatabase.attachments.insertAttachmentForPreUpload(firstPreUpload)

    val firstCompressionJob: AttachmentCompressionJob = AttachmentCompressionJob.fromAttachment(firstDatabaseAttachment, false, -1)

    var secondCompressionJob: AttachmentCompressionJob? = null
    var firstJobResult: Job.Result? = null
    var secondJobResult: Job.Result? = null

    val secondJobLatch = CountDownLatch(1)
    val jobThread = Thread {
      firstCompressionJob.setContext(AppDependencies.application)
      firstJobResult = firstCompressionJob.run()

      secondJobLatch.await()

      secondCompressionJob!!.setContext(AppDependencies.application)
      secondJobResult = secondCompressionJob!!.run()
    }

    jobThread.start()
    val secondPreUpload = createAttachment(1, blob, AttachmentTable.TransformProperties.forSentMediaQuality(Optional.empty(), SentMediaQuality.HIGH))
    val secondDatabaseAttachment = SignalDatabase.attachments.insertAttachmentForPreUpload(secondPreUpload)
    secondCompressionJob = AttachmentCompressionJob.fromAttachment(secondDatabaseAttachment, false, -1)

    secondJobLatch.countDown()

    jobThread.join()

    firstJobResult!!.isSuccess assertIs true
    secondJobResult!!.isSuccess assertIs true
  }

  private fun createAttachment(id: Long, uri: Uri, transformProperties: AttachmentTable.TransformProperties): UriAttachment {
    return UriAttachmentBuilder.build(
      id,
      uri = uri,
      contentType = MediaUtil.IMAGE_JPEG,
      transformProperties = transformProperties
    )
  }
}
