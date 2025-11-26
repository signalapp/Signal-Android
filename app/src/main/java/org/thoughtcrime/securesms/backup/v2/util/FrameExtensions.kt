/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.util

import okio.ByteString
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.backup.v2.proto.Chat
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.FilePointer
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.whispersystems.signalservice.api.backup.MediaName

fun Frame.getAllReferencedArchiveAttachmentInfos(): Set<ArchiveAttachmentInfo> {
  val infos: MutableSet<ArchiveAttachmentInfo> = mutableSetOf()
  when {
    this.account != null -> infos += this.account.getAllReferencedArchiveAttachmentInfos()
    this.chat != null -> infos += this.chat.getAllReferencedArchiveAttachmentInfos()
    this.chatItem != null -> infos += this.chatItem.getAllReferencedArchiveAttachmentInfos()
  }
  return infos.toSet()
}

private fun AccountData.getAllReferencedArchiveAttachmentInfos(): Set<ArchiveAttachmentInfo> {
  val info = this.accountSettings?.defaultChatStyle?.wallpaperPhoto?.toArchiveAttachmentInfo()

  return if (info != null) {
    setOf(info)
  } else {
    emptySet()
  }
}

private fun Chat.getAllReferencedArchiveAttachmentInfos(): Set<ArchiveAttachmentInfo> {
  val info = this.style?.wallpaperPhoto?.toArchiveAttachmentInfo(isWallpaper = true)

  return if (info != null) {
    setOf(info)
  } else {
    emptySet()
  }
}

private fun ChatItem.getAllReferencedArchiveAttachmentInfos(): Set<ArchiveAttachmentInfo> {
  var out: MutableSet<ArchiveAttachmentInfo>? = null

  // The user could have many chat items, and most will not have attachments. To avoid allocating unnecessary sets, we do this little trick.
  // (Note: emptySet() returns a constant under the hood, so that's fine)
  fun appendToOutput(item: ArchiveAttachmentInfo) {
    if (out == null) {
      out = mutableSetOf()
    }

    out.add(item)
  }

  this.contactMessage?.contact?.avatar?.toArchiveAttachmentInfo()?.let { appendToOutput(it) }
  this.directStoryReplyMessage?.textReply?.longText?.toArchiveAttachmentInfo()?.let { appendToOutput(it) }
  this.standardMessage?.attachments?.mapNotNull { it.pointer?.toArchiveAttachmentInfo() }?.forEach { appendToOutput(it) }
  this.standardMessage?.quote?.attachments?.mapNotNull { it.thumbnail?.pointer?.toArchiveAttachmentInfo(forQuote = true) }?.forEach { appendToOutput(it) }
  this.standardMessage?.linkPreview?.mapNotNull { it.image?.toArchiveAttachmentInfo() }?.forEach { appendToOutput(it) }
  this.standardMessage?.longText?.toArchiveAttachmentInfo()?.let { appendToOutput(it) }
  this.stickerMessage?.sticker?.data_?.toArchiveAttachmentInfo()?.let { appendToOutput(it) }
  this.viewOnceMessage?.attachment?.pointer?.toArchiveAttachmentInfo()?.let { appendToOutput(it) }

  this.revisions.forEach { revision ->
    revision.getAllReferencedArchiveAttachmentInfos().forEach { appendToOutput(it) }
  }

  return out ?: emptySet()
}

private fun FilePointer.toArchiveAttachmentInfo(forQuote: Boolean = false, isWallpaper: Boolean = false): ArchiveAttachmentInfo? {
  if (this.locatorInfo?.key == null) {
    return null
  }

  if (this.locatorInfo.plaintextHash == null) {
    return null
  }

  return ArchiveAttachmentInfo(
    plaintextHash = this.locatorInfo.plaintextHash,
    remoteKey = this.locatorInfo.key,
    cdn = this.locatorInfo.mediaTierCdnNumber ?: Cdn.CDN_0.cdnNumber,
    contentType = this.contentType,
    forQuote = forQuote,
    isWallpaper = isWallpaper
  )
}

data class ArchiveAttachmentInfo(
  val plaintextHash: ByteString,
  val remoteKey: ByteString,
  val cdn: Int,
  val contentType: String?,
  val forQuote: Boolean,
  val isWallpaper: Boolean = false
) {
  val fullSizeMediaName: MediaName get() = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash.toByteArray(), remoteKey.toByteArray())
  val thumbnailMediaName: MediaName get() = MediaName.fromPlaintextHashAndRemoteKeyForThumbnail(plaintextHash.toByteArray(), remoteKey.toByteArray())
}
