/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.archive.proto.AccountData
import org.signal.archive.proto.BackupInfo
import org.signal.archive.proto.Chat
import org.signal.archive.proto.ChatItem
import org.signal.archive.proto.Contact
import org.signal.archive.proto.Frame
import org.signal.archive.proto.Recipient
import org.signal.archive.proto.StandardMessage
import org.signal.archive.proto.Text
import org.signal.archive.stream.EncryptedBackupReader
import org.signal.archive.stream.EncryptedBackupWriter
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.backup.BackupId
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.Base64
import org.signal.core.util.Util
import org.signal.libsignal.messagebackup.BackupForwardSecrecyToken
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.UUID

class EncryptedBackupReaderWriterTest {

  companion object {
    private const val MINIMUM_BACKUP_BYTES = 64 * 1024
    private const val AES_BLOCK_BYTES = 16
    private const val IV_BYTES = 16
    private const val MAC_BYTES = 32
    private const val MINIMUM_ENCRYPTED_BACKUP_BYTES = MINIMUM_BACKUP_BYTES + AES_BLOCK_BYTES + IV_BYTES + MAC_BYTES

    private fun chatItem(index: Int, body: String = "message number $index, on my way"): ChatItem {
      return ChatItem(
        chatId = 1,
        authorId = 2,
        dateSent = 1000L + index,
        standardMessage = StandardMessage(text = Text(body = body))
      )
    }
  }

  @Test
  fun `can read back all of the frames we write`() {
    val key = MessageBackupKey(Util.getSecretBytes(32))
    val aci = ACI.from(UUID.randomUUID())

    val outputStream = ByteArrayOutputStream()

    val frameCount = 10_000
    EncryptedBackupWriter.createForLocalOrLinking(key, aci, outputStream, append = { outputStream.write(it) }).use { writer ->
      writer.write(BackupInfo(version = 1, backupTimeMs = 1000L))

      for (i in 0 until frameCount) {
        writer.write(Frame(account = AccountData(username = "username-$i")))
      }
    }

    val ciphertext: ByteArray = outputStream.toByteArray()
    println(ciphertext.size)

    val frames: List<Frame> = EncryptedBackupReader.createForLocalOrLinking(key, aci, ciphertext.size.toLong()) { ciphertext.inputStream() }.use { reader ->
      assertEquals(reader.backupInfo?.version, 1L)
      assertEquals(reader.backupInfo?.backupTimeMs, 1000L)
      reader.asSequence().toList()
    }

    assertEquals(frameCount, frames.size)

    for (i in 0 until frameCount) {
      assertEquals("username-$i", frames[i].account?.username)
    }
  }

  @Test
  fun `can read back a realistic mix of frames`() {
    val key = MessageBackupKey(Util.getSecretBytes(32))
    val aci = ACI.from(UUID.randomUUID())
    val recipientCount = 500
    val chatItemCount = 20_000

    val outputStream = ByteArrayOutputStream()

    EncryptedBackupWriter.createForLocalOrLinking(key, aci, outputStream, append = { outputStream.write(it) }).use { writer ->
      writer.write(BackupInfo(version = 1, backupTimeMs = 1000L))
      writer.write(Frame(account = AccountData(username = "username")))

      for (i in 0 until recipientCount) {
        writer.write(Frame(recipient = Recipient(id = i.toLong(), contact = Contact(e164 = 15550000000L + i))))
      }

      writer.write(Frame(chat = Chat(id = 1, recipientId = 0)))

      for (i in 0 until chatItemCount) {
        writer.write(Frame(chatItem = chatItem(i)))
      }
    }

    val ciphertext: ByteArray = outputStream.toByteArray()

    val frames: List<Frame> = EncryptedBackupReader.createForLocalOrLinking(key, aci, ciphertext.size.toLong()) { ciphertext.inputStream() }.use { reader ->
      assertEquals(reader.backupInfo?.version, 1L)
      reader.asSequence().toList()
    }

    assertEquals(1 + recipientCount + 1 + chatItemCount, frames.size)
    assertEquals("username", frames[0].account?.username)
    assertEquals(15550000499L, frames[recipientCount].recipient?.contact?.e164)
    assertEquals(1L, frames[recipientCount + 1].chat?.id)

    for (i in 0 until chatItemCount) {
      assertEquals(chatItem(i), frames[recipientCount + 2 + i].chatItem)
    }
  }

