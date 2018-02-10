package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.net.Uri;
import android.view.View;

import org.thoughtcrime.securesms.TextSecureTestCase;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.whispersystems.libsignal.util.Pair;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class SaveAttachmentTaskTest extends TextSecureTestCase
{
  private static final long TEST_TIMESTAMP = 585001320000L;

  private TestSaveAttachmentTask saveAttachmentTask;

  @Override
  public void setUp() {
    super.setUp();
    saveAttachmentTask = createTestSaveAttachmentTask();
  }

  private TestSaveAttachmentTask createTestSaveAttachmentTask() {
    return new TestSaveAttachmentTask(getInstrumentation().getTargetContext(), null, null);
  }

  public void testDoInBackground_emptyImageAttachmentWithFileNameIsCorrectlySaved()
          throws IOException, NoExternalStorageException
  {
    final String name = "testImageThatShouldNotAlreadyExist";
    final String extension = "png";
    final String outputFileName = name + "." + extension;
    final String contentType = "image/png";
    final File outputDir = StorageUtil.getImageDir();
    final File expectedOutputFile = generateOutputFileForKnownFilename(name, extension, outputDir);

    verifyAttachmentSavedCorrectly(outputFileName, contentType, outputDir, expectedOutputFile);

    assertTrue(expectedOutputFile.delete());
  }

  public void testDoInBackground_emptyImageAttachmentWithFileNameIsCorrectlySavedWithIndex()
          throws IOException, NoExternalStorageException
  {
    final String name = "testImageThatShouldNotAlreadyExist";
    final String extension = "png";
    final String outputFileName = name + "." + extension;
    final String contentType = "image/png";
    final File outputDir = StorageUtil.getImageDir();
    final ArrayList<File> testFiles = populateWithTestFiles(name, extension, outputDir);
    final File expectedOutputFile = generateOutputFileForKnownFilename(name, extension, outputDir);
    testFiles.add(expectedOutputFile);

    verifyAttachmentSavedCorrectly(outputFileName, contentType, outputDir, expectedOutputFile);

    for (File tmpFile : testFiles) {
      assertTrue(tmpFile.delete());
    }
  }

  public void testDoInBackground_emptyImageAttachmentWithoutFileNameIsCorrectlySaved()
          throws IOException, NoExternalStorageException
  {
    final String extension = "png";
    final String contentType = "image/png";
    final File outputDir = StorageUtil.getImageDir();
    final File expectedOutputFile = generateOutputFileForUnknownFilename(extension, TEST_TIMESTAMP, outputDir);

    verifyAttachmentSavedCorrectly(null, contentType, outputDir, expectedOutputFile);

    assertTrue(expectedOutputFile.delete());
  }

  public void testDoInBackground_emptyImageAttachmentWithoutFileNameNorExtensionIsCorrectlySaved()
          throws IOException, NoExternalStorageException
  {
    final String extension = "attach";
    final String contentType = "image/";
    final File outputDir = StorageUtil.getImageDir();
    final File expectedOutputFile = generateOutputFileForUnknownFilename(extension, TEST_TIMESTAMP, outputDir);

    verifyAttachmentSavedCorrectly(null, contentType, outputDir, expectedOutputFile);

    assertTrue(expectedOutputFile.delete());
  }

  public void testDoInBackground_emptyAudioAttachmentWithFileNameIsCorrectlySaved()
          throws IOException, NoExternalStorageException
  {
    final String name = "testAudioThatShouldNotAlreadyExist";
    final String extension = "mp3";
    final String outputFileName = name + "." + extension;
    final String contentType = "audio/";
    final File outputDir = StorageUtil.getAudioDir();
    final File expectedOutputFile = generateOutputFileForKnownFilename(name, extension, outputDir);

    verifyAttachmentSavedCorrectly(outputFileName, contentType, outputDir, expectedOutputFile);

    assertTrue(expectedOutputFile.delete());
  }

  public void testDoInBackground_emptyVideoAttachmentWithFileNameIsCorrectlySaved()
          throws IOException, NoExternalStorageException
  {
    final String name = "testVideoThatShouldNotAlreadyExist";
    final String extension = "mp4";
    final String outputFileName = name + "." + extension;
    final String contentType = "video/";
    final File outputDir = StorageUtil.getVideoDir();
    final File expectedOutputFile = generateOutputFileForKnownFilename(name, extension, outputDir);

    verifyAttachmentSavedCorrectly(outputFileName, contentType, outputDir, expectedOutputFile);

    assertTrue(expectedOutputFile.delete());
  }

  public void testDoInBackground_emptyUnknownAttachmentWithFileNameIsCorrectlySaved()
          throws IOException, NoExternalStorageException
  {
    final String name = "testFileThatShouldNotAlreadyExist";
    final String extension = "rand";
    final String outputFileName = name + "." + extension;
    final String contentType = "somethingweird/";
    final File outputDir = StorageUtil.getDownloadDir();
    final File expectedOutputFile = generateOutputFileForKnownFilename(name, extension, outputDir);

    verifyAttachmentSavedCorrectly(outputFileName, contentType, outputDir, expectedOutputFile);

    assertTrue(expectedOutputFile.delete());
  }

  private ArrayList<File> populateWithTestFiles(String name, String extension, final File outputDir)
          throws IOException
  {
    ArrayList<File> testFiles = new ArrayList<>();

    for (int i = 0; i < 4; i++) {
      File tmpFile = generateOutputFileForKnownFilename(name, extension, outputDir);
      if (tmpFile.createNewFile()) {
        testFiles.add(tmpFile);
      }
    }

    return testFiles;
  }

  private File generateOutputFileForKnownFilename(String name,
                                                  String extension,
                                                  final File outputDir)
  {
    final String outputFileName = guessOutputFileNameIndex(name, extension, outputDir);
    final File outputFile = new File(outputDir, outputFileName);

    assertFalse(outputFile.exists());
    return outputFile;
  }

  private String guessOutputFileNameIndex(String name, String extension, final File outputDir) {
    final File outputFile = new File(outputDir, name + "." + extension);

    if (outputFile.exists()) {
      String newName;

      if (name.charAt(name.length() - 2) == '-') {
        int newIndex = Integer.parseInt("" + name.charAt(name.length() - 1)) + 1;
        newName = name.substring(0, name.length() - 1) + newIndex;
      } else {
        newName = name + "-1";
      }

      return guessOutputFileNameIndex(newName, extension, outputDir);
    } else {
      return name + "." + extension;
    }
  }

  private void verifyAttachmentSavedCorrectly(String outputFileName,
                                              String contentType,
                                              final File outputDir,
                                              final File expectedOutputFile)
          throws IOException
  {
    final File testFile = createEmptyTempFile("testFile", "ext");
    final SaveAttachmentTask.Attachment attachment
            = new SaveAttachmentTask.Attachment(Uri.fromFile(testFile),
                                                contentType,
                                                TEST_TIMESTAMP,
                                                outputFileName);

    // Pair<Integer, File> result = saveAttachmentTask.doInBackground(attachment);

    // assertTrue(result.first() == SaveAttachmentTask.SUCCESS);
    // assertEquals(result.second().getAbsolutePath(), outputDir.getAbsolutePath());
    // assertTrue(expectedOutputFile.exists());
  }

  private File createEmptyTempFile(String fileName, String extension) throws IOException
  {
    String fullName = fileName + "." + extension;
    File file = new File(getInstrumentation().getTargetContext().getCacheDir(), fullName);

    if (file.exists()) {
      file = createEmptyTempFile(fileName + "-" + System.currentTimeMillis(), extension);
    } else {
      file.createNewFile();
    }

    return file;
  }

  private File generateOutputFileForUnknownFilename(String extension,
                                                    long date,
                                                    final File outputDir)
  {
    if (extension == null) extension = "attach";

    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
    String           base          = "signal-" + dateFormatter.format(date);

    final String outputFileName = guessOutputFileNameIndex(base, extension, outputDir);
    final File outputFile = new File(outputDir, outputFileName);

    assertFalse(outputFile.exists());
    return outputFile;
  }

  private class TestSaveAttachmentTask extends SaveAttachmentTask {
    private TestSaveAttachmentTask(Context context, MasterSecret masterSecret, View view)
    {
        super(context, masterSecret, view);
    }

    @Override
    protected void onPreExecute() {}
  }
}
