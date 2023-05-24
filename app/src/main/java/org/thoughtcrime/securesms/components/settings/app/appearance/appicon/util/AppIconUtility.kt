/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance.appicon.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.signal.core.util.logging.Log

class AppIconUtility(context: Context) {
  private val applicationContext: Context = context.applicationContext
  private val pm = applicationContext.packageManager

  val currentAppIcon by lazy { readCurrentAppIconFromPackageManager() }

  fun isCurrentlySelected(preset: AppIconPreset): Boolean {
    return preset == currentAppIcon
  }

  fun currentAppIconComponentName(): ComponentName {
    return currentAppIcon.getComponentName(applicationContext)
  }

  fun setNewAppIcon(desiredAppIcon: AppIconPreset) {
    Log.d(TAG, "Setting new app icon.")
    pm.setComponentEnabledSetting(desiredAppIcon.getComponentName(applicationContext), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    Log.d(TAG, "${desiredAppIcon.name} enabled.")
    val previousAppIcon = currentAppIcon
    pm.setComponentEnabledSetting(previousAppIcon.getComponentName(applicationContext), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    Log.d(TAG, "${previousAppIcon.name} disabled.")
    enumValues<AppIconPreset>().filterNot { it == desiredAppIcon || it == previousAppIcon }.forEach {
      pm.setComponentEnabledSetting(it.getComponentName(applicationContext), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
      Log.d(TAG, "${it.name} disabled.")
    }
  }

  private fun readCurrentAppIconFromPackageManager(): AppIconPreset {
    val activeIcon = enumValues<AppIconPreset>().firstOrNull {
      val componentName = it.getComponentName(applicationContext)
      val componentEnabledSetting = pm.getComponentEnabledSetting(componentName)

      Log.d(TAG, "Found $componentName with state of $componentEnabledSetting")
      if (it == AppIconPreset.DEFAULT && componentEnabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
        return it
      }

      componentEnabledSetting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    return if (activeIcon == null) {
      setNewAppIcon(AppIconPreset.DEFAULT)
      AppIconPreset.DEFAULT
    } else {
      activeIcon
    }
  }

  companion object {
    private const val TAG = "AppIconUtility"
  }
}
