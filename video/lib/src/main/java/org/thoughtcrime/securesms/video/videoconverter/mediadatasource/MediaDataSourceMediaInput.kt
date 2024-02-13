/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.videoconverter.mediadatasource

import android.media.MediaDataSource
import android.media.MediaExtractor
import androidx.annotation.RequiresApi
import org.thoughtcrime.securesms.video.interfaces.MediaInput
import java.io.IOException

/**
 * [MediaInput] implementation that adds support for the system framework's media data source.
 */
@RequiresApi(23)
class MediaDataSourceMediaInput(private val mediaDataSource: MediaDataSource) : MediaInput {
  @Throws(IOException::class)
  override fun createExtractor(): MediaExtractor {
    return MediaExtractor().apply {
      setDataSource(mediaDataSource)
    }
  }

  @Throws(IOException::class)
  override fun close() {
    mediaDataSource.close()
  }
}
