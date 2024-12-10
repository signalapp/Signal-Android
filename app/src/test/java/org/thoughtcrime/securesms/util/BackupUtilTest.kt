package org.thoughtcrime.securesms.util

import androidx.documentfile.provider.DocumentFile
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.thoughtcrime.securesms.util.BackupUtil.BackupFileException

class BackupUtilTest {
  companion object {
    private const val TEST_NAME = "1920837192.backup"

    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  private val documentFile = mockk<DocumentFile>(relaxed = true)

  @Test
  fun `Given a non-existent uri, when I getBackupInfoFromSingleDocumentFile, then I expect NOT_FOUND`() {
    val error = assertThrows("Expected a BackupFileException", BackupFileException::class.java) {
      BackupUtil.getBackupInfoFromSingleDocumentFile(documentFile)
    }
    assertEquals(BackupUtil.BackupFileState.NOT_FOUND, error.state)
  }

  @Test
  fun `Given an existent but unreadable uri, when I getBackupInfoFromSingleDocumentFile, then I expect NOT_READABLE`() {
    givenFileExists()

    val error = assertThrows("Expected a BackupFileException", BackupFileException::class.java) {
      BackupUtil.getBackupInfoFromSingleDocumentFile(documentFile)
    }
    assertEquals(BackupUtil.BackupFileState.NOT_READABLE, error.state)
  }

  @Test
  fun `Given an existent readable uri with a bad extension, when I getBackupInfoFromSingleDocumentFile, then I expect UNSUPPORTED_FILE_EXTENSION`() {
    givenFileExists()
    givenFileIsReadable()

    val error = assertThrows("Expected a BackupFileException", BackupFileException::class.java) {
      BackupUtil.getBackupInfoFromSingleDocumentFile(documentFile)
    }
    assertEquals(BackupUtil.BackupFileState.UNSUPPORTED_FILE_EXTENSION, error.state)
  }

  @Test
  fun `Given an existent readable uri, when I getBackupInfoFromSingleDocumentFile, then I expect an info`() {
    givenFileExists()
    givenFileIsReadable()
    givenFileHasCorrectExtension()

    val info = BackupUtil.getBackupInfoFromSingleDocumentFile(documentFile)
    assertNotNull(info)
  }

  private fun givenFileExists() {
    every { documentFile.exists() } returns true
  }

  private fun givenFileIsReadable() {
    every { documentFile.canRead() } returns true
  }

  private fun givenFileHasCorrectExtension() {
    every { documentFile.name } returns TEST_NAME
  }
}
