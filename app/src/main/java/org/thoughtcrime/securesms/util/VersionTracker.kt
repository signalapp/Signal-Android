package org.thoughtcrime.securesms.util

import android.content.Context
import android.content.pm.PackageManager
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.RemoteConfigRefreshJob
import org.thoughtcrime.securesms.jobs.RetrieveRemoteAnnouncementsJob
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.time.Duration

object VersionTracker {
  private val TAG = Log.tag(VersionTracker::class.java)

  @JvmStatic
  fun getLastSeenVersion(): Int {
    return PlainTextKeyValueStore.lastVersionCode
  }

  @JvmStatic
  fun updateLastSeenVersion() {
    val currentVersionCode = BuildConfig.VERSION_CODE
    val lastVersionCode = PlainTextKeyValueStore.lastVersionCode

    if (currentVersionCode != lastVersionCode) {
      Log.i(TAG, "Upgraded from $lastVersionCode to $currentVersionCode. Clearing client deprecation.", true)
      SignalStore.misc.isClientDeprecated = false
      SignalStore.remoteConfig.eTag = ""
      val jobChain = listOf(RemoteConfigRefreshJob(), RefreshAttributesJob())
      AppDependencies.jobManager.startChain(jobChain).enqueue()
      RetrieveRemoteAnnouncementsJob.enqueue(true)
      LocalMetrics.getInstance().clear()
    }

    PlainTextKeyValueStore.lastVersionCode = currentVersionCode
  }

  @JvmStatic
  fun getDaysSinceFirstInstalled(context: Context): Long {
    return try {
      val installTimestamp = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
      Duration.ofMillis(System.currentTimeMillis() - installTimestamp).toDays()
    } catch (e: PackageManager.NameNotFoundException) {
      Log.w(TAG, e)
      0
    }
  }
}
