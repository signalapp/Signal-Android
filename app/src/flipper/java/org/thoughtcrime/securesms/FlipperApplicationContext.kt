package org.thoughtcrime.securesms

import com.facebook.soloader.SoLoader
import com.facebook.flipper.android.AndroidFlipperClient
import com.facebook.flipper.plugins.inspector.InspectorFlipperPlugin
import com.facebook.flipper.plugins.inspector.DescriptorMapping
import com.facebook.flipper.plugins.databases.DatabasesFlipperPlugin
import org.thoughtcrime.securesms.database.FlipperSqlCipherAdapter
import com.facebook.flipper.plugins.sharedpreferences.SharedPreferencesFlipperPlugin
import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers

class FlipperApplicationContext : ApplicationContext() {
  override fun onCreate() {
    super.onCreate()
    SoLoader.init(this, false)

    val client = AndroidFlipperClient.getInstance(this)
    client.addPlugin(InspectorFlipperPlugin(this, DescriptorMapping.withDefaults()))
    client.addPlugin(DatabasesFlipperPlugin(FlipperSqlCipherAdapter(this)))
    client.addPlugin(SharedPreferencesFlipperPlugin(this))
    client.start()

    LeakCanary.config = LeakCanary.config.copy(
      referenceMatchers = AndroidReferenceMatchers.appDefaults +
        AndroidReferenceMatchers.instanceFieldLeak(
          className = "android.service.media.MediaBrowserService\$ServiceBinder",
          fieldName = "this\$0",
          description = "Framework bug",
          patternApplies = { true }
        ) +
        AndroidReferenceMatchers.instanceFieldLeak(
          className = "androidx.media.MediaBrowserServiceCompat\$MediaBrowserServiceImplApi26\$MediaBrowserServiceApi26",
          fieldName = "mBase",
          description = "Framework bug",
          patternApplies = { true }
        ) +
        AndroidReferenceMatchers.instanceFieldLeak(
          className = "org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackService",
          fieldName = "mApplication",
          description = "Framework bug",
          patternApplies = { true }
        )
    )
  }
}