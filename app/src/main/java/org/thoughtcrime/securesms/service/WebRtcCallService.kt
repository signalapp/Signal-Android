package org.thoughtcrime.securesms.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.media.AudioManager
import android.os.IBinder
import android.os.ResultReceiver
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.os.bundleOf
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.FutureTaskListener
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.calls.WebRtcCallActivity
import org.thoughtcrime.securesms.util.CallNotificationBuilder
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.TYPE_ESTABLISHED
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.TYPE_INCOMING_CONNECTING
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.TYPE_INCOMING_PRE_OFFER
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.TYPE_INCOMING_RINGING
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.TYPE_OUTGOING_RINGING
import org.thoughtcrime.securesms.webrtc.AudioManagerCommand
import org.thoughtcrime.securesms.webrtc.CallManager
import org.thoughtcrime.securesms.webrtc.CallViewModel
import org.thoughtcrime.securesms.webrtc.HangUpRtcOnPstnCallAnsweredListener
import org.thoughtcrime.securesms.webrtc.IncomingPstnCallReceiver
import org.thoughtcrime.securesms.webrtc.NetworkChangeReceiver
import org.thoughtcrime.securesms.webrtc.PeerConnectionException
import org.thoughtcrime.securesms.webrtc.PowerButtonReceiver
import org.thoughtcrime.securesms.webrtc.ProximityLockRelease
import org.thoughtcrime.securesms.webrtc.UncaughtExceptionHandlerManager
import org.thoughtcrime.securesms.webrtc.WiredHeadsetStateReceiver
import org.thoughtcrime.securesms.webrtc.audio.OutgoingRinger
import org.thoughtcrime.securesms.webrtc.data.Event
import org.thoughtcrime.securesms.webrtc.locks.LockManager
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState.CONNECTED
import org.webrtc.PeerConnection.IceConnectionState.DISCONNECTED
import org.webrtc.PeerConnection.IceConnectionState.FAILED
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.thoughtcrime.securesms.webrtc.data.State as CallState

@AndroidEntryPoint
class WebRtcCallService: Service(), CallManager.WebRtcListener {

    companion object {

        private val TAG = Log.tag(WebRtcCallService::class.java)

        const val ACTION_INCOMING_RING = "RING_INCOMING"
        const val ACTION_OUTGOING_CALL = "CALL_OUTGOING"
        const val ACTION_ANSWER_CALL = "ANSWER_CALL"
        const val ACTION_DENY_CALL = "DENY_CALL"
        const val ACTION_LOCAL_HANGUP = "LOCAL_HANGUP"
        const val ACTION_SET_MUTE_AUDIO = "SET_MUTE_AUDIO"
        const val ACTION_SET_MUTE_VIDEO = "SET_MUTE_VIDEO"
        const val ACTION_FLIP_CAMERA = "FLIP_CAMERA"
        const val ACTION_UPDATE_AUDIO = "UPDATE_AUDIO"
        const val ACTION_WIRED_HEADSET_CHANGE = "WIRED_HEADSET_CHANGE"
        const val ACTION_SCREEN_OFF = "SCREEN_OFF"
        const val ACTION_CHECK_TIMEOUT = "CHECK_TIMEOUT"
        const val ACTION_CHECK_RECONNECT = "CHECK_RECONNECT"
        const val ACTION_CHECK_RECONNECT_TIMEOUT = "CHECK_RECONNECT_TIMEOUT"
        const val ACTION_IS_IN_CALL_QUERY = "IS_IN_CALL"
        const val ACTION_WANTS_TO_ANSWER = "WANTS_TO_ANSWER"

        const val ACTION_PRE_OFFER = "PRE_OFFER"
        const val ACTION_RESPONSE_MESSAGE = "RESPONSE_MESSAGE"
        const val ACTION_ICE_MESSAGE = "ICE_MESSAGE"
        const val ACTION_REMOTE_HANGUP = "REMOTE_HANGUP"
        const val ACTION_ICE_CONNECTED = "ICE_CONNECTED"

        const val EXTRA_RECIPIENT_ADDRESS = "RECIPIENT_ID"
        const val EXTRA_ENABLED = "ENABLED"
        const val EXTRA_AUDIO_COMMAND = "AUDIO_COMMAND"
        const val EXTRA_MUTE = "mute_value"
        const val EXTRA_AVAILABLE = "enabled_value"
        const val EXTRA_REMOTE_DESCRIPTION = "remote_description"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_ICE_SDP = "ice_sdp"
        const val EXTRA_ICE_SDP_MID = "ice_sdp_mid"
        const val EXTRA_ICE_SDP_LINE_INDEX = "ice_sdp_line_index"
        const val EXTRA_RESULT_RECEIVER = "result_receiver"
        const val EXTRA_WANTS_TO_ANSWER = "wants_to_answer"

        const val INVALID_NOTIFICATION_ID = -1
        private const val TIMEOUT_SECONDS = 30L
        private const val RECONNECT_SECONDS = 5L
        private const val MAX_RECONNECTS = 5

        fun cameraEnabled(context: Context, enabled: Boolean) = Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_SET_MUTE_VIDEO)
                .putExtra(EXTRA_MUTE, !enabled)

