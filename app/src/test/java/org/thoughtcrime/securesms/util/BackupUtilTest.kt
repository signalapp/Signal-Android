package org.thoughtcrime.securesms.util

import androidx.documentfile.provider.DocumentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.EmptyLogger

class BackupUtilTest {

  companion object {
    private const val TEST_NAME = "1920837192.backup"

    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  private val documentFile = mock(DocumentFile::class.java)

  @Test
  fun `Given a non-existent uri, when I getBackupInfoFromSingleDocumentFile, then I expect NOT_FOUND`() {
    try {
      BackupUtil.getBackupInfoFromSingleDocumentFile(documentFile)
      fail("Expected a BackupFileException")
    } catch (e: BackupUtil.BackupFileException) {
      assertEquals(BackupUtil.BackupFileState.NOT_FOUND, e.state)
    }
  }

  @Test
  fun `Given an existent but unreadable uri, when I getBackupInfoFromSingleDocumentFile, then I expect NOT_READABLE`() {
    givenFileExists()

    try {
      BackupUtil.getBackupInfoFromSingleDocumentFile(documentFile)
      fail("Expected a BackupFileException")
    } catch (e: BackupUtil.BackupFileException) {
      assertEquals(BackupUtil.BackupFileState.NOT_READABLE, e.state)
    }
  }

  @Test
  fun `Given an existent readable uri with a bad extension, when I getBackupInfoFromSingleDocumentFile, then I expect UNSUPPORTED_FILE_EXTENSION`() {
    givenFileExists()
    givenFileIsReadable()

    try {
      BackupUtil.getBackupInfoFromSingleDocumentFile(documentFile)
      fail("Expected a BackupFileException")
    } catch (e: BackupUtil.BackupFileException) {
      assertEquals(BackupUtil.BackupFileState.UNSUPPORTED_FILE_EXTENSION, e.state)
    }
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
    doReturn(true).`when`(documentFile).exists()
  }

  private fun givenFileIsReadable() {
    doReturn(true).`when`(documentFile).canRead()
  }

  private fun givenFileHasCorrectExtension() {
    doReturn(TEST_NAME).`when`(documentFile).name
  }
}
