/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.emoji

import android.app.Application
import org.junit.rules.ExternalResource
import org.signal.core.util.PartAuthorityUris
import org.signal.core.util.crypto.AttachmentSecretStore

/**
 * Initializes [EmojiDependencies] for tests, so that emoji rendering such as [Emojifier] works without an app module
 * behind it. Emoji render with the system font, since loading the bundled emoji sheets blocks forever under Robolectric,
 * and nothing is ever downloaded.
 */
class EmojiDependenciesRule(private val application: Application) : ExternalResource() {
  override fun before() {
    PartAuthorityUris.init("org.signal.emoji.test")
    EmojiDependencies.testInject(application, TestProvider)
  }

  private object TestProvider : EmojiDependencies.Provider {
    override fun providePreferSystemEmoji(): Boolean = true
    override fun provideForceBuiltInEmoji(): Boolean = false
    override fun provideAttachmentSecretStore(): AttachmentSecretStore = error("Not available in tests")
    override fun provideRemote(): EmojiDependencies.RemoteSource = error("Not available in tests")
    override fun provideCanAutoDownloadJumboEmoji(): Boolean = false
    override fun provideDownloadedJumboSheets(version: Int): Set<String> = emptySet()
    override fun onJumboSheetDownloaded(version: Int, sheet: String) = Unit
  }
}
