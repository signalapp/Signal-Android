/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mms

import android.content.Context
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

/**
 * A Glide [DataFetcher] that retrieves an [InputStreamFactory] for a [DecryptableUri].
 */
class DecryptableUriStreamFetcher(
  private val context: Context,
  private val decryptableUri: DecryptableUri
) : DataFetcher<InputStreamFactory> {

  override fun getDataClass(): Class<InputStreamFactory> = InputStreamFactory::class.java
  override fun getDataSource(): DataSource = DataSource.LOCAL

  override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStreamFactory>) {
    try {
      callback.onDataReady(InputStreamFactory.build(context, decryptableUri.uri))
    } catch (e: Exception) {
      callback.onLoadFailed(e)
    }
  }

  override fun cancel() = Unit
  override fun cleanup() = Unit
}
