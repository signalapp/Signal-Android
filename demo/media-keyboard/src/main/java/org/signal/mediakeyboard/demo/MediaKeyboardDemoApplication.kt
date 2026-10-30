/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo

import android.app.Application
import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import org.signal.core.ui.CoreUiDependencies
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.signal.mediakeyboard.MediaKeyboardDependencies
import org.signal.mediakeyboard.demo.data.DemoMediaKeyboardRepository
import org.signal.network.api.RemoteConfigApi
import org.signal.remoteconfig.RemoteConfig
import org.signal.remoteconfig.RemoteConfigDependencies
import org.signal.video.exo.ExoPlayerPool
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

    RemoteConfig.initDependencies(
      object : RemoteConfigDependencies {
        override val isStagingBuild: Boolean = false
        override val isInternalBuild: Boolean = true
        override var currentConfig: String? = null
        override var pendingConfig: String? = null
        override var lastFetchTime: Long = 0
        override val localOverrides: Map<String, String> = emptyMap()
        override val remoteConfigApi: RemoteConfigApi
          get() = error("Not supported in the demo")

        override fun enqueueRefresh() = Unit
        override fun onBackgroundMessageProcessIntervalChanged() = Unit
        override fun onSvr3MigrationPhaseChanged(change: RemoteConfig.ConfigChange) = Unit
      }
    )

    MediaKeyboardDependencies.init(
      this,
      object : MediaKeyboardDependencies.Provider {
        private val exoPlayerPool = DemoExoPlayerPool(this@MediaKeyboardDemoApplication)

        override fun provideExoPlayerPool(): ExoPlayerPool<ExoPlayer> = exoPlayerPool
      }
    )

    repository = DemoMediaKeyboardRepository(this)
  }
}
