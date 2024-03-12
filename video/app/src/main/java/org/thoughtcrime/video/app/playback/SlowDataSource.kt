/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener

/**
 * This wraps a [DefaultDataSource] and adds [latency] to each read. This is intended to approximate a slow/shoddy network connection that drip-feeds in data.
 *
 * @property latency the amount, in milliseconds, that each read should be delayed. A good proxy for network ping.
 * @constructor
 *
 * @param context used to initialize the underlying [DefaultDataSource.Factory]
 */
@OptIn(UnstableApi::class)
class SlowDataSource(context: Context, private val latency: Long) : DataSource {
  private val internalDataSource: DataSource = DefaultDataSource.Factory(context).createDataSource()

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    Thread.sleep(latency)
    return internalDataSource.read(buffer, offset, length)
  }

  override fun addTransferListener(transferListener: TransferListener) {
    internalDataSource.addTransferListener(transferListener)
  }

  override fun open(dataSpec: DataSpec): Long {
    return internalDataSource.open(dataSpec)
  }

  override fun getUri(): Uri? {
    return internalDataSource.uri
  }

  override fun close() {
    return internalDataSource.close()
  }

  class Factory(private val context: Context, private val latency: Long) : DataSource.Factory {
    override fun createDataSource(): DataSource {
      return SlowDataSource(context, latency)
    }
  }
}
