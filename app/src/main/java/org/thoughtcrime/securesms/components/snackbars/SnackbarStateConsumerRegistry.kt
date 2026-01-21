/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.snackbars

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.util.Consumer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.main.MainSnackbarHostKey
import java.io.Closeable

/**
 * CompositionLocal providing access to the [SnackbarStateConsumerRegistry].
 */
val LocalSnackbarStateConsumerRegistry = staticCompositionLocalOf<SnackbarStateConsumerRegistry> {
  error("No SnackbarStateConsumerRegistry provided")
}

/**
 * Holder for snackbar state that allows clearing the state after consumption.
 */
@Stable
class SnackbarStateHolder(
  private val state: MutableState<SnackbarState?>
) {
  val value: SnackbarState?
    get() = state.value

  /**
   * Clears the current snackbar state. Should be called after the snackbar has been consumed/dismissed.
   */
  fun clear() {
    state.value = null
  }
}

@Composable
fun rememberSnackbarState(
  key: SnackbarHostKey
): SnackbarStateHolder {
  val state: MutableState<SnackbarState?> = remember(key) { mutableStateOf(null) }
  val holder = remember(key, state) { SnackbarStateHolder(state) }

  val registry = LocalSnackbarStateConsumerRegistry.current
  DisposableEffect(registry, key) {
    val registration = registry.register(MainSnackbarHostKey.MainChrome) {
      state.value = it
    }

    onDispose {
      registration.close()
    }
  }

  return holder
}

/**
 * Registry for managing snackbar consumers tied to lifecycle-aware components.
 *
 * Consumers are automatically enabled when their lifecycle resumes, disabled when paused,
 * and removed when destroyed.
 */
class SnackbarStateConsumerRegistry : ViewModel() {

  private val entries = mutableSetOf<Entry>()

  /**
   * Registers a snackbar consumer for the given host and returns a [Closeable] to unregister it.
   *
   * The consumer starts enabled immediately. Call [Closeable.close] to unregister.
   * This is useful for Compose components using DisposableEffect.
   *
   * If a consumer is already registered for the given host, it will be replaced.
   *
   * @param host The host key identifying this consumer's display location.
   * @param consumer The consumer that will handle snackbar display.
   * @return A [Closeable] that unregisters the consumer when closed.
   */
  fun register(host: SnackbarHostKey, consumer: Consumer<SnackbarState>): Closeable {
    entries.removeAll { it.host == host }

    val entry = Entry(
      host = host,
      consumer = consumer,
      enabled = true
    )
    entries.add(entry)

    return Closeable { entries.remove(entry) }
  }

  /**
   * Registers a snackbar consumer for the given host, bound to a lifecycle.
   *
   * The consumer will be automatically managed based on the lifecycle:
   * - Enabled when the lifecycle is in RESUMED state
   * - Disabled when paused
   * - Removed when destroyed
   *
   * If a consumer is already registered for the given host, it will be replaced.
   *
   * @param host The host key identifying this consumer's display location.
   * @param lifecycleOwner The lifecycle owner to bind the consumer to.
   * @param consumer The consumer that will handle snackbar display.
   * @throws IllegalStateException if the lifecycle is not at least CREATED.
   */
  fun register(host: SnackbarHostKey, lifecycleOwner: LifecycleOwner, consumer: Consumer<SnackbarState>) {
    val currentState = lifecycleOwner.lifecycle.currentState
    check(currentState.isAtLeast(Lifecycle.State.CREATED)) {
      "Cannot register a consumer with a lifecycle in state $currentState"
    }

    val closeable = register(host, consumer)
    val entry = entries.find { it.host == host }!!
    entry.enabled = currentState.isAtLeast(Lifecycle.State.RESUMED)

    lifecycleOwner.lifecycle.addObserver(EntryLifecycleObserver(entry, closeable, lifecycleOwner))
  }

  /**
   * Emits a snackbar state to be consumed by a registered consumer.
   *
   * The snackbar is first offered to the consumer registered for the matching [SnackbarState.hostKey].
   * If no matching consumer is enabled, the [SnackbarState.fallbackKey] is tried next (if present).
   * Finally, the snackbar is offered to the first enabled registered consumer.
   *
   * @param snackbarState The snackbar state to emit.
   */
  fun emit(snackbarState: SnackbarState) {
    val matchingEntry = entries.find { it.host == snackbarState.hostKey && it.enabled }
    if (matchingEntry != null) {
      matchingEntry.consumer.accept(snackbarState)
      return
    }

    val fallbackEntry = snackbarState.fallbackKey?.let { fallback ->
      entries.find { it.host == fallback && it.enabled }
    }
    if (fallbackEntry != null) {
      fallbackEntry.consumer.accept(snackbarState)
      return
    }

    val firstEnabled = entries.find { it.enabled }
    firstEnabled?.consumer?.accept(snackbarState)
  }

  private class EntryLifecycleObserver(
    private val entry: Entry,
    private val closeable: Closeable,
    private val lifecycleOwner: LifecycleOwner
  ) : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
      entry.enabled = true
    }

    override fun onPause(owner: LifecycleOwner) {
      entry.enabled = false
    }

    override fun onDestroy(owner: LifecycleOwner) {
      closeable.close()
      lifecycleOwner.lifecycle.removeObserver(this)
    }
  }

  private data class Entry(
    val host: SnackbarHostKey,
    val consumer: Consumer<SnackbarState>,
    var enabled: Boolean
  )
}
