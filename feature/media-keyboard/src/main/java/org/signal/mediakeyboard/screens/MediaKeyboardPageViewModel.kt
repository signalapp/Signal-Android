/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.mediakeyboard.MediaKeyboardAction

/**
 * A keyboard page's view model, reporting anything only the host can carry out as a [MediaKeyboardAction].
 *
 * Actions go out on a channel rather than through a callback the host hands over. A view model is retained across a
 * configuration change while the host that created it is not, so a stored callback would go on pointing at a host
 * whose view is gone. The host collects [actions] for as long as it is composed instead.
 */
abstract class MediaKeyboardPageViewModel<E : Any>(
  tag: String,
  shouldLogEvents: Boolean = false
) : EventDrivenViewModel<E>(tag, shouldLogEvents) {

  private val actionChannel = Channel<MediaKeyboardAction>(Channel.UNLIMITED)

  /** Actions raised for the host. Buffered, so none are lost between hosts. */
  val actions: Flow<MediaKeyboardAction> = actionChannel.receiveAsFlow()

  protected fun emitAction(action: MediaKeyboardAction) {
    // Unlimited buffer means this will always succeed
    actionChannel.trySend(action)
  }
}