  @Test
  fun `padding spreads sizes for identical content`() {
    val key = MessageBackupKey(Util.getSecretBytes(32))
    val aci = ACI.from(UUID.randomUUID())
    val random = Random(0)
    val bodies = (0 until 1500).map { (1..160).map { 'a' + random.nextInt(26) }.joinToString("") }

    val sizes = (1..10).map {
      val outputStream = ByteArrayOutputStream()

      EncryptedBackupWriter.createForLocalOrLinking(key, aci, outputStream, append = { outputStream.write(it) }).use { writer ->
        writer.write(BackupInfo(version = 1, backupTimeMs = 1000L))
        bodies.forEachIndexed { i, body ->
          writer.write(Frame(chatItem = chatItem(i, body)))
        }
      }

      outputStream.size()
    }

    assertTrue("compressed content must clear the padding floor by a wide margin, or paddingSize returns the floor instead of a draw: $sizes", sizes.min() > 2 * MINIMUM_BACKUP_BYTES)
    assertTrue("identical content collapsed to too few sizes: $sizes", sizes.toSet().size > 8)
  }

  @Test
  fun `backups under the minimum size all come out the same size`() {
    val key = MessageBackupKey(Util.getSecretBytes(32))
    val aci = ACI.from(UUID.randomUUID())

    val sizes = (1..10).map { frameCount ->
      val outputStream = ByteArrayOutputStream()

      EncryptedBackupWriter.createForLocalOrLinking(key, aci, outputStream, append = { outputStream.write(it) }).use { writer ->
        writer.write(BackupInfo(version = 1, backupTimeMs = 1000L))
        for (i in 0 until frameCount) {
          writer.write(Frame(chatItem = chatItem(i)))
        }
      }

      outputStream.size()
    }

    assertEquals("small backups did not collapse to one size: $sizes", 1, sizes.toSet().size)
    assertEquals("collapsed size is not the padding floor plus the encryption envelope", MINIMUM_ENCRYPTED_BACKUP_BYTES, sizes.first())
  }

  @Test
  fun `using a different IV every time`() {
    val key = MessageBackupKey(Util.getSecretBytes(32))
    val aci = ACI.from(UUID.randomUUID())
    val count = 10

    val uniqueOutputs = (0 until count)
      .map {
        val outputStream = ByteArrayOutputStream()

        EncryptedBackupWriter.createForLocalOrLinking(key, aci, outputStream, append = { outputStream.write(it) }).use { writer ->
          writer.write(BackupInfo(version = 1, backupTimeMs = 1000L))
          writer.write(Frame(account = AccountData(username = "static-data")))
        }

        outputStream.toByteArray()
      }
      .map { Base64.encodeWithPadding(it) }
      .toSet()

    assertEquals(count, uniqueOutputs.size)
  }

  @Test
  fun `can read back all frames using BackupId directly - local`() {
    val key = MessageBackupKey(Util.getSecretBytes(32))
    val backupId = BackupId(Util.getSecretBytes(16))

    val outputStream = ByteArrayOutputStream()
    val frameCount = 10_000
    EncryptedBackupWriter.createForLocalOrLinking(key, backupId, outputStream, append = { outputStream.write(it) }).use { writer ->
      writer.write(BackupInfo(version = 1, backupTimeMs = 1000L))

      for (i in 0 until frameCount) {
        writer.write(Frame(account = AccountData(username = "username-$i")))
      }
    }

    val ciphertext: ByteArray = outputStream.toByteArray()

    val frames: List<Frame> = EncryptedBackupReader.createForLocalOrLinking(key, backupId, ciphertext.size.toLong()) { ciphertext.inputStream() }.use { reader ->
      assertEquals(reader.backupInfo?.version, 1L)
      assertEquals(reader.backupInfo?.backupTimeMs, 1000L)
      reader.asSequence().toList()
    }

    assertEquals(frameCount, frames.size)

    for (i in 0 until frameCount) {
      assertEquals("username-$i", frames[i].account?.username)
    }
  }

