package org.thoughtcrime.securesms

import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers

class CanaryApplicationContext : ApplicationContext() {
  override fun onCreate() {
    super.onCreate()

    StrictMode.setThreadPolicy(
      ThreadPolicy.Builder()
        .detectDiskReads()
        .detectDiskWrites()
        .detectNetwork()
        .penaltyLog()
        .build()
    )

    try {
      Class.forName("dalvik.system.CloseGuard")
        .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
        .invoke(null, true)
    } catch (e: ReflectiveOperationException) {
      throw RuntimeException(e)
    }

    LeakCanary.config = LeakCanary.config.copy(
      referenceMatchers = AndroidReferenceMatchers.appDefaults +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "android.service.media.MediaBrowserService\$ServiceBinder",
          fieldName = "this\$0"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "androidx.media.MediaBrowserServiceCompat\$MediaBrowserServiceImplApi26\$MediaBrowserServiceApi26",
          fieldName = "mBase"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "android.support.v4.media.MediaBrowserCompat",
          fieldName = "mImpl"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "android.support.v4.media.session.MediaControllerCompat",
          fieldName = "mToken"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "android.support.v4.media.session.MediaControllerCompat",
          fieldName = "mImpl"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackService",
          fieldName = "mApplication"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "org.thoughtcrime.securesms.service.GenericForegroundService\$LocalBinder",
          fieldName = "this\$0"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "org.thoughtcrime.securesms.contacts.ContactsSyncAdapter",
          fieldName = "mContext"
        )
    )
  }
}
