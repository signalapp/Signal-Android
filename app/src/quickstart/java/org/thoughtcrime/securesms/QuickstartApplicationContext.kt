/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Intent
import android.os.Bundle
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Application subclass for the quickstart build variant.
 * On first launch, if the account is not yet registered, it triggers
 * [QuickstartInitializer] to import pre-baked credentials from assets.
 */
class QuickstartApplicationContext : ApplicationContext() {

  companion object {
    private val TAG = Log.tag(QuickstartApplicationContext::class.java)
  }

  override fun onCreate() {
    super.onCreate()
    if (!SignalStore.account.isRegistered) {
      Log.i(TAG, "Account not registered, attempting quickstart initialization...")
      QuickstartInitializer.initialize(this)

      if (QuickstartInitializer.pendingBackupDir != null) {
        Log.i(TAG, "Pending backup detected, will redirect to restore activity")
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
          override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is QuickstartRestoreActivity) return
            unregisterActivityLifecycleCallbacks(this)
            activity.startActivity(Intent(activity, QuickstartRestoreActivity::class.java))
            activity.finish()
          }

          override fun onActivityStarted(activity: Activity) = Unit
          override fun onActivityResumed(activity: Activity) = Unit
          override fun onActivityPaused(activity: Activity) = Unit
          override fun onActivityStopped(activity: Activity) = Unit
          override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
          override fun onActivityDestroyed(activity: Activity) = Unit
        })
      }
    }
  }
}
