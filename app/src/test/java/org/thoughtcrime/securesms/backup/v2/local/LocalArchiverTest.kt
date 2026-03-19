/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.local

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import okio.ByteString.Companion.toByteString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.backup.BackupId
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.Util
import org.thoughtcrime.securesms.backup.v2.local.proto.Metadata
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.backup.v2.proto.BackupInfo
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupWriter
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LocalArchiverTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `canDecryptMainArchive returns true for valid key`() {
    val messageBackupKey = MessageBackupKey(Util.getSecretBytes(32))
    val snapshot = createSnapshot()

    writeSnapshotFiles(snapshot, messageBackupKey)

    assertThat(LocalArchiver.canDecryptMainArchive(snapshot, messageBackupKey)).isTrue()
  }

  @Test
  fun `canDecryptMainArchive returns false for wrong key`() {
    val validKey = MessageBackupKey(Util.getSecretBytes(32))
    val wrongKey = MessageBackupKey(Util.getSecretBytes(32))
    val snapshot = createSnapshot()

    writeSnapshotFiles(snapshot, validKey)

    assertThat(LocalArchiver.canDecryptMainArchive(snapshot, wrongKey)).isFalse()
  }

  @Test
  fun `canDecryptMainArchive returns false when metadata is missing`() {
    val messageBackupKey = MessageBackupKey(Util.getSecretBytes(32))
    val snapshot = createSnapshot()

    writeMainArchive(snapshot, messageBackupKey, BackupId(Util.getSecretBytes(16)))

    assertThat(LocalArchiver.canDecryptMainArchive(snapshot, messageBackupKey)).isFalse()
  }

  @Test
  fun `canDecryptMainArchive returns false when main archive is corrupted`() {
    val messageBackupKey = MessageBackupKey(Util.getSecretBytes(32))
    val snapshot = createSnapshot()

    writeSnapshotFiles(snapshot, messageBackupKey, corruptMainArchive = true)

    assertThat(LocalArchiver.canDecryptMainArchive(snapshot, messageBackupKey)).isFalse()
  }

  @Test
  fun `getBackupId returns correct id for valid key`() {
    val messageBackupKey = MessageBackupKey(Util.getSecretBytes(32))
    val snapshot = createSnapshot()
    val backupId = BackupId(Util.getSecretBytes(16))

    snapshot.metadataOutputStream()!!.use { it.write(createMetadata(messageBackupKey, backupId).encode()) }

    val result = LocalArchiver.getBackupId(snapshot, messageBackupKey)
    assertThat(result?.value?.contentEquals(backupId.value) == true).isTrue()
  }

  @Test
  fun `getBackupId returns null when metadata is missing`() {
    val messageBackupKey = MessageBackupKey(Util.getSecretBytes(32))
    val snapshot = createSnapshot()

    assertThat(LocalArchiver.getBackupId(snapshot, messageBackupKey) == null).isTrue()
  }

  @Test
  fun `getBackupId returns wrong id for wrong key`() {
    val validKey = MessageBackupKey(Util.getSecretBytes(32))
    val wrongKey = MessageBackupKey(Util.getSecretBytes(32))
    val snapshot = createSnapshot()
    val backupId = BackupId(Util.getSecretBytes(16))

    snapshot.metadataOutputStream()!!.use { it.write(createMetadata(validKey, backupId).encode()) }

    val result = LocalArchiver.getBackupId(snapshot, wrongKey)
    assertThat(result?.value?.contentEquals(backupId.value) == true).isFalse()
  }

  private fun createSnapshot(): SnapshotFileSystem {
    val archiveRoot = temporaryFolder.newFolder()
    return ArchiveFileSystem.fromFile(context, archiveRoot).createSnapshot()!!
  }

  private fun writeSnapshotFiles(
    snapshot: SnapshotFileSystem,
    messageBackupKey: MessageBackupKey,
    corruptMainArchive: Boolean = false
  ) {
    val backupId = BackupId(Util.getSecretBytes(16))

    snapshot.metadataOutputStream()!!.use { it.write(createMetadata(messageBackupKey, backupId).encode()) }
    writeMainArchive(snapshot, messageBackupKey, backupId, corruptMainArchive)
  }

  private fun writeMainArchive(
    snapshot: SnapshotFileSystem,
    messageBackupKey: MessageBackupKey,
    backupId: BackupId,
    corruptMainArchive: Boolean = false
  ) {
    val output = ByteArrayOutputStream()

    EncryptedBackupWriter.createForLocalOrLinking(
      key = messageBackupKey,
      backupId = backupId,
      outputStream = output,
      append = { output.write(it) }
    ).use { writer ->
      writer.write(BackupInfo(version = 1, backupTimeMs = 1000L))
      writer.write(Frame(account = AccountData(username = "username")))
    }

    val bytes = output.toByteArray()
    if (corruptMainArchive) {
      bytes[bytes.lastIndex] = bytes.last().xor(0x01)
    }

    snapshot.mainOutputStream()!!.use { it.write(bytes) }
  }

  private fun createMetadata(messageBackupKey: MessageBackupKey, backupId: BackupId): Metadata {
    val metadataKey = messageBackupKey.deriveLocalBackupMetadataKey()
    val iv = Util.getSecretBytes(12)
    val cipherText = Cipher.getInstance("AES/CTR/NoPadding").run {
      init(Cipher.ENCRYPT_MODE, SecretKeySpec(metadataKey, "AES"), IvParameterSpec(iv))
      doFinal(backupId.value)
    }

    return Metadata(
      version = 1,
      backupId = Metadata.EncryptedBackupId(
        iv = iv.toByteString(),
        encryptedId = cipherText.toByteString()
      )
    )
  }

  private fun Byte.xor(mask: Int): Byte {
    return (toInt() xor mask).toByte()
  }
}
