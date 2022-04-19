package org.thoughtcrime.securesms.calls

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.any
import org.mockito.Mockito.mockStatic
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.webrtc.data.Event
import org.thoughtcrime.securesms.webrtc.data.State

class CallStateMachineTests {

    private lateinit var stateProcessor: TestStateProcessor

    lateinit var mock: MockedStatic<Log>

    @Before
    fun setup() {
        stateProcessor = TestStateProcessor(State.Idle)
        mock = mockStatic(Log::class.java).apply {
            `when`<Unit> { Log.e(any(), any(), any()) }.then { invocation ->
                val msg = invocation.getArgument<Any>(1)
                println(msg)
            }
            `when`<Unit> { Log.i(any(), any(), any()) }.then { invocation ->
                val msg = invocation.getArgument<Any>(1)
                println(msg)
            }
        }
    }

    @After
    fun teardown() {
        mock.close()
    }

    @Test
    fun `should transition to full connection from remote offer`() {
        val executions = listOf(
            Event.ReceivePreOffer,
            Event.ReceiveOffer,
            Event.SendAnswer,
            Event.Connect
        )
        executions.forEach { event ->
            stateProcessor.processEvent(event)
        }

        assertEquals(stateProcessor.transitions, executions.size)
        assertEquals(stateProcessor.currentState, State.Connected)
    }

    @Test
    fun `should transition to full connection from local offer`() {
        val executions = listOf(
            Event.ReceivePreOffer,
            Event.ReceiveOffer,
            Event.SendAnswer,
            Event.Connect
        )
        executions.forEach { event ->
            stateProcessor.processEvent(event)
        }

        assertEquals(stateProcessor.transitions, executions.size)
        assertEquals(stateProcessor.currentState, State.Connected)
    }

    @Test
    fun `should not transition to connected from idle`() {
        val executions = listOf(
            Event.Connect
        )
        executions.forEach { event ->
            stateProcessor.processEvent(event)
        }

        assertEquals(stateProcessor.transitions, 0)
        assertEquals(stateProcessor.currentState, State.Idle)
    }

    @Test
    fun `should not transition to connecting from local and remote offers`() {
        val executions = listOf(
            Event.SendPreOffer,
            Event.SendOffer,
            Event.ReceivePreOffer,
            Event.ReceiveOffer
        )

        val validTransitions = 2

        executions.forEach { event ->
            stateProcessor.processEvent(event)
        }

        assertEquals(stateProcessor.transitions, validTransitions)
        assertEquals(stateProcessor.currentState, State.LocalRing)
    }

    @Test
    fun `cannot answer in local ring`() {
        val executions = listOf(
            Event.SendPreOffer,
            Event.SendOffer,
            Event.SendAnswer
        )

        val validTransitions = 2

        executions.forEach { event ->
            stateProcessor.processEvent(event)
        }

        assertEquals(stateProcessor.transitions, validTransitions)
        assertEquals(stateProcessor.currentState, State.LocalRing)
    }

    @Test
    fun `test full state cycles`() {
        val executions = listOf(
            Event.ReceivePreOffer,
            Event.ReceiveOffer,
            Event.SendAnswer,
            Event.Connect,
            Event.Hangup,
            Event.Cleanup,
            Event.SendPreOffer,
            Event.SendOffer,
            Event.ReceiveAnswer,
            Event.Connect,
            Event.IceDisconnect,
            Event.NetworkReconnect,
            Event.ReceiveAnswer,
            Event.Connect,
            Event.Hangup,
            Event.Cleanup,
            Event.ReceivePreOffer,
            Event.ReceiveOffer,
            Event.SendAnswer,
            Event.Connect,
            Event.IceDisconnect,
            Event.PrepareForNewOffer,
            Event.ReceiveOffer,
            Event.SendAnswer,
            Event.Connect,
            Event.Hangup,
            Event.Cleanup,
            Event.ReceivePreOffer,
            Event.ReceiveOffer,
            Event.SendAnswer,
            Event.IceFailed,
            Event.Cleanup,
            Event.ReceivePreOffer,
            Event.DeclineCall,
            Event.Cleanup
        )

        executions.forEach { event -> stateProcessor.processEvent(event) }

        assertEquals(State.Idle, stateProcessor.currentState)
        assertEquals(executions.size, stateProcessor.transitions)
    }

}