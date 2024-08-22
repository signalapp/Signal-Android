/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * A lifecycle-aware observer that will let the changes to the [TextSecurePreferences] be observed.
 *
 * @param keysToListeners a map of [TextSecurePreferences] string keys to listeners that should be invoked when the values change.
 */
class SharedPreferencesLifecycleObserver(private val context: Context, keysToListeners: Map<String, () -> Unit>) : DefaultLifecycleObserver {

  private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    keysToListeners[key]?.invoke()
  }

  override fun onResume(owner: LifecycleOwner) {
    TextSecurePreferences.registerListener(context, listener)
  }

  override fun onPause(owner: LifecycleOwner) {
    TextSecurePreferences.unregisterListener(context, listener)
  }
}
