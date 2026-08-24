/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import okhttp3.Response
import org.signal.core.util.crypto.AttachmentSecretStore
import org.signal.emoji.EmojiDependencies
import org.thoughtcrime.securesms.crypto.AppAttachmentSecretStore
import org.thoughtcrime.securesms.jobmanager.impl.AutoDownloadEmojiConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.s3.S3

object EmojiDependenciesProvider : EmojiDependencies.Provider {
  override fun providePreferSystemEmoji(): Boolean = SignalStore.settings.isPreferSystemEmoji

  override fun provideForceBuiltInEmoji(): Boolean = SignalStore.internal.forceBuiltInEmoji

  override fun provideAttachmentSecretStore(): AttachmentSecretStore = AppAttachmentSecretStore

  override fun provideRemote(): EmojiDependencies.RemoteSource = S3RemoteSource

  override fun provideCanAutoDownloadJumboEmoji(): Boolean {
    return AutoDownloadEmojiConstraint.canAutoDownloadJumboEmoji(AppDependencies.application)
  }

  override fun provideDownloadedJumboSheets(version: Int): Set<String> = SignalStore.emoji.getJumboEmojiSheets(version)

  override fun onJumboSheetDownloaded(version: Int, sheet: String) = SignalStore.emoji.addJumboEmojiSheet(version, sheet)

  private object S3RemoteSource : EmojiDependencies.RemoteSource {
    override val staticPath: String = S3.STATIC_PATH
    override val dynamicPath: String = S3.DYNAMIC_PATH

    override fun getLong(endpoint: String): Long = S3.getLong(endpoint)

    override fun getObjectMd5(endpoint: String): ByteArray? = S3.getObjectMD5(endpoint)

    override fun getObject(endpoint: String): Response = S3.getObject(endpoint)
  }
}