  @Test
  fun `can read back all of the frames we write - forward secrecy`() {
    val key = MessageBackupKey(Util.getSecretBytes(32))
    val aci = ACI.from(UUID.randomUUID())

    val outputStream = ByteArrayOutputStream()

    val forwardSecrecyToken = BackupForwardSecrecyToken(Util.getSecretBytes(32))

    val frameCount = 10_000
    EncryptedBackupWriter.createForSignalBackup(
      key = key,
      aci = aci,
      forwardSecrecyToken = forwardSecrecyToken,
      forwardSecrecyMetadata = Util.getSecretBytes(64),
      outputStream = outputStream,
      append = { outputStream.write(it) }
    ).use { writer ->
      writer.write(BackupInfo(version = 1, backupTimeMs = 1000L))

      for (i in 0 until frameCount) {
        writer.write(Frame(account = AccountData(username = "username-$i")))
      }
    }

    val ciphertext: ByteArray = outputStream.toByteArray()
    println(ciphertext.size)

    val frames: List<Frame> = EncryptedBackupReader.createForSignalBackup(key, aci, forwardSecrecyToken, ciphertext.size.toLong()) { ciphertext.inputStream() }.use { reader ->
      assertEquals(reader.backupInfo?.version, 1L)
      assertEquals(reader.backupInfo?.backupTimeMs, 1000L)
      reader.asSequence().toList()
    }

    assertEquals(frameCount, frames.size)

    for (i in 0 until frameCount) {
      assertEquals("username-$i", frames[i].account?.username)
    }
  }

  @Test
  fun `can read back all frames using BackupId directly - forward secrecy`() {
    val key = MessageBackupKey(Util.getSecretBytes(32))
    val backupId = BackupId(Util.getSecretBytes(16))
    val forwardSecrecyToken = BackupForwardSecrecyToken(Util.getSecretBytes(32))

    val outputStream = ByteArrayOutputStream()
    val frameCount = 10_000
    EncryptedBackupWriter.createForSignalBackup(
      key = key,
      backupId = backupId,
      forwardSecrecyToken = forwardSecrecyToken,
      forwardSecrecyMetadata = Util.getSecretBytes(64),
      outputStream = outputStream,
      append = { outputStream.write(it) }
    ).use { writer ->
      writer.write(BackupInfo(version = 1, backupTimeMs = 1000L))

      for (i in 0 until frameCount) {
        writer.write(Frame(account = AccountData(username = "username-$i")))
      }
    }

    val ciphertext: ByteArray = outputStream.toByteArray()

    val frames: List<Frame> = EncryptedBackupReader.createForSignalBackup(key, backupId, forwardSecrecyToken, ciphertext.size.toLong()) { ciphertext.inputStream() }.use { reader ->
      assertEquals(reader.backupInfo?.version, 1L)
      assertEquals(reader.backupInfo?.backupTimeMs, 1000L)
      reader.asSequence().toList()
    }

    assertEquals(frameCount, frames.size)

    for (i in 0 until frameCount) {
      assertEquals("username-$i", frames[i].account?.username)
    }
  }

  @Test
  fun `can write and read using BackupId for both - local`() {
    val key = MessageBackupKey(Util.getSecretBytes(32))
    val backupId = BackupId(Util.getSecretBytes(16))

    val outputStream = ByteArrayOutputStream()

    val frameCount = 10_000
    EncryptedBackupWriter.createForLocalOrLinking(key, backupId, outputStream, append = { outputStream.write(it) }).use { writer ->
      writer.write(BackupInfo(version = 1, backupTimeMs = 1000L))

      for (i in 0 until frameCount) {
        writer.write(Frame(account = AccountData(username = "username-$i")))
      }
    }

    val ciphertext: ByteArray = outputStream.toByteArray()

    val frames: List<Frame> = EncryptedBackupReader.createForLocalOrLinking(key, backupId, ciphertext.size.toLong()) { ciphertext.inputStream() }.use { reader ->
      assertEquals(reader.backupInfo?.version, 1L)
      assertEquals(reader.backupInfo?.backupTimeMs, 1000L)
      reader.asSequence().toList()
    }

    assertEquals(frameCount, frames.size)

    for (i in 0 until frameCount) {
      assertEquals("username-$i", frames[i].account?.username)
    }
  }
}
