/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.emoji

import android.app.Application
import okhttp3.Response
import org.signal.core.util.crypto.AttachmentSecretStore

/**
 * Application state and policy that the emoji engine depends on but does not own.
 */
object EmojiDependencies {

  private lateinit var _application: Application
  private lateinit var _provider: Provider

  fun init(application: Application, provider: Provider) {
    if (this::_provider.isInitialized) {
      return
    }

    _application = application
    _provider = provider
  }

  @JvmStatic
  val application: Application
    get() = _application

  /** Whether the user prefers their system emoji font over Signal's emoji sheets. */
  @JvmStatic
  val preferSystemEmoji: Boolean
    get() = _provider.providePreferSystemEmoji()

  /** Internal setting that pins the engine to the emoji bundled in assets. */
  val forceBuiltInEmoji: Boolean
    get() = _provider.provideForceBuiltInEmoji()

  /** Store for the secret that downloaded emoji are encrypted with on disk. */
  val attachmentSecretStore: AttachmentSecretStore
    get() = _provider.provideAttachmentSecretStore()

  /** The CDN that emoji versions, sheets, and jumbomoji are fetched from. */
  val remote: RemoteSource
    get() = _provider.provideRemote()

  /** Whether current network conditions and settings permit downloading jumbomoji. */
  @JvmStatic
  fun canAutoDownloadJumboEmoji(): Boolean = _provider.provideCanAutoDownloadJumboEmoji()

  /** Names of the jumbomoji sheets already downloaded for [version]. */
  fun getDownloadedJumboSheets(version: Int): Set<String> = _provider.provideDownloadedJumboSheets(version)

  /** Records that [sheet] finished downloading for [version]. */
  fun onJumboSheetDownloaded(version: Int, sheet: String) = _provider.onJumboSheetDownloaded(version, sheet)

  interface Provider {
    fun providePreferSystemEmoji(): Boolean
    fun provideForceBuiltInEmoji(): Boolean
    fun provideAttachmentSecretStore(): AttachmentSecretStore
    fun provideRemote(): RemoteSource
    fun provideCanAutoDownloadJumboEmoji(): Boolean
    fun provideDownloadedJumboSheets(version: Int): Set<String>
    fun onJumboSheetDownloaded(version: Int, sheet: String)
  }

  interface RemoteSource {
    val staticPath: String
    val dynamicPath: String

    fun getLong(endpoint: String): Long

    fun getObjectMd5(endpoint: String): ByteArray?

    fun getObject(endpoint: String): Response
  }
}
