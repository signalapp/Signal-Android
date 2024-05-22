@file:JvmName("SafeNavigation")

package org.thoughtcrime.securesms.util.navigation

import android.content.res.Resources
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies

private const val TAG = "SafeNavigation"

/**
 * Check [currentDestination] has an action with [resId] before attempting to navigate.
 */
fun NavController.safeNavigate(@IdRes resId: Int) {
  if (currentDestination?.getAction(resId) != null) {
    navigate(resId)
  } else {
    Log.w(TAG, "Unable to find action ${getDisplayName(resId)} for $currentDestination")
  }
}

/**
 * Check [currentDestination] has an action with [resId] before attempting to navigate.
 */
fun NavController.safeNavigate(@IdRes resId: Int, arguments: Bundle?) {
  if (currentDestination?.getAction(resId) != null) {
    navigate(resId, arguments)
  } else {
    Log.w(TAG, "Unable to find action ${getDisplayName(resId)} for $currentDestination")
  }
}

/**
 * Check [currentDestination] has an action for [directions] before attempting to navigate.
 */
fun NavController.safeNavigate(directions: NavDirections) {
  if (currentDestination?.getAction(directions.actionId) != null) {
    navigate(directions)
  } else {
    Log.w(TAG, "Unable to find ${getDisplayName(directions.actionId)} for $currentDestination")
  }
}

/**
 * Check [currentDestination] has an action for [directions] before attempting to navigate.
 */
fun NavController.safeNavigate(directions: NavDirections, navOptions: NavOptions?) {
  if (currentDestination?.getAction(directions.actionId) != null) {
    navigate(directions, navOptions)
  } else {
    Log.w(TAG, "Unable to find ${getDisplayName(directions.actionId)} for $currentDestination")
  }
}

private fun getDisplayName(id: Int): String? {
  return if (id <= 0x00FFFFFF) {
    id.toString()
  } else {
    try {
      AppDependencies.application.resources.getResourceName(id)
    } catch (e: Resources.NotFoundException) {
      id.toString()
    }
  }
}
