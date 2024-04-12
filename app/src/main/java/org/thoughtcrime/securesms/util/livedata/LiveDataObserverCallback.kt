/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util.livedata

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * A wrapper class that can be implemented in order to create a [LiveData] [Observer] that cleans up after itself.
 *
 * Useful for one-shot observers that can be executed as a callback on an asynchronous call that updates a [LiveData] upon completion.
 */
abstract class LiveDataObserverCallback<T>(private val liveData: LiveData<T>) : Observer<T> {
  final override fun onChanged(value: T) {
    val shouldRemove = onValue(value)
    if (shouldRemove) {
      liveData.removeObserver(this)
    }
  }

  /**
   * The body of the observer that gets executed when the value is changed.
   * Recommended usage is to check some condition in the [LiveData] to determine whether the data has been handled and therefore can be removed.
   *
   * @return should remove this observer from the [LiveData]
   */
  abstract fun onValue(value: T): Boolean
}
