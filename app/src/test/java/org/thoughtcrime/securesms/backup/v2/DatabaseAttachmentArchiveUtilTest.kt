/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.app.Application
import arrow.core.right
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Covers which archive CDN ends up in the pointer we download from. A restore that reaches for the wrong CDN gets a 404, and
 * [org.thoughtcrime.securesms.jobs.RestoreAttachmentJob] treats a 404 as permanent, so the precedence here decides whether media survives a stale CDN number.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DatabaseAttachmentArchiveUtilTest {

  companion object {
    private const val FALLBACK_CDN = 3
    private const val STORED_CDN = 42
  }

  @get:Rule
  val mockSignalStore = MockSignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Before
  fun setUp() {
    every { SignalStore.backup.mediaRootBackupKey } returns MediaRootBackupKey(ByteArray(32))
    coEvery { AppDependencies.archiveService.getArchivedMediaCdnPath() } returns "backups/media".right()

    mockkObject(RemoteConfig)
    every { RemoteConfig.backupFallbackArchiveCdn } returns FALLBACK_CDN
  }

  @After
  fun tearDown() {
    unmockkObject(RemoteConfig)
  }

  @Test
  fun `uses the stored archive cdn when there is no override`() {
    val pointer = archivedAttachment(archiveCdn = STORED_CDN).createArchiveAttachmentPointer(useArchiveCdn = true)

    assertThat(pointer.cdnNumber).isEqualTo(STORED_CDN)
  }

  @Test
  fun `falls back to the configured cdn when nothing is stored`() {
    val pointer = archivedAttachment(archiveCdn = null).createArchiveAttachmentPointer(useArchiveCdn = true)

    assertThat(pointer.cdnNumber).isEqualTo(FALLBACK_CDN)
  }

  /**
   * The retry path: a stored CDN that 404s is worth re-attempting against the CDN a missing value would have resolved to, so the override has to win over it.
   */
  @Test
  fun `prefers the override over the stored archive cdn`() {
    val pointer = archivedAttachment(archiveCdn = STORED_CDN).createArchiveAttachmentPointer(useArchiveCdn = true, archiveCdnOverride = FALLBACK_CDN)

    assertThat(pointer.cdnNumber).isEqualTo(FALLBACK_CDN)
  }

  private fun archivedAttachment(archiveCdn: Int?): DatabaseAttachment {
    return DatabaseAttachment(
      attachmentId = AttachmentId(1L),
      mmsId = 42L,
      hasData = true,
      hasThumbnail = false,
      contentType = "image/jpeg",
      transferProgress = AttachmentTable.TRANSFER_PROGRESS_DONE,
      size = 1_000L,
      fileName = "photo.jpg",
      cdn = Cdn.CDN_3,
      location = null,
      key = Base64.encodeWithPadding(ByteArray(64) { 1 }),
      digest = null,
      incrementalDigest = null,
      incrementalMacChunkSize = 0,
      fastPreflightId = null,
      voiceNote = false,
      borderless = false,
      videoGif = false,
      width = 0,
      height = 0,
      quote = false,
      caption = null,
      stickerLocator = null,
      blurHash = null,
      audioHash = null,
      transformProperties = null,
      displayOrder = 0,
      uploadTimestamp = 0,
      dataHash = Base64.encodeWithPadding(ByteArray(32) { 2 }),
      archiveCdn = archiveCdn,
      thumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.NONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.FINISHED,
      archiveThumbnailTransferState = AttachmentTable.ArchiveTransferState.NONE,
      uuid = null,
      quoteTargetContentType = null,
      metadata = null
    )
  }
}