        fun flipCamera(context: Context) = Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_FLIP_CAMERA)

        fun acceptCallIntent(context: Context) = Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_ANSWER_CALL)

        fun microphoneIntent(context: Context, enabled: Boolean) = Intent(context, WebRtcCallService::class.java)
            .setAction(ACTION_SET_MUTE_AUDIO)
            .putExtra(EXTRA_MUTE, !enabled)

        fun createCall(context: Context, recipient: Recipient) = Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_OUTGOING_CALL)
                .putExtra(EXTRA_RECIPIENT_ADDRESS, recipient.address)

        fun incomingCall(context: Context, address: Address, sdp: String, callId: UUID, callTime: Long) =
                Intent(context, WebRtcCallService::class.java)
                        .setAction(ACTION_INCOMING_RING)
                        .putExtra(EXTRA_RECIPIENT_ADDRESS, address)
                        .putExtra(EXTRA_CALL_ID, callId)
                        .putExtra(EXTRA_REMOTE_DESCRIPTION, sdp)
                        .putExtra(EXTRA_TIMESTAMP, callTime)

        fun incomingAnswer(context: Context, address: Address, sdp: String, callId: UUID) =
                Intent(context, WebRtcCallService::class.java)
                        .setAction(ACTION_RESPONSE_MESSAGE)
                        .putExtra(EXTRA_RECIPIENT_ADDRESS, address)
                        .putExtra(EXTRA_CALL_ID, callId)
                        .putExtra(EXTRA_REMOTE_DESCRIPTION, sdp)

        fun preOffer(context: Context, address: Address, callId: UUID, callTime: Long) =
                Intent(context, WebRtcCallService::class.java)
                    .setAction(ACTION_PRE_OFFER)
                    .putExtra(EXTRA_RECIPIENT_ADDRESS, address)
                    .putExtra(EXTRA_CALL_ID, callId)
                    .putExtra(EXTRA_TIMESTAMP, callTime)

        fun iceCandidates(context: Context, address: Address, iceCandidates: List<IceCandidate>, callId: UUID) =
                Intent(context, WebRtcCallService::class.java)
                        .setAction(ACTION_ICE_MESSAGE)
                        .putExtra(EXTRA_CALL_ID, callId)
                        .putExtra(EXTRA_ICE_SDP, iceCandidates.map(IceCandidate::sdp).toTypedArray())
                        .putExtra(EXTRA_ICE_SDP_LINE_INDEX, iceCandidates.map(IceCandidate::sdpMLineIndex).toIntArray())
                        .putExtra(EXTRA_ICE_SDP_MID, iceCandidates.map(IceCandidate::sdpMid).toTypedArray())
                        .putExtra(EXTRA_RECIPIENT_ADDRESS, address)

        fun denyCallIntent(context: Context) = Intent(context, WebRtcCallService::class.java).setAction(ACTION_DENY_CALL)

        fun remoteHangupIntent(context: Context, callId: UUID) = Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_REMOTE_HANGUP)
                .putExtra(EXTRA_CALL_ID, callId)

        fun hangupIntent(context: Context) = Intent(context, WebRtcCallService::class.java).setAction(ACTION_LOCAL_HANGUP)

        fun sendAudioManagerCommand(context: Context, command: AudioManagerCommand) {
            val intent = Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_UPDATE_AUDIO)
                .putExtra(EXTRA_AUDIO_COMMAND, command)
            context.startService(intent)
        }

        fun broadcastWantsToAnswer(context: Context, wantsToAnswer: Boolean) {
            val intent = Intent(ACTION_WANTS_TO_ANSWER)
                    .putExtra(EXTRA_WANTS_TO_ANSWER, wantsToAnswer)

            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }

        @JvmStatic
        fun isCallActive(context: Context, resultReceiver: ResultReceiver) {
            val intent = Intent(context, WebRtcCallService::class.java)
                    .setAction(ACTION_IS_IN_CALL_QUERY)
                    .putExtra(EXTRA_RESULT_RECEIVER, resultReceiver)
            context.startService(intent)
        }
    }

    @Inject lateinit var callManager: CallManager

    private var wantsToAnswer = false
    private var currentTimeouts = 0
    private var isNetworkAvailable = true
    private var scheduledTimeout: ScheduledFuture<*>? = null
    private var scheduledReconnect: ScheduledFuture<*>? = null

    private val lockManager by lazy { LockManager(this) }
    private val serviceExecutor = Executors.newSingleThreadExecutor()
    private val timeoutExecutor = Executors.newScheduledThreadPool(1)
    private val hangupOnCallAnswered = HangUpRtcOnPstnCallAnsweredListener {
        startService(hangupIntent(this))
    }

    private var networkChangedReceiver: NetworkChangeReceiver? = null
    private var callReceiver: IncomingPstnCallReceiver? = null
    private var wantsToAnswerReceiver: BroadcastReceiver? = null
    private var wiredHeadsetStateReceiver: WiredHeadsetStateReceiver? = null
    private var uncaughtExceptionHandlerManager: UncaughtExceptionHandlerManager? = null
    private var powerButtonReceiver: PowerButtonReceiver? = null

    @Synchronized
    private fun terminate() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(WebRtcCallActivity.ACTION_END))
        lockManager.updatePhoneState(LockManager.PhoneState.IDLE)
        callManager.stop()
        wantsToAnswer = false
        currentTimeouts = 0
        isNetworkAvailable = true
        scheduledTimeout?.cancel(false)
        scheduledReconnect?.cancel(false)
        scheduledTimeout = null
        scheduledReconnect = null
        stopForeground(true)
    }

    private fun isSameCall(intent: Intent): Boolean {
        val expectedCallId = getCallId(intent)
        return callManager.callId == expectedCallId
    }


    private fun isPreOffer() = callManager.isPreOffer()

    private fun isBusy(intent: Intent) = callManager.isBusy(this, getCallId(intent))

    private fun isIdle() = callManager.isIdle()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onHangup() {
        serviceExecutor.execute {
            callManager.handleRemoteHangup()

            if (callManager.currentConnectionState in CallState.CAN_DECLINE_STATES) {
                callManager.recipient?.let { recipient ->
                    insertMissedCall(recipient, true)
                }
            }
            terminate()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) return START_NOT_STICKY
        serviceExecutor.execute {
            val action = intent.action
            Log.i("Loki", "Handling ${intent.action}")
            when {
                action == ACTION_INCOMING_RING && isSameCall(intent) && callManager.currentConnectionState == CallState.Reconnecting -> handleNewOffer(intent)
                action == ACTION_PRE_OFFER && isIdle() -> handlePreOffer(intent)
                action == ACTION_INCOMING_RING && isBusy(intent) -> handleBusyCall(intent)
                action == ACTION_INCOMING_RING && isPreOffer() -> handleIncomingRing(intent)
                action == ACTION_OUTGOING_CALL && isIdle() -> handleOutgoingCall(intent)
                action == ACTION_ANSWER_CALL -> handleAnswerCall(intent)
                action == ACTION_DENY_CALL -> handleDenyCall(intent)
                action == ACTION_LOCAL_HANGUP -> handleLocalHangup(intent)
                action == ACTION_REMOTE_HANGUP -> handleRemoteHangup(intent)
                action == ACTION_SET_MUTE_AUDIO -> handleSetMuteAudio(intent)
                action == ACTION_SET_MUTE_VIDEO -> handleSetMuteVideo(intent)
                action == ACTION_FLIP_CAMERA -> handleSetCameraFlip(intent)
                action == ACTION_WIRED_HEADSET_CHANGE -> handleWiredHeadsetChanged(intent)
                action == ACTION_SCREEN_OFF -> handleScreenOffChange(intent)
                action == ACTION_RESPONSE_MESSAGE && isSameCall(intent) && callManager.currentConnectionState == CallState.Reconnecting -> handleResponseMessage(intent)
                action == ACTION_RESPONSE_MESSAGE -> handleResponseMessage(intent)
                action == ACTION_ICE_MESSAGE -> handleRemoteIceCandidate(intent)
                action == ACTION_ICE_CONNECTED -> handleIceConnected(intent)
                action == ACTION_CHECK_TIMEOUT -> handleCheckTimeout(intent)
                action == ACTION_CHECK_RECONNECT -> handleCheckReconnect(intent)
                action == ACTION_IS_IN_CALL_QUERY -> handleIsInCallQuery(intent)
                action == ACTION_UPDATE_AUDIO -> handleUpdateAudio(intent)
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        callManager.registerListener(this)
        wantsToAnswer = false
        isNetworkAvailable = true
        registerIncomingPstnCallReceiver()
        registerWiredHeadsetStateReceiver()
        registerWantsToAnswerReceiver()
        getSystemService(TelephonyManager::class.java)
                .listen(hangupOnCallAnswered, PhoneStateListener.LISTEN_CALL_STATE)
        registerUncaughtExceptionHandler()
        networkChangedReceiver = NetworkChangeReceiver(::networkChange)
        networkChangedReceiver!!.register(this)
    }

    private fun registerUncaughtExceptionHandler() {
        uncaughtExceptionHandlerManager = UncaughtExceptionHandlerManager().apply {
            registerHandler(ProximityLockRelease(lockManager))
        }
    }

    private fun registerIncomingPstnCallReceiver() {
        callReceiver = IncomingPstnCallReceiver()
        registerReceiver(callReceiver, IntentFilter("android.intent.action.PHONE_STATE"))
    }

    private fun registerWantsToAnswerReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                wantsToAnswer = intent?.getBooleanExtra(EXTRA_WANTS_TO_ANSWER, false) ?: false
            }
        }
        wantsToAnswerReceiver = receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(ACTION_WANTS_TO_ANSWER))
    }

    private fun registerWiredHeadsetStateReceiver() {
        wiredHeadsetStateReceiver = WiredHeadsetStateReceiver()
        registerReceiver(wiredHeadsetStateReceiver, IntentFilter(AudioManager.ACTION_HEADSET_PLUG))
    }

    private fun handleBusyCall(intent: Intent) {
        val recipient = getRemoteRecipient(intent)
        val callState = callManager.currentConnectionState

        insertMissedCall(recipient, false)

        if (callState == CallState.Idle) {
            stopForeground(true)
        }
    }

    private fun handleUpdateAudio(intent: Intent) {
        val audioCommand = intent.getParcelableExtra<AudioManagerCommand>(EXTRA_AUDIO_COMMAND)!!
        if (callManager.currentConnectionState !in arrayOf(CallState.Connected, *CallState.PENDING_CONNECTION_STATES)) {
            Log.w(TAG, "handling audio command not in call")
            return
        }
        callManager.handleAudioCommand(audioCommand)
    }

    private fun handleNewOffer(intent: Intent) {
        val offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION) ?: return
        val callId = getCallId(intent)
        val recipient = getRemoteRecipient(intent)
        callManager.onNewOffer(offer, callId, recipient).fail {
            Log.e("Loki", "Error handling new offer", it)
            callManager.postConnectionError()
            terminate()
        }
    }

    private fun handlePreOffer(intent: Intent) {
        if (!callManager.isIdle()) {
            Log.w(TAG, "Handling pre-offer from non-idle state")
            return
        }
        val callId = getCallId(intent)
        val recipient = getRemoteRecipient(intent)

        if (isIncomingMessageExpired(intent)) {
            insertMissedCall(recipient, true)
            terminate()
            return
        }

        callManager.onPreOffer(callId, recipient) {
            setCallInProgressNotification(TYPE_INCOMING_PRE_OFFER, recipient)
            callManager.postViewModelState(CallViewModel.State.CALL_PRE_INIT)
            callManager.initializeAudioForCall()
            callManager.startIncomingRinger()
            callManager.setAudioEnabled(true)
        }
    }

    private fun handleIncomingRing(intent: Intent) {
        val callId = getCallId(intent)
        val recipient = getRemoteRecipient(intent)
        val preOffer = callManager.preOfferCallData

        if (callManager.isPreOffer() && (preOffer == null || preOffer.callId != callId || preOffer.recipient != recipient)) {
            Log.d(TAG, "Incoming ring from non-matching pre-offer")
            return
        }

        val offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION) ?: return
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, -1)

        callManager.onIncomingRing(offer, callId, recipient, timestamp) {
            if (wantsToAnswer) {
                setCallInProgressNotification(TYPE_INCOMING_CONNECTING, recipient)
            } else {
                setCallInProgressNotification(TYPE_INCOMING_RINGING, recipient)
            }
            callManager.clearPendingIceUpdates()
            callManager.postViewModelState(CallViewModel.State.CALL_RINGING)
            registerPowerButtonReceiver()
        }
    }

    private fun handleOutgoingCall(intent: Intent) {
        callManager.postConnectionEvent(Event.SendPreOffer) {
            val recipient = getRemoteRecipient(intent)
            callManager.recipient = recipient
            val callId = UUID.randomUUID()
            callManager.callId = callId

            callManager.initializeVideo(this)

            callManager.postViewModelState(CallViewModel.State.CALL_OUTGOING)
            lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL)
            callManager.initializeAudioForCall()
            callManager.startOutgoingRinger(OutgoingRinger.Type.RINGING)
            setCallInProgressNotification(TYPE_OUTGOING_RINGING, callManager.recipient)
            callManager.insertCallMessage(recipient.address.serialize(), CallMessageType.CALL_OUTGOING)
            scheduledTimeout = timeoutExecutor.schedule(TimeoutRunnable(callId, this), TIMEOUT_SECONDS, TimeUnit.SECONDS)
            callManager.setAudioEnabled(true)

            val expectedState = callManager.currentConnectionState
            val expectedCallId = callManager.callId

            try {
                val offerFuture = callManager.onOutgoingCall(this)
                offerFuture.fail { e ->
                    if (isConsistentState(expectedState, expectedCallId, callManager.currentConnectionState, callManager.callId)) {
                        Log.e(TAG,e)
                        callManager.postViewModelState(CallViewModel.State.NETWORK_FAILURE)
                        callManager.postConnectionError()
                        terminate()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG,e)
                callManager.postConnectionError()
                terminate()
            }
        }
    }

    private fun handleAnswerCall(intent: Intent) {
        val recipient = callManager.recipient ?: return
        val pending = callManager.pendingOffer ?: return
        val callId = callManager.callId ?: return
        val timestamp = callManager.pendingOfferTime

        if (callManager.currentConnectionState != CallState.RemoteRing) {
            Log.e(TAG, "Can only answer from ringing!")
            return
        }

        intent.putExtra(EXTRA_CALL_ID, callId)
        intent.putExtra(EXTRA_RECIPIENT_ADDRESS, recipient.address)
        intent.putExtra(EXTRA_REMOTE_DESCRIPTION, pending)
        intent.putExtra(EXTRA_TIMESTAMP, timestamp)

        if (isIncomingMessageExpired(intent)) {
            val didHangup = callManager.postConnectionEvent(Event.TimeOut) {
                insertMissedCall(recipient, true)
                terminate()
            }
            if (didHangup) {
                return
            }
        }

        callManager.postConnectionEvent(Event.SendAnswer) {
            setCallInProgressNotification(TYPE_INCOMING_CONNECTING, recipient)

            callManager.silenceIncomingRinger()
            callManager.postViewModelState(CallViewModel.State.CALL_INCOMING)

            scheduledTimeout = timeoutExecutor.schedule(TimeoutRunnable(callId, this), TIMEOUT_SECONDS, TimeUnit.SECONDS)

            callManager.initializeAudioForCall()
            callManager.initializeVideo(this)

            val expectedState = callManager.currentConnectionState
            val expectedCallId = callManager.callId

            try {
                val answerFuture = callManager.onIncomingCall(this)
                answerFuture.fail { e ->
                    if (isConsistentState(expectedState,expectedCallId, callManager.currentConnectionState, callManager.callId)) {
                        Log.e(TAG, e)
                        insertMissedCall(recipient, true)
                        callManager.postConnectionError()
                        terminate()
                    }
                }
                lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING)
                callManager.setAudioEnabled(true)
            } catch (e: Exception) {
                Log.e(TAG,e)
                callManager.postConnectionError()
                terminate()
            }
        }
    }

    private fun handleDenyCall(intent: Intent) {
        callManager.handleDenyCall()
        terminate()
    }

    private fun handleLocalHangup(intent: Intent) {
        val intentRecipient = getOptionalRemoteRecipient(intent)
        callManager.handleLocalHangup(intentRecipient)
        terminate()
    }

    private fun handleRemoteHangup(intent: Intent) {
        if (callManager.callId != getCallId(intent)) {
            Log.e(TAG, "Hangup for non-active call...")
            return
        }

        onHangup()
    }

    private fun handleSetMuteAudio(intent: Intent) {
        val muted = intent.getBooleanExtra(EXTRA_MUTE, false)
        callManager.handleSetMuteAudio(muted)
    }

    private fun handleSetMuteVideo(intent: Intent) {
        val muted = intent.getBooleanExtra(EXTRA_MUTE, false)
        callManager.handleSetMuteVideo(muted, lockManager)
    }

    private fun handleSetCameraFlip(intent: Intent) {
        callManager.handleSetCameraFlip()
    }

    private fun handleWiredHeadsetChanged(intent: Intent) {
        callManager.handleWiredHeadsetChanged(intent.getBooleanExtra(EXTRA_AVAILABLE, false))
    }

    private fun handleScreenOffChange(intent: Intent) {
        callManager.handleScreenOffChange()
    }

    private fun handleResponseMessage(intent: Intent) {
        try {
            val recipient = getRemoteRecipient(intent)
            if (callManager.isCurrentUser(recipient) && callManager.currentConnectionState in CallState.CAN_DECLINE_STATES) {
                handleLocalHangup(intent)
                return
            }
            val callId = getCallId(intent)
            val description = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION)
            callManager.handleResponseMessage(recipient, callId, SessionDescription(SessionDescription.Type.ANSWER, description))
        } catch (e: PeerConnectionException) {
            terminate()
        }
    }

    private fun handleRemoteIceCandidate(intent: Intent) {
        val callId = getCallId(intent)
        val sdpMids = intent.getStringArrayExtra(EXTRA_ICE_SDP_MID) ?: return
        val sdpLineIndexes = intent.getIntArrayExtra(EXTRA_ICE_SDP_LINE_INDEX) ?: return
        val sdps = intent.getStringArrayExtra(EXTRA_ICE_SDP) ?: return
        if (sdpMids.size != sdpLineIndexes.size || sdpLineIndexes.size != sdps.size) {
            Log.w(TAG,"sdp info not of equal length")
            return
        }
        val iceCandidates = sdpMids.indices.map { index ->
            IceCandidate(
                    sdpMids[index],
                    sdpLineIndexes[index],
                    sdps[index]
            )
        }
        callManager.handleRemoteIceCandidate(iceCandidates, callId)
    }

    private fun handleIceConnected(intent: Intent) {
        val recipient = callManager.recipient ?: return
        val connected = callManager.postConnectionEvent(Event.Connect) {
            callManager.postViewModelState(CallViewModel.State.CALL_CONNECTED)
            setCallInProgressNotification(TYPE_ESTABLISHED, recipient)
            callManager.startCommunication(lockManager)
        }
        if (!connected) {
            Log.e("Loki", "Error handling ice connected state transition")
            callManager.postConnectionError()
            terminate()
        }
    }

    private fun handleIsInCallQuery(intent: Intent) {
        val listener = intent.getParcelableExtra<ResultReceiver>(EXTRA_RESULT_RECEIVER) ?: return
        val currentState = callManager.currentConnectionState
        val isInCall = if (currentState in arrayOf(*CallState.PENDING_CONNECTION_STATES, CallState.Connected)) 1 else 0
        listener.send(isInCall, bundleOf())
    }

    private fun registerPowerButtonReceiver() {
        if (powerButtonReceiver == null) {
            powerButtonReceiver = PowerButtonReceiver()

            registerReceiver(powerButtonReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
    }

    private fun handleCheckReconnect(intent: Intent) {
        val callId = callManager.callId ?: return
        val numTimeouts = ++currentTimeouts

        if (callId == getCallId(intent) && isNetworkAvailable && numTimeouts <= MAX_RECONNECTS) {
            Log.i("Loki", "Trying to re-connect")
            callManager.networkReestablished()
            scheduledTimeout = timeoutExecutor.schedule(TimeoutRunnable(callId, this), TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } else if (numTimeouts < MAX_RECONNECTS) {
            Log.i("Loki", "Network isn't available, timeouts == $numTimeouts out of $MAX_RECONNECTS")
            scheduledReconnect = timeoutExecutor.schedule(CheckReconnectedRunnable(callId, this), RECONNECT_SECONDS, TimeUnit.SECONDS)
        } else {
            Log.i("Loki", "Network isn't available, timing out")
            handleLocalHangup(intent)
        }
    }



    private fun handleCheckTimeout(intent: Intent) {
        val callId = callManager.callId ?: return
        val callState = callManager.currentConnectionState

        if (callId == getCallId(intent) && (callState !in arrayOf(CallState.Connected, CallState.Connecting))) {
            Log.w(TAG, "Timing out call: $callId")
            handleLocalHangup(intent)
        }
    }

    private fun setCallInProgressNotification(type: Int, recipient: Recipient?) {
        startForeground(
                CallNotificationBuilder.WEBRTC_NOTIFICATION,
                CallNotificationBuilder.getCallInProgressNotification(this, type, recipient)
        )
        if (!CallNotificationBuilder.areNotificationsEnabled(this) && type == TYPE_INCOMING_PRE_OFFER) {
            // start an intent for the fullscreen
            val foregroundIntent = Intent(this, WebRtcCallActivity::class.java)
                .setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                .setAction(WebRtcCallActivity.ACTION_FULL_SCREEN_INTENT)
            startActivity(foregroundIntent)
        }
    }

    private fun getOptionalRemoteRecipient(intent: Intent): Recipient? =
        if (intent.hasExtra(EXTRA_RECIPIENT_ADDRESS)) {
            getRemoteRecipient(intent)
        } else {
            null
        }

    private fun getRemoteRecipient(intent: Intent): Recipient {
        val remoteAddress = intent.getParcelableExtra<Address>(EXTRA_RECIPIENT_ADDRESS)
                ?: throw AssertionError("No recipient in intent!")

        return Recipient.from(this, remoteAddress, true)
    }

    private fun getCallId(intent: Intent) : UUID {
        return intent.getSerializableExtra(EXTRA_CALL_ID) as? UUID
                ?: throw AssertionError("No callId in intent!")
    }

    private fun insertMissedCall(recipient: Recipient, signal: Boolean) {
        callManager.insertCallMessage(
            threadPublicKey = recipient.address.serialize(),
            callMessageType = CallMessageType.CALL_MISSED,
            signal = signal
        )
    }

    private fun isIncomingMessageExpired(intent: Intent) =
            System.currentTimeMillis() - intent.getLongExtra(EXTRA_TIMESTAMP, -1) > TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)

    override fun onDestroy() {
        Log.d(TAG,"onDestroy()")
        callManager.unregisterListener(this)
        callReceiver?.let { receiver ->
            unregisterReceiver(receiver)
        }
        networkChangedReceiver?.unregister(this)
        wantsToAnswerReceiver?.let { receiver ->
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        }
        networkChangedReceiver = null
        callReceiver = null
        uncaughtExceptionHandlerManager?.unregister()
        wantsToAnswer = false
        currentTimeouts = 0
        isNetworkAvailable = false
        super.onDestroy()
    }

    private fun networkChange(networkAvailable: Boolean) {
        Log.d("Loki", "flipping network available to $networkAvailable")
        isNetworkAvailable = networkAvailable
        if (networkAvailable && !callManager.isReestablishing && callManager.currentConnectionState == CallState.Connected) {
            Log.d("Loki", "Should reconnected")
        }
    }

    private class CheckReconnectedRunnable(private val callId: UUID, private val context: Context): Runnable {
        override fun run() {
            val intent = Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_CHECK_RECONNECT)
                .putExtra(EXTRA_CALL_ID, callId)
            context.startService(intent)
        }
    }

    private class ReconnectTimeoutRunnable(private val callId: UUID, private val context: Context): Runnable {
        override fun run() {
            val intent = Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_CHECK_RECONNECT_TIMEOUT)
                .putExtra(EXTRA_CALL_ID, callId)
            context.startService(intent)
        }
    }

    private class TimeoutRunnable(private val callId: UUID, private val context: Context): Runnable {
        override fun run() {
            val intent = Intent(context, WebRtcCallService::class.java)
                    .setAction(ACTION_CHECK_TIMEOUT)
                    .putExtra(EXTRA_CALL_ID, callId)
            context.startService(intent)
        }
    }

    private abstract class FailureListener<V>(
            expectedState: CallState,
            expectedCallId: UUID?,
            getState: () -> Pair<CallState, UUID?>): StateAwareListener<V>(expectedState, expectedCallId, getState) {
        override fun onSuccessContinue(result: V) {}
    }

    private abstract class SuccessOnlyListener<V>(
            expectedState: CallState,
            expectedCallId: UUID?,
            getState: () -> Pair<CallState, UUID>): StateAwareListener<V>(expectedState, expectedCallId, getState) {
        override fun onFailureContinue(throwable: Throwable?) {
            Log.e(TAG, throwable)
            throw AssertionError(throwable)
        }
    }

    private abstract class StateAwareListener<V>(
            private val expectedState: CallState,
            private val expectedCallId: UUID?,
            private val getState: ()->Pair<CallState, UUID?>): FutureTaskListener<V> {

        companion object {
            private val TAG = Log.tag(StateAwareListener::class.java)
        }

        override fun onSuccess(result: V) {
            if (!isConsistentState()) {
                Log.w(TAG,"State has changed since request, aborting success callback...")
            } else {
                onSuccessContinue(result)
            }
        }

        override fun onFailure(exception: ExecutionException?) {
            if (!isConsistentState()) {
                Log.w(TAG, exception)
                Log.w(TAG,"State has changed since request, aborting failure callback...")
            } else {
                exception?.let {
                    onFailureContinue(it.cause)
                }
            }
        }

        private fun isConsistentState(): Boolean {
            val (currentState, currentCallId) = getState()
            return expectedState == currentState && expectedCallId == currentCallId
        }

        abstract fun onSuccessContinue(result: V)
        abstract fun onFailureContinue(throwable: Throwable?)

    }

    private fun isConsistentState(
            expectedState: CallState,
            expectedCallId: UUID?,
            currentState: CallState,
            currentCallId: UUID?
    ): Boolean {
        return expectedState == currentState && expectedCallId == currentCallId
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        newState?.let { state -> processIceConnectionChange(state) }
    }

    private fun processIceConnectionChange(newState: PeerConnection.IceConnectionState) {
        serviceExecutor.execute {
            if (newState == CONNECTED) {
                scheduledTimeout?.cancel(false)
                scheduledReconnect?.cancel(false)
                scheduledTimeout = null
                scheduledReconnect = null

                val intent = Intent(this, WebRtcCallService::class.java)
                    .setAction(ACTION_ICE_CONNECTED)
                startService(intent)
            } else if (newState in arrayOf(FAILED, DISCONNECTED) && (scheduledReconnect == null && scheduledTimeout == null)) {
                callManager.callId?.let { callId ->
                    callManager.postConnectionEvent(Event.IceDisconnect) {
                        callManager.postViewModelState(CallViewModel.State.CALL_RECONNECTING)
                        if (callManager.isInitiator()) {
                            Log.i("Loki", "Starting reconnect timer")
                            scheduledReconnect = timeoutExecutor.schedule(CheckReconnectedRunnable(callId, this), RECONNECT_SECONDS, TimeUnit.SECONDS)
                        } else {
                            Log.i("Loki", "Starting timeout, awaiting new reconnect")
                            callManager.postConnectionEvent(Event.PrepareForNewOffer) {
                                scheduledTimeout = timeoutExecutor.schedule(TimeoutRunnable(callId, this), TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            }
                        }
                    }
                } ?: run {
                    val intent = hangupIntent(this)
                    startService(intent)
                }
            }
            Log.i("Loki", "onIceConnectionChange: $newState")
        }
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {}

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

    override fun onIceCandidate(p0: IceCandidate?) {}

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

    override fun onAddStream(p0: MediaStream?) {}

    override fun onRemoveStream(p0: MediaStream?) {}

    override fun onDataChannel(p0: DataChannel?) {}

    override fun onRenegotiationNeeded() {
        Log.w(TAG,"onRenegotiationNeeded was called!")
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
}