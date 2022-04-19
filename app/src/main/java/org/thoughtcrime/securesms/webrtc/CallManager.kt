package org.thoughtcrime.securesms.webrtc

import android.content.Context
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.messaging.messages.control.CallMessage
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Debouncer
import org.session.libsession.utilities.Util
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.protos.SignalServiceProtos.CallMessage.Type.ICE_CANDIDATES
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.webrtc.CallManager.StateEvent.AudioDeviceUpdate
import org.thoughtcrime.securesms.webrtc.CallManager.StateEvent.AudioEnabled
import org.thoughtcrime.securesms.webrtc.CallManager.StateEvent.RecipientUpdate
import org.thoughtcrime.securesms.webrtc.CallManager.StateEvent.VideoEnabled
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat
import org.thoughtcrime.securesms.webrtc.audio.OutgoingRinger
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.AudioDevice
import org.thoughtcrime.securesms.webrtc.data.Event
import org.thoughtcrime.securesms.webrtc.data.StateProcessor
import org.thoughtcrime.securesms.webrtc.locks.LockManager
import org.thoughtcrime.securesms.webrtc.video.CameraEventListener
import org.thoughtcrime.securesms.webrtc.video.CameraState
import org.thoughtcrime.securesms.webrtc.video.RemoteRotationVideoProxySink
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.UUID
import org.thoughtcrime.securesms.webrtc.data.State as CallState

