/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.exo

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import org.signal.core.util.AppForegroundObserver
import org.signal.video.exo.ExoPlayerPool
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.net.ContentProxySelector
import org.thoughtcrime.securesms.util.DeviceProperties
import kotlin.time.Duration.Companion.seconds

/**
 * ExoPlayerPool concrete instance which helps to manage a pool of ExoPlayer objects
 */
@OptIn(markerClass = [UnstableApi::class])
class SimpleExoPlayerPool(context: Context) : ExoPlayerPool<ExoPlayer>(MAXIMUM_RESERVED_PLAYERS) {
  private val context: Context = context.applicationContext
  private val okHttpClient = AppDependencies.okHttpClient.newBuilder().proxySelector(ContentProxySelector()).build()
  private val dataSourceFactory: DataSource.Factory = SignalDataSource.Factory(AppDependencies.application, okHttpClient, DataSourceTransferListener)
  private val mediaSourceFactory: MediaSource.Factory = DefaultMediaSourceFactory(dataSourceFactory)

  init {
    AppForegroundObserver.addListener(this)
  }

  /**
   * Tries to get the max number of instances that can be played back on the screen at a time, based off of
   * the device API level and decoder info.
   */
  override fun getMaxSimultaneousPlayback(): Int {
    val maxInstances = try {
      val info = MediaCodecUtil.getDecoderInfo(MimeTypes.VIDEO_H264, false, false)
      if (info != null && info.maxSupportedInstances > 0) {
        info.maxSupportedInstances
      } else {
        0
      }
    } catch (ignored: MediaCodecUtil.DecoderQueryException) {
      0
    }

    if (maxInstances > 0) {
      return maxInstances
    }

    return if (DeviceProperties.isLowMemoryDevice(AppDependencies.application)) {
      MAXIMUM_SUPPORTED_PLAYBACK_PRE_23_LOW_MEM
    } else {
      MAXIMUM_SUPPORTED_PLAYBACK_PRE_23
    }
  }

  @MainThread
  override fun createPlayer(): ExoPlayer {
    return ExoPlayer.Builder(context)
      .setMediaSourceFactory(mediaSourceFactory)
      .setSeekBackIncrementMs(SEEK_INTERVAL.inWholeMilliseconds)
      .setSeekForwardIncrementMs(SEEK_INTERVAL.inWholeMilliseconds)
      .build()
  }

  companion object {
    private const val MAXIMUM_RESERVED_PLAYERS = 1
    private const val MAXIMUM_SUPPORTED_PLAYBACK_PRE_23 = 6
    private const val MAXIMUM_SUPPORTED_PLAYBACK_PRE_23_LOW_MEM = 3
    private val SEEK_INTERVAL = 15.seconds
  }
}
