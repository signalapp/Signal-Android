package org.thoughtcrime.securesms.webrtc.data

import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.webrtc.data.State.Companion.CAN_DECLINE_STATES
import org.thoughtcrime.securesms.webrtc.data.State.Companion.CAN_HANGUP_STATES

sealed class State {
    object Idle : State()
    object RemotePreOffer : State()
    object RemoteRing : State()
    object LocalPreOffer : State()
    object LocalRing : State()
    object Connecting : State()
    object Connected : State()
    object Reconnecting : State()
    object PendingReconnect : State()
    object Disconnected : State()
    companion object {

        val ALL_STATES = arrayOf(
            Idle, RemotePreOffer, RemoteRing, LocalPreOffer, LocalRing,
            Connecting, Connected, Reconnecting, Disconnected
        )

        val CAN_DECLINE_STATES = arrayOf(RemotePreOffer, RemoteRing)
        val PENDING_CONNECTION_STATES = arrayOf(
            LocalPreOffer,
            LocalRing,
            RemotePreOffer,
            RemoteRing,
            Connecting,
        )
        val OUTGOING_STATES = arrayOf(
            LocalPreOffer,
            LocalRing,
        )
        val CAN_HANGUP_STATES =
            arrayOf(
                RemotePreOffer,
                RemoteRing,
                LocalPreOffer,
                LocalRing,
                Connecting,
                Connected,
                Reconnecting
            )
        val CAN_RECEIVE_ICE_STATES =
            arrayOf(RemoteRing, LocalRing, Connecting, Connected, Reconnecting)
    }

    fun withState(vararg expectedState: State, body: () -> Unit) {
        if (this in expectedState) {
            body()
        }
    }

}

sealed class Event(vararg val expectedStates: State, val outputState: State) {
    object ReceivePreOffer :
        Event(State.Idle, outputState = State.RemotePreOffer)

    object ReceiveOffer :
        Event(State.RemotePreOffer, State.Reconnecting, outputState = State.RemoteRing)

    object SendPreOffer : Event(State.Idle, outputState = State.LocalPreOffer)
    object SendOffer : Event(State.LocalPreOffer, outputState = State.LocalRing)
    object SendAnswer : Event(State.RemoteRing, outputState = State.Connecting)
    object ReceiveAnswer :
        Event(State.LocalRing, State.Reconnecting, outputState = State.Connecting)

    object Connect : Event(State.Connecting, State.Reconnecting, outputState = State.Connected)
    object IceFailed : Event(State.Connecting, outputState = State.Disconnected)
    object IceDisconnect : Event(State.Connected, outputState = State.PendingReconnect)
    object NetworkReconnect : Event(State.PendingReconnect, outputState = State.Reconnecting)
    object PrepareForNewOffer : Event(State.PendingReconnect, outputState = State.Reconnecting)
    object TimeOut :
        Event(
            State.Connecting,
            State.LocalRing,
            State.RemoteRing,
            State.Reconnecting,
            outputState = State.Disconnected
        )

    object Error : Event(*State.ALL_STATES, outputState = State.Disconnected)
    object DeclineCall : Event(*CAN_DECLINE_STATES, outputState = State.Disconnected)
    object Hangup : Event(*CAN_HANGUP_STATES, outputState = State.Disconnected)
    object Cleanup : Event(State.Disconnected, outputState = State.Idle)
}

open class StateProcessor(initialState: State) {
    private var _currentState: State = initialState
    val currentState get() = _currentState

    open fun processEvent(event: Event, sideEffect: () -> Unit = {}): Boolean {
        if (currentState in event.expectedStates) {
            Log.i(
                "Loki-Call",
                "succeeded transitioning from ${currentState::class.simpleName} to ${event.outputState::class.simpleName} with ${event::class.simpleName}"
            )
            _currentState = event.outputState
            sideEffect()
            return true
        }
        Log.e(
            "Loki-Call",
            "error transitioning from ${currentState::class.simpleName} to ${event.outputState::class.simpleName} with ${event::class.simpleName}"
        )
        return false
    }
}