class CallManager(context: Context, audioManager: AudioManagerCompat, private val storage: StorageProtocol): PeerConnection.Observer,
        SignalAudioManager.EventListener, CameraEventListener, DataChannel.Observer {

    sealed class StateEvent {
        data class AudioEnabled(val isEnabled: Boolean): StateEvent()
        data class VideoEnabled(val isEnabled: Boolean): StateEvent()
        data class CallStateUpdate(val state: CallState): StateEvent()
        data class AudioDeviceUpdate(val selectedDevice: AudioDevice, val audioDevices: Set<AudioDevice>): StateEvent()
        data class RecipientUpdate(val recipient: Recipient?): StateEvent() {
            companion object {
                val UNKNOWN = RecipientUpdate(recipient = null)
            }
        }
    }

    companion object {
        val VIDEO_DISABLED_JSON by lazy { buildJsonObject { put("video", false) } }
        val VIDEO_ENABLED_JSON by lazy { buildJsonObject { put("video", true) } }
        val HANGUP_JSON by lazy { buildJsonObject { put("hangup", true) } }

        private val TAG = Log.tag(CallManager::class.java)
        private const val DATA_CHANNEL_NAME = "signaling"
    }

    private val signalAudioManager: SignalAudioManager = SignalAudioManager(context, this, audioManager)

    private val peerConnectionObservers = mutableSetOf<WebRtcListener>()

    fun registerListener(listener: WebRtcListener) {
        peerConnectionObservers.add(listener)
    }

    fun unregisterListener(listener: WebRtcListener) {
        peerConnectionObservers.remove(listener)
    }

    private val _audioEvents = MutableStateFlow(AudioEnabled(false))
    val audioEvents = _audioEvents.asSharedFlow()
    private val _videoEvents = MutableStateFlow(VideoEnabled(false))
    val videoEvents = _videoEvents.asSharedFlow()
    private val _remoteVideoEvents = MutableStateFlow(VideoEnabled(false))
    val remoteVideoEvents = _remoteVideoEvents.asSharedFlow()

    private val stateProcessor = StateProcessor(CallState.Idle)

    private val _callStateEvents = MutableStateFlow(CallViewModel.State.CALL_PENDING)
    val callStateEvents = _callStateEvents.asSharedFlow()
    private val _recipientEvents = MutableStateFlow(RecipientUpdate.UNKNOWN)
    val recipientEvents = _recipientEvents.asSharedFlow()
    private var localCameraState: CameraState = CameraState.UNKNOWN

    private val _audioDeviceEvents = MutableStateFlow(AudioDeviceUpdate(AudioDevice.NONE, setOf()))
    val audioDeviceEvents = _audioDeviceEvents.asSharedFlow()

    val currentConnectionState
        get() = stateProcessor.currentState

    val currentCallState
        get() = _callStateEvents.value

    var iceState = IceConnectionState.CLOSED

    private var eglBase: EglBase? = null

    var pendingOffer: String? = null
    var pendingOfferTime: Long = -1
    var preOfferCallData: PreOffer? = null
    var callId: UUID? = null
    var recipient: Recipient? = null
    set(value) {
        field = value
        _recipientEvents.value = RecipientUpdate(value)
    }
    var callStartTime: Long = -1
    var isReestablishing: Boolean = false

    private var peerConnection: PeerConnectionWrapper? = null
    private var dataChannel: DataChannel? = null

    private val pendingOutgoingIceUpdates = ArrayDeque<IceCandidate>()
    private val pendingIncomingIceUpdates = ArrayDeque<IceCandidate>()

    private val outgoingIceDebouncer = Debouncer(200L)

    var localRenderer: SurfaceViewRenderer? = null
    var remoteRotationSink: RemoteRotationVideoProxySink? = null
    var remoteRenderer: SurfaceViewRenderer? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null

    fun clearPendingIceUpdates() {
        pendingOutgoingIceUpdates.clear()
        pendingIncomingIceUpdates.clear()
    }

    fun initializeAudioForCall() {
        signalAudioManager.handleCommand(AudioManagerCommand.Initialize)
    }

    fun startOutgoingRinger(ringerType: OutgoingRinger.Type) {
        if (ringerType == OutgoingRinger.Type.RINGING) {
            signalAudioManager.handleCommand(AudioManagerCommand.UpdateAudioDeviceState)
        }
        signalAudioManager.handleCommand(AudioManagerCommand.StartOutgoingRinger(ringerType))
    }

    fun silenceIncomingRinger() {
        signalAudioManager.handleCommand(AudioManagerCommand.SilenceIncomingRinger)
    }

    fun postConnectionEvent(transition: Event, onSuccess: ()->Unit): Boolean {
        return stateProcessor.processEvent(transition, onSuccess)
    }

    fun postConnectionError(): Boolean {
        return stateProcessor.processEvent(Event.Error)
    }

    fun postViewModelState(newState: CallViewModel.State) {
        Log.d("Loki", "Posting view model state $newState")
        _callStateEvents.value = newState
    }

    fun isBusy(context: Context, callId: UUID) = callId != this.callId && (currentConnectionState != CallState.Idle
            || context.getSystemService(TelephonyManager::class.java).callState  != TelephonyManager.CALL_STATE_IDLE)

    fun isPreOffer() = currentConnectionState == CallState.RemotePreOffer

    fun isIdle() = currentConnectionState == CallState.Idle

    fun isCurrentUser(recipient: Recipient) = recipient.address.serialize() == storage.getUserPublicKey()

    fun initializeVideo(context: Context) {
        Util.runOnMainSync {
            val base = EglBase.create()
            eglBase = base
            localRenderer = SurfaceViewRenderer(context).apply {
//                setScalingType(SCALE_ASPECT_FIT)
            }

            remoteRenderer = SurfaceViewRenderer(context).apply {
//                setScalingType(SCALE_ASPECT_FIT)
            }
            remoteRotationSink = RemoteRotationVideoProxySink()


            localRenderer?.init(base.eglBaseContext, null)
            localRenderer?.setMirror(localCameraState.activeDirection == CameraState.Direction.FRONT)
            remoteRenderer?.init(base.eglBaseContext, null)
            remoteRotationSink!!.setSink(remoteRenderer!!)

            val encoderFactory = DefaultVideoEncoderFactory(base.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(base.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                    .setOptions(object: PeerConnectionFactory.Options() {
                        init {
                            networkIgnoreMask = 1 shl 4
                        }
                    })
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory()
        }
    }

    fun callEnded() {
        peerConnection?.dispose()
        peerConnection = null
    }

    fun setAudioEnabled(isEnabled: Boolean) {
        currentConnectionState.withState(*CallState.CAN_HANGUP_STATES) {
            peerConnection?.setAudioEnabled(isEnabled)
            _audioEvents.value = AudioEnabled(true)
        }
    }

    override fun onSignalingChange(newState: PeerConnection.SignalingState) {
        peerConnectionObservers.forEach { listener -> listener.onSignalingChange(newState) }
    }

    override fun onIceConnectionChange(newState: IceConnectionState) {
        Log.d("Loki", "New ice connection state = $newState")
        iceState = newState
        peerConnectionObservers.forEach { listener -> listener.onIceConnectionChange(newState) }
        if (newState == IceConnectionState.CONNECTED) {
            callStartTime = System.currentTimeMillis()
        }
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        peerConnectionObservers.forEach { listener -> listener.onIceConnectionReceivingChange(receiving) }
    }

    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
        peerConnectionObservers.forEach { listener -> listener.onIceGatheringChange(newState) }
    }

    override fun onIceCandidate(iceCandidate: IceCandidate) {
        peerConnectionObservers.forEach { listener -> listener.onIceCandidate(iceCandidate) }
        val expectedCallId = this.callId ?: return
        val expectedRecipient = this.recipient ?: return
        pendingOutgoingIceUpdates.add(iceCandidate)

        if (peerConnection?.readyForIce != true) return

        queueOutgoingIce(expectedCallId, expectedRecipient)
    }

    private fun queueOutgoingIce(expectedCallId: UUID, expectedRecipient: Recipient) {
        outgoingIceDebouncer.publish {
            val currentCallId = this.callId ?: return@publish
            val currentRecipient = this.recipient ?: return@publish
            if (currentCallId == expectedCallId && expectedRecipient == currentRecipient) {
                val currentPendings = mutableSetOf<IceCandidate>()
                while (pendingOutgoingIceUpdates.isNotEmpty()) {
                    currentPendings.add(pendingOutgoingIceUpdates.pop())
                }
                val sdps = currentPendings.map { it.sdp }
                val sdpMLineIndexes = currentPendings.map { it.sdpMLineIndex }
                val sdpMids = currentPendings.map { it.sdpMid }

                MessageSender.sendNonDurably(CallMessage(
                        ICE_CANDIDATES,
                        sdps = sdps,
                        sdpMLineIndexes = sdpMLineIndexes,
                        sdpMids = sdpMids,
                        currentCallId
                ), currentRecipient.address)
            }
        }
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        peerConnectionObservers.forEach { listener -> listener.onIceCandidatesRemoved(candidates) }
    }

    override fun onAddStream(stream: MediaStream) {
        peerConnectionObservers.forEach { listener -> listener.onAddStream(stream) }
        for (track in stream.audioTracks) {
            track.setEnabled(true)
        }

        if (stream.videoTracks != null && stream.videoTracks.size == 1) {
            val videoTrack = stream.videoTracks.first()
            videoTrack.setEnabled(true)
            videoTrack.addSink(remoteRotationSink)
        }
    }

    override fun onRemoveStream(p0: MediaStream?) {
        peerConnectionObservers.forEach { listener -> listener.onRemoveStream(p0) }
    }

    override fun onDataChannel(p0: DataChannel?) {
        peerConnectionObservers.forEach { listener -> listener.onDataChannel(p0) }
    }

    override fun onRenegotiationNeeded() {
        peerConnectionObservers.forEach { listener -> listener.onRenegotiationNeeded() }
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        peerConnectionObservers.forEach { listener -> listener.onAddTrack(p0, p1) }
    }

    override fun onBufferedAmountChange(l: Long) {
        Log.i(TAG,"onBufferedAmountChange: $l")
    }

    override fun onStateChange() {
        Log.i(TAG,"onStateChange")
    }

    override fun onMessage(buffer: DataChannel.Buffer?) {
        Log.i(TAG,"onMessage...")
        buffer ?: return

        try {
            val byteArray = ByteArray(buffer.data.remaining()) { buffer.data[it] }
            val json = Json.parseToJsonElement(byteArray.decodeToString()) as JsonObject
            if (json.containsKey("video")) {
                _remoteVideoEvents.value = VideoEnabled((json["video"] as JsonPrimitive).boolean)
            } else if (json.containsKey("hangup")) {
                peerConnectionObservers.forEach(WebRtcListener::onHangup)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize data channel message", e)
        }
    }

    override fun onAudioDeviceChanged(activeDevice: AudioDevice, devices: Set<AudioDevice>) {
        _audioDeviceEvents.value = AudioDeviceUpdate(activeDevice, devices)
    }

    fun stop() {
        val isOutgoing = currentConnectionState in CallState.OUTGOING_STATES
        stateProcessor.processEvent(Event.Cleanup) {
            signalAudioManager.handleCommand(AudioManagerCommand.Stop(isOutgoing))
            peerConnection?.dispose()
            peerConnection = null

            localRenderer?.release()
            remoteRotationSink?.release()
            remoteRenderer?.release()
            eglBase?.release()

            localRenderer = null
            remoteRenderer = null
            eglBase = null

            localCameraState = CameraState.UNKNOWN
            recipient = null
            callId = null
            pendingOfferTime = -1
            pendingOffer = null
            callStartTime = -1
            _audioEvents.value = AudioEnabled(false)
            _videoEvents.value = VideoEnabled(false)
            _remoteVideoEvents.value = VideoEnabled(false)
            pendingOutgoingIceUpdates.clear()
            pendingIncomingIceUpdates.clear()
        }
    }

    override fun onCameraSwitchCompleted(newCameraState: CameraState) {
        localCameraState = newCameraState
    }

    fun onPreOffer(callId: UUID, recipient: Recipient, onSuccess: () -> Unit) {
        stateProcessor.processEvent(Event.ReceivePreOffer) {
            if (preOfferCallData != null) {
                Log.d(TAG, "Received new pre-offer when we are already expecting one")
            }
            this.recipient = recipient
            this.callId = callId
            preOfferCallData = PreOffer(callId, recipient)
            onSuccess()
        }
    }

    fun onNewOffer(offer: String, callId: UUID, recipient: Recipient): Promise<Unit, Exception> {
        if (callId != this.callId) return Promise.ofFail(NullPointerException("No callId"))
        if (recipient != this.recipient) return Promise.ofFail(NullPointerException("No recipient"))

        val connection = peerConnection ?: return Promise.ofFail(NullPointerException("No peer connection wrapper"))

        val reconnected = stateProcessor.processEvent(Event.ReceiveOffer) && stateProcessor.processEvent(Event.SendAnswer)
        return if (reconnected) {
            Log.i("Loki", "Handling new offer, restarting ice session")
            connection.setNewRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offer))
            // re-established an ice
            val answer = connection.createAnswer(MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            })
            connection.setLocalDescription(answer)
            pendingIncomingIceUpdates.toList().forEach { update ->
                connection.addIceCandidate(update)
            }
            pendingIncomingIceUpdates.clear()
            val answerMessage = CallMessage.answer(answer.description, callId)
            Log.i("Loki", "Posting new answer")
            MessageSender.sendNonDurably(answerMessage, recipient.address)
        } else {
            Promise.ofFail(Exception("Couldn't reconnect from current state"))
        }
    }

    fun onIncomingRing(offer: String, callId: UUID, recipient: Recipient, callTime: Long, onSuccess: () -> Unit) {
        postConnectionEvent(Event.ReceiveOffer) {
            this.callId = callId
            this.recipient = recipient
            this.pendingOffer = offer
            this.pendingOfferTime = callTime
            initializeAudioForCall()
            startIncomingRinger()
            onSuccess()
        }
    }

    fun onIncomingCall(context: Context, isAlwaysTurn: Boolean = false): Promise<Unit, Exception> {
        val callId = callId ?: return Promise.ofFail(NullPointerException("callId is null"))
        val recipient = recipient ?: return Promise.ofFail(NullPointerException("recipient is null"))
        val offer = pendingOffer ?: return Promise.ofFail(NullPointerException("pendingOffer is null"))
        val factory = peerConnectionFactory ?: return Promise.ofFail(NullPointerException("peerConnectionFactory is null"))
        val local = localRenderer ?: return Promise.ofFail(NullPointerException("localRenderer is null"))
        val base = eglBase ?: return Promise.ofFail(NullPointerException("eglBase is null"))
        val connection = PeerConnectionWrapper(
                context,
                factory,
                this,
                local,
                this,
                base,
                isAlwaysTurn
        )
        peerConnection = connection
        localCameraState = connection.getCameraState()
        val dataChannel = connection.createDataChannel(DATA_CHANNEL_NAME)
        this.dataChannel = dataChannel
        dataChannel.registerObserver(this)
        connection.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offer))
        val answer = connection.createAnswer(MediaConstraints())
        connection.setLocalDescription(answer)
        val answerMessage = CallMessage.answer(answer.description, callId)
        val userAddress = storage.getUserPublicKey() ?: return Promise.ofFail(NullPointerException("No user public key"))
        MessageSender.sendNonDurably(answerMessage, Address.fromSerialized(userAddress))
        val sendAnswerMessage = MessageSender.sendNonDurably(CallMessage.answer(
                answer.description,
                callId
        ), recipient.address)

        insertCallMessage(recipient.address.serialize(), CallMessageType.CALL_INCOMING, false)

        while (pendingIncomingIceUpdates.isNotEmpty()) {
            val candidate = pendingIncomingIceUpdates.pop() ?: break
            connection.addIceCandidate(candidate)
        }
        return sendAnswerMessage.success {
            pendingOffer = null
            pendingOfferTime = -1
        }
    }

    fun onOutgoingCall(context: Context, isAlwaysTurn: Boolean = false): Promise<Unit, Exception> {
        val callId = callId ?: return Promise.ofFail(NullPointerException("callId is null"))
        val recipient = recipient
                ?: return Promise.ofFail(NullPointerException("recipient is null"))
        val factory = peerConnectionFactory
                ?: return Promise.ofFail(NullPointerException("peerConnectionFactory is null"))
        val local = localRenderer
                ?: return Promise.ofFail(NullPointerException("localRenderer is null"))
        val base = eglBase ?: return Promise.ofFail(NullPointerException("eglBase is null"))

        val sentOffer = stateProcessor.processEvent(Event.SendOffer)

        if (!sentOffer) {
            return Promise.ofFail(Exception("Couldn't transition to sent offer state"))
        } else {
            val connection = PeerConnectionWrapper(
                context,
                factory,
                this,
                local,
                this,
                base,
                isAlwaysTurn
            )

            peerConnection = connection
            localCameraState = connection.getCameraState()
            val dataChannel = connection.createDataChannel(DATA_CHANNEL_NAME)
            dataChannel.registerObserver(this)
            this.dataChannel = dataChannel
            val offer = connection.createOffer(MediaConstraints())
            connection.setLocalDescription(offer)

            Log.d("Loki", "Sending pre-offer")
            return MessageSender.sendNonDurably(CallMessage.preOffer(
                callId
            ), recipient.address).bind {
                Log.d("Loki", "Sent pre-offer")
                Log.d("Loki", "Sending offer")
                MessageSender.sendNonDurably(CallMessage.offer(
                    offer.description,
                    callId
                ), recipient.address).success {
                    Log.d("Loki", "Sent offer")
                }.fail {
                    Log.e("Loki", "Failed to send offer", it)
                }
            }
        }
    }

    fun handleDenyCall() {
        val callId = callId ?: return
        val recipient = recipient ?: return
        val userAddress = storage.getUserPublicKey() ?: return
        stateProcessor.processEvent(Event.DeclineCall) {
            MessageSender.sendNonDurably(CallMessage.endCall(callId), Address.fromSerialized(userAddress))
            MessageSender.sendNonDurably(CallMessage.endCall(callId), recipient.address)
            insertCallMessage(recipient.address.serialize(), CallMessageType.CALL_MISSED)
        }
    }

    fun handleLocalHangup(intentRecipient: Recipient?) {
        val recipient = recipient ?: return
        val callId = callId ?: return

        val currentUserPublicKey  = storage.getUserPublicKey()
        val sendHangup = intentRecipient == null || (intentRecipient == recipient && recipient.address.serialize() != currentUserPublicKey)

        postViewModelState(CallViewModel.State.CALL_DISCONNECTED)
        stateProcessor.processEvent(Event.Hangup)
        if (sendHangup) {
            dataChannel?.let { channel ->
                val buffer = DataChannel.Buffer(ByteBuffer.wrap(HANGUP_JSON.toString().encodeToByteArray()), false)
                channel.send(buffer)
            }
            MessageSender.sendNonDurably(CallMessage.endCall(callId), recipient.address)
        }
    }

    fun insertCallMessage(threadPublicKey: String, callMessageType: CallMessageType, signal: Boolean = false, sentTimestamp: Long = System.currentTimeMillis()) {
        storage.insertCallMessage(threadPublicKey, callMessageType, sentTimestamp)
    }

    fun handleRemoteHangup() {
        when (currentConnectionState) {
            CallState.LocalRing,
            CallState.RemoteRing -> postViewModelState(CallViewModel.State.RECIPIENT_UNAVAILABLE)
            else -> postViewModelState(CallViewModel.State.CALL_DISCONNECTED)
        }
        if (!stateProcessor.processEvent(Event.Hangup)) {
            Log.e("Loki", "")
            stateProcessor.processEvent(Event.Error)
        }
    }

    fun handleSetMuteAudio(muted: Boolean) {
        _audioEvents.value = AudioEnabled(!muted)
        peerConnection?.setAudioEnabled(!muted)
    }

    fun handleSetMuteVideo(muted: Boolean, lockManager: LockManager) {
        _videoEvents.value = VideoEnabled(!muted)
        val connection = peerConnection ?: return
        connection.setVideoEnabled(!muted)
        dataChannel?.let { channel ->
            val toSend = if (muted) VIDEO_DISABLED_JSON else VIDEO_ENABLED_JSON
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(toSend.toString().encodeToByteArray()), false)
            channel.send(buffer)
        }

        if (currentConnectionState == CallState.Connected) {
            if (connection.isVideoEnabled()) lockManager.updatePhoneState(LockManager.PhoneState.IN_VIDEO)
            else lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL)
        }

        if (localCameraState.enabled
                && !signalAudioManager.isSpeakerphoneOn()
                && !signalAudioManager.isBluetoothScoOn()
                && !signalAudioManager.isWiredHeadsetOn()
        ) {
            signalAudioManager.handleCommand(AudioManagerCommand.SetUserDevice(AudioDevice.SPEAKER_PHONE))
        }
    }

    fun handleSetCameraFlip() {
        if (!localCameraState.enabled) return
        peerConnection?.let { connection ->
            connection.flipCamera()
            localCameraState = connection.getCameraState()
            localRenderer?.setMirror(localCameraState.activeDirection == CameraState.Direction.FRONT)
        }
    }

    fun setDeviceRotation(newRotation: Int) {
        peerConnection?.setDeviceRotation(newRotation)
        remoteRotationSink?.rotation = newRotation
    }

    fun handleWiredHeadsetChanged(present: Boolean) {
        if (currentConnectionState in arrayOf(CallState.Connected,
                        CallState.LocalRing,
                        CallState.RemoteRing)) {
            if (present && signalAudioManager.isSpeakerphoneOn()) {
                signalAudioManager.handleCommand(AudioManagerCommand.SetUserDevice(AudioDevice.WIRED_HEADSET))
            } else if (!present && !signalAudioManager.isSpeakerphoneOn() && !signalAudioManager.isBluetoothScoOn() && localCameraState.enabled) {
                signalAudioManager.handleCommand(AudioManagerCommand.SetUserDevice(AudioDevice.SPEAKER_PHONE))
            }
        }
    }

    fun handleScreenOffChange() {
        if (currentConnectionState in arrayOf(CallState.Connecting, CallState.LocalRing)) {
            signalAudioManager.handleCommand(AudioManagerCommand.SilenceIncomingRinger)
        }
    }

    fun handleResponseMessage(recipient: Recipient, callId: UUID, answer: SessionDescription) {
        if (recipient != this.recipient || callId != this.callId) {
            Log.w(TAG,"Got answer for recipient and call ID we're not currently dialing")
            return
        }

        stateProcessor.processEvent(Event.ReceiveAnswer) {
            val connection = peerConnection ?: throw AssertionError("assert")

            connection.setRemoteDescription(answer)
            while (pendingIncomingIceUpdates.isNotEmpty()) {
                connection.addIceCandidate(pendingIncomingIceUpdates.pop())
            }
            queueOutgoingIce(callId, recipient)
        }
    }

    fun handleRemoteIceCandidate(iceCandidates: List<IceCandidate>, callId: UUID) {
        if (callId != this.callId) {
            Log.w(TAG, "Got remote ice candidates for a call that isn't active")
            return
        }

        val connection = peerConnection
        if (connection != null && connection.readyForIce && currentConnectionState != CallState.Reconnecting) {
            Log.i("Loki", "Handling connection ice candidate")
            iceCandidates.forEach { candidate ->
                connection.addIceCandidate(candidate)
            }
        } else {
            Log.i("Loki", "Handling add to pending ice candidate")
            pendingIncomingIceUpdates.addAll(iceCandidates)
        }
    }

    fun startIncomingRinger() {
        signalAudioManager.handleCommand(AudioManagerCommand.StartIncomingRinger(true))
    }

    fun startCommunication(lockManager: LockManager) {
        signalAudioManager.handleCommand(AudioManagerCommand.Start)
        val connection = peerConnection ?: return
        if (connection.isVideoEnabled()) lockManager.updatePhoneState(LockManager.PhoneState.IN_VIDEO)
        else lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL)
        connection.setCommunicationMode()
        setAudioEnabled(true)
        dataChannel?.let { channel ->
            val toSend = if (!_videoEvents.value.isEnabled) VIDEO_DISABLED_JSON else VIDEO_ENABLED_JSON
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(toSend.toString().encodeToByteArray()), false)
            channel.send(buffer)
        }
    }

    fun handleAudioCommand(audioCommand: AudioManagerCommand) {
        signalAudioManager.handleCommand(audioCommand)
    }

    fun networkReestablished() {
        val connection = peerConnection ?: return
        val callId = callId ?: return
        val recipient = recipient ?: return

        postConnectionEvent(Event.NetworkReconnect) {
            Log.d("Loki", "start re-establish")

            val offer = connection.createOffer(MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            })
            connection.setLocalDescription(offer)

            MessageSender.sendNonDurably(CallMessage.offer(offer.description, callId), recipient.address)
        }
    }

    fun isInitiator(): Boolean = peerConnection?.isInitiator() == true

    interface WebRtcListener: PeerConnection.Observer {
        fun onHangup()
    }

}