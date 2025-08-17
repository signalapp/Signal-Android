/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mms

import android.content.Context
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

/**
 * A Glide [ModelLoader] that handles conversion from [DecryptableUri] to [InputStreamFactory].
 */
class DecryptableUriStreamLoader(
  private val context: Context
) : ModelLoader<DecryptableUri, InputStreamFactory> {

  override fun handles(model: DecryptableUri): Boolean = true

  override fun buildLoadData(
    model: DecryptableUri,
    width: Int,
    height: Int,
    options: Options
  ): ModelLoader.LoadData<InputStreamFactory> {
    val sourceKey = ObjectKey(model)
    val dataFetcher = DecryptableUriStreamFetcher(context, model)
    return ModelLoader.LoadData(sourceKey, dataFetcher)
  }

  class Factory(
    private val context: Context
  ) : ModelLoaderFactory<DecryptableUri, InputStreamFactory> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<DecryptableUri, InputStreamFactory> {
      return DecryptableUriStreamLoader(context)
    }

    override fun teardown() = Unit
  }
}
