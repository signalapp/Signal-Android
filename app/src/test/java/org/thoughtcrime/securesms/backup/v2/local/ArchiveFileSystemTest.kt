/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.local

import android.app.Application
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ArchiveFileSystemTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `openForRestore succeeds when given the parent directory`() {
    val parent = temporaryFolder.newFolder()
    buildSignalBackupsStructure(parent)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(parent))

    assertThat(result).isNotNull()
  }

  @Test
  fun `openForRestore isRootedAtSignalBackups is false when given the parent directory`() {
    val parent = temporaryFolder.newFolder()
    buildSignalBackupsStructure(parent)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(parent))!!

    assertThat(result.isRootedAtSignalBackups).isFalse()
  }

  @Test
  fun `openForRestore succeeds when given the SignalBackups directory directly`() {
    val parent = temporaryFolder.newFolder()
    val signalBackups = buildSignalBackupsStructure(parent)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(signalBackups))

    assertThat(result).isNotNull()
  }

  @Test
  fun `openForRestore isRootedAtSignalBackups is true when given the SignalBackups directory directly`() {
    val parent = temporaryFolder.newFolder()
    val signalBackups = buildSignalBackupsStructure(parent)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(signalBackups))!!

    assertThat(result.isRootedAtSignalBackups).isTrue()
  }

  @Test
  fun `openForRestore isRootedAtSignalBackups is false when parent is named SignalBackups but contains a real SignalBackups subfolder`() {
    val outerSignalBackups = temporaryFolder.newFolder(ArchiveFileSystem.MAIN_DIRECTORY_NAME)
    buildSignalBackupsStructure(outerSignalBackups)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(outerSignalBackups))!!

    assertThat(result.isRootedAtSignalBackups).isFalse()
  }

  @Test
  fun `openForRestore returns null for a directory named SignalBackups without expected structure`() {
    val fakeSignalBackups = temporaryFolder.newFolder(ArchiveFileSystem.MAIN_DIRECTORY_NAME)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(fakeSignalBackups))

    assertThat(result).isNull()
  }

  @Test
  fun `openForRestore returns null for an unrelated directory`() {
    val unrelated = temporaryFolder.newFolder("SomeOtherFolder")

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(unrelated))

    assertThat(result).isNull()
  }

  @Test
  fun `openForRestore succeeds when the archive lives in a differently-named directory`() {
    val renamed = temporaryFolder.newFolder("Signal")
    buildArchiveContents(renamed)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(renamed))

    assertThat(result).isNotNull()
  }

  @Test
  fun `openForRestore isRootedAtSignalBackups is true for a differently-named archive directory`() {
    val renamed = temporaryFolder.newFolder("Signal")
    buildArchiveContents(renamed)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(renamed))!!

    assertThat(result.isRootedAtSignalBackups).isTrue()
  }

  @Test
  fun `openForRestore returns null for a differently-named directory that only contains a files folder`() {
    val renamed = temporaryFolder.newFolder("Signal")
    renamed.resolve("files").mkdir()

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(renamed))

    assertThat(result).isNull()
  }

  @Test
  fun `createSnapshot folder name round-trips through listSnapshots in a non-UTC time zone`() {
    val defaultTimeZone = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))

      val parent = temporaryFolder.newFolder()
      val fileSystem = ArchiveFileSystem.fromFile(context, parent)

      val before = System.currentTimeMillis() / 1000 * 1000
      val snapshot = fileSystem.createSnapshot()!!
      snapshot.finalize()
      val after = System.currentTimeMillis()

      val info = fileSystem.listSnapshots().single()
      assertThat(info.timestamp).isBetween(before, after)
    } finally {
      TimeZone.setDefault(defaultTimeZone)
    }
  }

  @Test
  fun `listSnapshots sorts a newer local-time snapshot ahead of an older UTC-named one`() {
    val defaultTimeZone = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))

      val parent = temporaryFolder.newFolder()
      val fileSystem = ArchiveFileSystem.fromFile(context, parent)
      val signalBackups = parent.resolve(ArchiveFileSystem.MAIN_DIRECTORY_NAME)

      // Written by a version that stamped names in UTC. Created first, but its name reads 7 hours ahead of local time.
      val older = signalBackups.resolve("${ArchiveFileSystem.BACKUP_DIRECTORY_PREFIX}-2026-08-03-18-55-33").also { it.mkdir() }
      // Written after the switch to local-time names, so its name reads lower despite being newer.
      val newer = signalBackups.resolve("${ArchiveFileSystem.BACKUP_DIRECTORY_PREFIX}-2026-08-03-12-04-35").also { it.mkdir() }

      older.setLastModified(1_785_783_333_000)
      newer.setLastModified(1_785_783_875_000)

      val snapshots = fileSystem.listSnapshots()

      assertThat(snapshots.map { it.name }).isEqualTo(listOf(newer.name, older.name))
    } finally {
      TimeZone.setDefault(defaultTimeZone)
    }
  }

  @Test
  fun `listSnapshots falls back to the name when no modified time is reported`() {
    val parent = temporaryFolder.newFolder()
    val fileSystem = ArchiveFileSystem.fromFile(context, parent)
    val signalBackups = parent.resolve(ArchiveFileSystem.MAIN_DIRECTORY_NAME)
    val snapshot = signalBackups.resolve("${ArchiveFileSystem.BACKUP_DIRECTORY_PREFIX}-2026-01-02-03-04-05").also { it.mkdir() }
    snapshot.setLastModified(0)

    val info = fileSystem.listSnapshots().single()

    val expected = Calendar.getInstance(Locale.US).apply {
      set(2026, Calendar.JANUARY, 2, 3, 4, 5)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    assertThat(info.timestamp).isEqualTo(expected)
  }

  @Test
  fun `listSnapshots sorts newest first`() {
    val parent = temporaryFolder.newFolder()
    val fileSystem = ArchiveFileSystem.fromFile(context, parent)
    val signalBackups = parent.resolve(ArchiveFileSystem.MAIN_DIRECTORY_NAME)
    signalBackups.resolve("${ArchiveFileSystem.BACKUP_DIRECTORY_PREFIX}-2026-01-01-00-00-00").also { it.mkdir() }.setLastModified(1_767_225_600_000)
    signalBackups.resolve("${ArchiveFileSystem.BACKUP_DIRECTORY_PREFIX}-2026-01-02-00-00-00").also { it.mkdir() }.setLastModified(1_767_312_000_000)

    val snapshots = fileSystem.listSnapshots()

    assertThat(snapshots.map { it.name }).isEqualTo(
      listOf(
        "${ArchiveFileSystem.BACKUP_DIRECTORY_PREFIX}-2026-01-02-00-00-00",
        "${ArchiveFileSystem.BACKUP_DIRECTORY_PREFIX}-2026-01-01-00-00-00"
      )
    )
  }

  /**
   * Creates the SignalBackups directory structure inside [parent] and returns the SignalBackups directory.
   */
  private fun buildSignalBackupsStructure(parent: java.io.File): java.io.File {
    val signalBackups = parent.resolve(ArchiveFileSystem.MAIN_DIRECTORY_NAME).also { it.mkdir() }
    signalBackups.resolve("files").mkdir()
    return signalBackups
  }

  /**
   * Creates the raw archive contents (a "files" directory and a snapshot directory) directly inside [dir].
   */
  private fun buildArchiveContents(dir: java.io.File) {
    dir.resolve("files").mkdir()
    dir.resolve("${ArchiveFileSystem.BACKUP_DIRECTORY_PREFIX}-2026-01-01-00-00-00").mkdir()
  }
}
