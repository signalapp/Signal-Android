/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.SqlUtil
import org.signal.core.util.crypto.AttachmentSecret
import org.signal.core.util.crypto.AttachmentSecretProvider
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.AppInitialization
import org.thoughtcrime.securesms.backup.BackupPassphrase
import org.thoughtcrime.securesms.backup.FullBackupImporter
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.DataRestoreConstraint
import org.thoughtcrime.securesms.jobs.E164FormattingJob
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.service.LocalBackupListener
import org.thoughtcrime.securesms.testutil.LogRecorder
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.util.BackupUtil
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class RestoreRepositoryTest {
  @get:Rule
  val signalStore = MockSignalStoreRule(relaxed = setOf(SettingsValues::class))

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var context: Context
  private lateinit var backupUri: Uri
  private lateinit var logRecorder: LogRecorder
  private lateinit var database: SQLiteDatabase
  private lateinit var attachmentSecret: AttachmentSecret
  private lateinit var attachmentSecretProvider: AttachmentSecretProvider
  private lateinit var notificationChannels: NotificationChannels

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    backupUri = Uri.parse("content://org.thoughtcrime.securesms/backups/signal-backup")
    logRecorder = LogRecorder()
    Log.initialize(logRecorder)

    database = mockk(relaxed = true)
    attachmentSecret = mockk(relaxed = true)
    attachmentSecretProvider = mockk()
    notificationChannels = mockk(relaxed = true)

    mockkObject(SignalDatabase.Companion)
    mockkStatic(
      FullBackupImporter::class,
      BackupPassphrase::class,
      AttachmentSecretProvider::class,
      NotificationChannels::class,
      BackupUtil::class,
      LocalBackupListener::class,
      AppInitialization::class
    )

    every { SignalDatabase.backupDatabase } returns database
    every { SignalDatabase.runPostBackupRestoreTasks(any()) } answers { }
    every { BackupPassphrase.set(any(), any()) } answers { }
    every { AttachmentSecretProvider.getInstance(any(), any()) } returns attachmentSecretProvider
    every { attachmentSecretProvider.getOrCreateAttachmentSecret() } returns attachmentSecret
    every { NotificationChannels.getInstance() } returns notificationChannels
    every { BackupUtil.canUserAccessBackupDirectory(any()) } returns true
    every { LocalBackupListener.setNextBackupTimeToIntervalFromNow(any()) } returns 0L
    every { LocalBackupListener.schedule(any()) } answers { }
    every { AppInitialization.onPostBackupRestore(any()) } answers { }
    every { signalStore.registration.localRegistrationMetadata } returns null
    every { FullBackupImporter.validatePassphrase(any(), any(), any()) } returns true
    stubImportFile(throwable = null)

    DataRestoreConstraint.isRestoringData = false
  }

  @After
  fun tearDown() {
    DataRestoreConstraint.isRestoringData = false
    unmockkAll()
  }

  @Test
  fun `validation returning false is passphrase failure and does not import`() {
    every { FullBackupImporter.validatePassphrase(any(), any(), any()) } returns false

    val result = restore()

    assertThat(result).isEqualTo(RestoreRepository.BackupImportResult.FAILURE_PASSPHRASE_VALIDATION)
    verifyImportNotCalled()
    verifyPostRestoreWorkSkipped()
  }

  @Test
  fun `validation IOException is unknown failure and cleans up`() {
    val ioException = IOException("unreadable content uri")
    every { FullBackupImporter.validatePassphrase(any(), any(), any()) } throws ioException

    val result = restore()

    assertThat(result).isEqualTo(RestoreRepository.BackupImportResult.FAILURE_UNKNOWN)
    verifyImportNotCalled()
    verifyPostRestoreWorkSkipped()
    assertUnknownFailureLogged(ioException)
  }

  @Test
  fun `import IOException is unknown failure and skips post restore work`() {
    val ioException = IOException("backup read failed after restore started")
    stubImportFile(ioException)

    val result = restore()

    assertThat(result).isEqualTo(RestoreRepository.BackupImportResult.FAILURE_UNKNOWN)
    verify(exactly = 1) {
      FullBackupImporter.importFile(context, attachmentSecret, database, backupUri, PASSPHRASE, false)
    }
    verifyPostRestoreWorkSkipped()
    assertUnknownFailureLogged(ioException)
  }

  @Test
  fun `import DatabaseDowngradeException keeps the downgrade result`() {
    stubImportFile(databaseDowngradeException())

    val result = restore()

    assertThat(result).isEqualTo(RestoreRepository.BackupImportResult.FAILURE_VERSION_DOWNGRADE)
    verifyPostRestoreWorkSkipped()
  }

  @Test
  fun `import ForeignKeyViolationException keeps the foreign key result`() {
    stubImportFile(foreignKeyViolationException())

    val result = restore()

    assertThat(result).isEqualTo(RestoreRepository.BackupImportResult.FAILURE_FOREIGN_KEY)
    verifyPostRestoreWorkSkipped()
  }

  @Test
  fun `valid backup imports and runs post restore work`() {
    val result = restore()

    assertThat(result).isEqualTo(RestoreRepository.BackupImportResult.SUCCESS)
    verify(exactly = 1) {
      FullBackupImporter.importFile(context, attachmentSecret, database, backupUri, PASSPHRASE, false)
    }
    verify(exactly = 1) { SignalDatabase.runPostBackupRestoreTasks(database) }
    verify(exactly = 1) { notificationChannels.restoreContactNotificationChannels() }
    verify(exactly = 1) { AppDependencies.jobManager.add(ofType<E164FormattingJob>()) }
    verify(exactly = 1) { BackupUtil.canUserAccessBackupDirectory(context) }
    verify(exactly = 1) { LocalBackupListener.setNextBackupTimeToIntervalFromNow(context) }
    verify(exactly = 1) { signalStore.settings.isBackupEnabled = true }
    verify(exactly = 1) { LocalBackupListener.schedule(context) }
    verify(exactly = 1) { AppInitialization.onPostBackupRestore(context) }
  }

  private fun restore(): RestoreRepository.BackupImportResult {
    DataRestoreConstraint.isRestoringData = true

    val result = runBlocking {
      RestoreRepository.restoreBackupAsynchronously(context, backupUri, PASSPHRASE)
    }

    assertThat(DataRestoreConstraint.isRestoringData).isFalse()
    return result
  }

  private fun stubImportFile(throwable: Throwable?) {
    every {
      FullBackupImporter.importFile(
        any<Context>(),
        any<AttachmentSecret>(),
        any<SQLiteDatabase>(),
        any<Uri>(),
        any<String>(),
        any<Boolean>()
      )
    } answers {
      if (throwable != null) {
        throw throwable
      }
    }
  }

  private fun verifyImportNotCalled() {
    verify(exactly = 0) {
      FullBackupImporter.importFile(
        any<Context>(),
        any<AttachmentSecret>(),
        any<SQLiteDatabase>(),
        any<Uri>(),
        any<String>(),
        any<Boolean>()
      )
    }
  }

  private fun verifyPostRestoreWorkSkipped() {
    verify(exactly = 0) { SignalDatabase.runPostBackupRestoreTasks(any()) }
    verify(exactly = 0) { notificationChannels.restoreContactNotificationChannels() }
    verify(exactly = 0) { AppDependencies.jobManager.add(ofType<E164FormattingJob>()) }
    verify(exactly = 0) { LocalBackupListener.setNextBackupTimeToIntervalFromNow(any()) }
    verify(exactly = 0) { LocalBackupListener.schedule(any()) }
    verify(exactly = 0) { AppInitialization.onPostBackupRestore(any()) }
    verify(exactly = 0) { signalStore.settings.isBackupEnabled = true }
  }

  private fun assertUnknownFailureLogged(throwable: Throwable) {
    val warnings = logRecorder.warnings.filter { entry ->
      entry.tag == "RestoreRepository" && entry.message == "Restore failed due to unknown error!" && entry.throwable == throwable
    }
    assertThat(warnings).hasSize(1)
  }

  private fun databaseDowngradeException(): FullBackupImporter.DatabaseDowngradeException {
    val constructor = FullBackupImporter.DatabaseDowngradeException::class.java.getDeclaredConstructor(
      Int::class.javaPrimitiveType!!,
      Int::class.javaPrimitiveType!!
    )
    constructor.isAccessible = true
    return constructor.newInstance(1, 2) as FullBackupImporter.DatabaseDowngradeException
  }

  private fun foreignKeyViolationException(): FullBackupImporter.ForeignKeyViolationException {
    return FullBackupImporter.ForeignKeyViolationException(
      listOf(
        SqlUtil.ForeignKeyViolation(
          table = "message",
          violatingRowId = 1L,
          dependsOnTable = "thread",
          column = "thread_id"
        )
      )
    )
  }

  companion object {
    private const val PASSPHRASE = "12345 67890"
  }
}
