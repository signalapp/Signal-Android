/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.content.res.AssetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.archive.proto.Frame
import org.signal.archive.stream.EncryptedBackupReader
import org.signal.archive.stream.EncryptedBackupWriter
import org.signal.archive.stream.PlainTextBackupReader
import org.signal.core.util.logging.Log
import org.signal.core.util.readFully
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.ArchiveImportExportTests.Companion.SELF_ACI
import org.thoughtcrime.securesms.backup.v2.ArchiveImportExportTests.Companion.SELF_E164
import org.thoughtcrime.securesms.backup.v2.ArchiveImportExportTests.Companion.SELF_PNI
import org.thoughtcrime.securesms.backup.v2.ArchiveImportExportTests.Companion.SELF_PROFILE_KEY
import org.thoughtcrime.securesms.backup.v2.ArchiveImportExportTests.Companion.TESTS_FOLDER
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Exercises the real gzip path that [org.signal.archive.stream.PaddedGzipOutputStream] drives: full flushes at the
 * frame boundaries, the periodic schedule through the chat items, and the zero padding appended past the gzip trailer.
 * [ArchiveImportExportTests] runs entirely in plaintext, so nothing there covers any of it.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedArchiveRoundTripTest {

  companion object {
    const val TAG = "EncryptedRoundTrip"
    const val MINIMUM_BACKUP_BYTES = 64 * 1024
    const val COPIES = 250
    const val PERIODIC_FLUSH_THRESHOLD = 8 * 1024
    const val LARGEST_COUNT = 3
    const val SPREAD_COUNT = 8
  }

  @Before
  fun setup() {
    AppDependencies.jobManager.shutdown()
  }

  @Test
  fun encryptedBackupsSurviveFlushingAndPaddingAndStillValidate() {
    val assets = InstrumentationRegistry.getInstrumentation().context.resources.assets
    val selected = selectBackups(assets)

    assertTrue("no backup assets found under $TESTS_FOLDER", selected.isNotEmpty())
    assertTrue(
      "largest selected backup is ${selected.maxOf { it.second }} bytes, under $PERIODIC_FLUSH_THRESHOLD, so none of them reach the periodic flush schedule",
      selected.any { it.second > PERIODIC_FLUSH_THRESHOLD }
    )

    Log.d(TAG, "Round tripping ${selected.size} of ${assets.list(TESTS_FOLDER)!!.size} backups: ${selected.map { "${it.first}=${it.second}" }}")

    for ((filename, _) in selected) {
      resetAllData()

      val plaintextInput = assets.open("$TESTS_FOLDER/$filename").readFully(true)
      val importResult = BackupRepository.importPlaintextTest(
        length = plaintextInput.size.toLong(),
        inputStreamFactory = { ByteArrayInputStream(plaintextInput) },
        selfData = BackupRepository.SelfData(SELF_ACI, SELF_PNI, SELF_E164, ProfileKey(SELF_PROFILE_KEY))
      )
      assertTrue("[$filename] import failed", importResult is ImportResult.Success)
      val backupTime = (importResult as ImportResult.Success).backupTime

      val expectedFrames = BackupRepository
        .exportInMemoryForTests(plaintext = true, currentTime = backupTime)
        .let { PlainTextBackupReader(it.inputStream(), it.size.toLong()).use { reader -> reader.asSequence().toList() } }

      val encrypted = ByteArrayOutputStream()
      BackupRepository.exportForDebugging(
        outputStream = encrypted,
        append = { encrypted.write(it) },
        plaintext = false,
        currentTime = backupTime
      )
      val encryptedBytes = encrypted.toByteArray()

      val actualFrames: List<Frame> = EncryptedBackupReader
        .createForLocalOrLinking(SignalStore.backup.messageBackupKey, SELF_ACI, encryptedBytes.size.toLong()) { encryptedBytes.inputStream() }
        .use { it.asSequence().toList() }

      assertEquals("[$filename] frame count changed across the encrypted round trip", expectedFrames.size, actualFrames.size)
      assertEquals("[$filename] frames changed across the encrypted round trip", expectedFrames, actualFrames)

      val backupFile = File.createTempFile("encrypted-round-trip", ".backup").apply { writeBytes(encryptedBytes) }
      try {
        val validation = ArchiveValidator.validateLocalOrLinking(backupFile, SignalStore.backup.messageBackupKey, forTransfer = false)
        assertEquals("[$filename] libsignal rejected the padded backup", ArchiveValidator.ValidationResult.Success, validation)
      } finally {
        backupFile.delete()
      }
    }
  }

  /**
   * Every asset backup lands under [MINIMUM_BACKUP_BYTES], where the padding is the floor rather than a draw. Inflating
   * one past that is the only way to see libsignal hand back real Gaussian padding, and to check the validator accepts
   * a backup carrying hundreds of KiB of it.
   */
  @Test
  fun aBackupPastTheMinimumSizeGetsRandomPaddingAndStillValidates() {
    val assets = InstrumentationRegistry.getInstrumentation().context.resources.assets

    resetAllData()

    val source = assetSizes(assets).maxBy { it.second }.first
    Log.d(TAG, "Inflating $source")

    val plaintextInput = assets.open("$TESTS_FOLDER/$source").readFully(true)
    val importResult = BackupRepository.importPlaintextTest(
      length = plaintextInput.size.toLong(),
      inputStreamFactory = { ByteArrayInputStream(plaintextInput) },
      selfData = BackupRepository.SelfData(SELF_ACI, SELF_PNI, SELF_E164, ProfileKey(SELF_PROFILE_KEY))
    )
    assertTrue("import failed", importResult is ImportResult.Success)

    val plaintextExport = BackupRepository.exportInMemoryForTests(plaintext = true, currentTime = (importResult as ImportResult.Success).backupTime)
    val reader = PlainTextBackupReader(plaintextExport.inputStream(), plaintextExport.size.toLong())
    val header = reader.backupInfo!!
    val frames = reader.use { it.asSequence().toList() }
    val chatItems = frames.mapNotNull { it.chatItem }.filter { it.revisions.isEmpty() }
    assertTrue("source backup has no duplicable chat items", chatItems.isNotEmpty())

    val sizes = mutableListOf<Int>()
    var lastBackup = ByteArray(0)

    repeat(2) {
      val out = ByteArrayOutputStream()
      EncryptedBackupWriter.createForLocalOrLinking(SignalStore.backup.messageBackupKey, SELF_ACI, out, append = { out.write(it) }).use { writer ->
        writer.write(header)
        frames.filter { it.chatItem == null }.forEach { writer.write(it) }

        for (copy in 1..COPIES) {
          chatItems.forEach { writer.write(Frame(chatItem = it.copy(dateSent = it.dateSent + copy * 1_000L))) }
        }
      }
      lastBackup = out.toByteArray()
      sizes += lastBackup.size
    }

    Log.d(TAG, "Inflated backup sizes: $sizes")
    assertTrue(
      "compressed backup must clear the padding floor by a wide margin, or paddingSize returns the floor instead of a draw: $sizes",
      sizes.min() > 2 * MINIMUM_BACKUP_BYTES
    )
    assertNotEquals("identical content produced identical sizes, so the padding was not randomized", sizes[0], sizes[1])

    val readBack = EncryptedBackupReader
      .createForLocalOrLinking(SignalStore.backup.messageBackupKey, SELF_ACI, lastBackup.size.toLong()) { lastBackup.inputStream() }
      .use { it.asSequence().toList() }
    assertEquals("inflated backup did not round trip", frames.count { it.chatItem == null } + chatItems.size * COPIES, readBack.size)

    val backupFile = File.createTempFile("large-padding", ".backup").apply { writeBytes(lastBackup) }
    try {
      val validation = ArchiveValidator.validateLocalOrLinking(backupFile, SignalStore.backup.messageBackupKey, forTransfer = false)
      assertEquals("libsignal rejected a backup with large randomized padding", ArchiveValidator.ValidationResult.Success, validation)
    } finally {
      backupFile.delete()
    }
  }

  /**
   * Picks backups by shape rather than by name, so regenerating the assets cannot silently drop coverage or force this
   * list to be rewritten. The largest few are the only ones whose chat item region reaches the periodic flush
   * schedule; the even spread covers the floor-padding path across the range of frame mixes.
   */
  private fun selectBackups(assets: AssetManager): List<Pair<String, Int>> {
    val sized = assetSizes(assets)
    val largest = sized.sortedByDescending { it.second }.take(LARGEST_COUNT)
    val stride = maxOf(1, sized.size / SPREAD_COUNT)
    val spread = sized.filterIndexed { index, _ -> index % stride == 0 }

    return (largest + spread).distinctBy { it.first }
  }

  private fun assetSizes(assets: AssetManager): List<Pair<String, Int>> {
    return assets.list(TESTS_FOLDER)!!.sorted().map { it to assets.open("$TESTS_FOLDER/$it").use { stream -> stream.available() } }
  }

  private fun resetAllData() {
    KeyValueDatabase.getInstance(AppDependencies.application).clear()
    SignalStore.resetCache()

    SignalStore.account.resetAccountEntropyPool()
    SignalStore.account.setE164(SELF_E164)
    SignalStore.account.setAci(SELF_ACI)
    SignalStore.account.setPni(SELF_PNI)
    SignalStore.account.generateAciIdentityKeyIfNecessary()
    SignalStore.account.generatePniIdentityKeyIfNecessary()
    SignalStore.backup.backupTier = MessageBackupTier.PAID
  }
}
