/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo

import android.app.Application
import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import org.signal.core.ui.CoreUiDependencies
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.signal.mediakeyboard.demo.data.DemoMediaKeyboardRepository
import org.thoughtcrime.securesms.mms.RegisterGlideComponents
import org.thoughtcrime.securesms.mms.SignalGlideModule

class MediaKeyboardDemoApplication : Application() {

  lateinit var repository: DemoMediaKeyboardRepository
    private set

  override fun onCreate() {
    super.onCreate()

    Log.initialize(AndroidLogger)

    SignalGlideModule.registerGlideComponents = object : RegisterGlideComponents {
      override fun registerComponents(context: Context, glide: Glide, registry: Registry) = Unit
    }

    CoreUiDependencies.init(
      this,
      object : CoreUiDependencies.Provider {
        override fun providePackageId(): String = BuildConfig.APPLICATION_ID
        override fun provideIsIncognitoKeyboardEnabled(): Boolean = false
        override fun provideIsScreenSecurityEnabled(): Boolean = false
      }
    )

    repository = DemoMediaKeyboardRepository(this)
  }
}
