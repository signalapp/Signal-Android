package org.thoughtcrime.securesms.calls

import org.thoughtcrime.securesms.webrtc.data.Event
import org.thoughtcrime.securesms.webrtc.data.State
import org.thoughtcrime.securesms.webrtc.data.StateProcessor

class TestStateProcessor(initial: State): StateProcessor(initial) {

    private var _transitions = 0
    val transitions get() = _transitions

    override fun processEvent(event: Event, sideEffect: () -> Unit): Boolean {
        val didExecute = super.processEvent(event, sideEffect)
        if (didExecute) _transitions++

        return didExecute
    }
}