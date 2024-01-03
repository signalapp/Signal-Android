/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.glide.webp.app

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import org.signal.core.util.logging.Log

@GlideModule
class SampleAppGlideModule : AppGlideModule() {
  companion object {
    private val TAG = Log.tag(SampleAppGlideModule::class.java)
  }

  override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
    Log.e(TAG, "AppModule - registerComponents")
  }
}
