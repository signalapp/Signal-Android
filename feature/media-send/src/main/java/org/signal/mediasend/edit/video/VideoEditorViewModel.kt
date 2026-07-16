/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit.video

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Activity-scoped, stateless event bus that decouples [VideoEditorFragment] from whichever host
 * (the v2 review fragment or the v3 Compose editor) is driving it. It replaces the old
 * `VideoEditorFragment.Controller` interface with two directions of [Channel]:
 *
 *  - [events]: emitted by the fragment, consumed by the host (player lifecycle + playback position).
 *  - [commands]: emitted by the host, consumed by the fragment (timeline scrub seeks).
 *
 * Channels are keyed by media [Uri] so that, when several video pages are alive at once, each
 * fragment and host only ever see signals for their own video.
 */
class VideoEditorViewModel : ViewModel() {

  private val eventChannels = mutableMapOf<Uri, Channel<Event>>()
  private val commandChannels = mutableMapOf<Uri, Channel<Command>>()

  fun events(uri: Uri): Flow<Event> = eventChannel(uri).receiveAsFlow()

  fun commands(uri: Uri): Flow<Command> = commandChannel(uri).receiveAsFlow()

  fun emitEvent(uri: Uri, event: Event) {
    eventChannel(uri).trySend(event)
  }

  fun sendCommand(uri: Uri, command: Command) {
    commandChannel(uri).trySend(command)
  }

  private fun eventChannel(uri: Uri): Channel<Event> = eventChannels.getOrPut(uri) { Channel(Channel.BUFFERED) }

  private fun commandChannel(uri: Uri): Channel<Command> = commandChannels.getOrPut(uri) { Channel(Channel.BUFFERED) }

  /** Signals from the fragment to the host. */
  sealed interface Event {
    data object PlayerReady : Event
    data object PlayerError : Event
    data class TouchEventsNeeded(val needed: Boolean) : Event
    data class ActualPositionChanged(val positionUs: Long) : Event
  }

  /** Signals from the host to the fragment. */
  sealed interface Command {
    data class PositionDrag(val positionUs: Long) : Command
    data class EndPositionDrag(val positionUs: Long) : Command
  }
}
