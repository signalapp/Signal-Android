/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.engine.Resource
import org.signal.apng.ApngDecoder

class ApngResource(private val decoder: ApngDecoder) : Resource<ApngDecoder> {
  override fun getResourceClass(): Class<ApngDecoder> = ApngDecoder::class.java

  override fun get(): ApngDecoder = decoder

  override fun getSize(): Int = 0

  override fun recycle() {
    decoder.close()
  }
}
