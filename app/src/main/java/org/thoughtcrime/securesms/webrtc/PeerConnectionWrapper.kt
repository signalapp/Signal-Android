package org.thoughtcrime.securesms.webrtc

import android.content.Context
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.SettableFuture
import org.thoughtcrime.securesms.webrtc.video.Camera
import org.thoughtcrime.securesms.webrtc.video.CameraEventListener
import org.thoughtcrime.securesms.webrtc.video.CameraState
import org.thoughtcrime.securesms.webrtc.video.RotationVideoSink
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.security.SecureRandom
import java.util.concurrent.ExecutionException
import kotlin.random.asKotlinRandom

class PeerConnectionWrapper(private val context: Context,
                            private val factory: PeerConnectionFactory,
                            private val observer: PeerConnection.Observer,
                            private val localRenderer: VideoSink,
                            private val cameraEventListener: CameraEventListener,
                            private val eglBase: EglBase,
                            private val relay: Boolean = false): CameraEventListener {

    private var peerConnection: PeerConnection? = null
    private val audioTrack: AudioTrack
    private val audioSource: AudioSource
    private val camera: Camera
    private val mediaStream: MediaStream
    private val videoSource: VideoSource?
    private val videoTrack: VideoTrack?
    private val rotationVideoSink = RotationVideoSink()

    val readyForIce
        get() = peerConnection?.localDescription != null && peerConnection?.remoteDescription != null

    private var isInitiator = false

    private fun initPeerConnection() {
        val random = SecureRandom().asKotlinRandom()
        val iceServers = listOf("freyr","fenrir","frigg","angus","hereford","holstein", "brahman").shuffled(random).take(2).map { sub ->
            PeerConnection.IceServer.builder("turn:$sub.getsession.org")
                .setUsername("session202111")
                .setPassword("053c268164bc7bd7")
                .createIceServer()
        }

        val constraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }

        val configuration = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            if (relay) {
                iceTransportsType = PeerConnection.IceTransportsType.RELAY
            }
        }

        val newPeerConnection = factory.createPeerConnection(configuration, constraints, observer)!!
        peerConnection = newPeerConnection
        newPeerConnection.setAudioPlayout(true)
        newPeerConnection.setAudioRecording(true)

        newPeerConnection.addStream(mediaStream)
    }

    init {
        val audioConstraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }

        mediaStream = factory.createLocalMediaStream("ARDAMS")
        audioSource = factory.createAudioSource(audioConstraints)
        audioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        audioTrack.setEnabled(true)
        mediaStream.addTrack(audioTrack)

        val newCamera = Camera(context, this)
        camera = newCamera

        if (newCamera.capturer != null) {
            val newVideoSource = factory.createVideoSource(false)
            videoSource = newVideoSource
            val newVideoTrack = factory.createVideoTrack("ARDAMSv0", newVideoSource)
            videoTrack = newVideoTrack

            rotationVideoSink.setObserver(newVideoSource.capturerObserver)
            newCamera.capturer.initialize(
                SurfaceTextureHelper.create("WebRTC-SurfaceTextureHelper", eglBase.eglBaseContext),
                context,
                rotationVideoSink
            )
            rotationVideoSink.mirrored = newCamera.activeDirection == CameraState.Direction.FRONT
            rotationVideoSink.setSink(localRenderer)
            newVideoTrack.setEnabled(false)
            mediaStream.addTrack(newVideoTrack)
        } else {
            videoSource = null
            videoTrack = null
        }
        initPeerConnection()
    }

    fun getCameraState(): CameraState {
        return CameraState(camera.activeDirection, camera.cameraCount)
    }

    fun createDataChannel(channelName: String): DataChannel {
        val dataChannelConfiguration = DataChannel.Init().apply {
            ordered = true
            negotiated = true
            id = 548
        }
        return peerConnection!!.createDataChannel(channelName, dataChannelConfiguration)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        // TODO: filter logic based on known servers
        peerConnection!!.addIceCandidate(candidate)
    }

    fun dispose() {
        camera.dispose()

        videoSource?.dispose()

        audioSource.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
    }

    fun setNewRemoteDescription(description: SessionDescription) {
        val future = SettableFuture<Boolean>()

        peerConnection!!.setRemoteDescription(object: SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                throw AssertionError()
            }

            override fun onCreateFailure(p0: String?) {
                throw AssertionError()
            }

            override fun onSetSuccess() {
                future.set(true)
            }

            override fun onSetFailure(error: String?) {
                future.setException(PeerConnectionException(error))
            }
        }, description)

        try {
            future.get()
        } catch (e: InterruptedException) {
            throw AssertionError(e)
        } catch (e: ExecutionException) {
            throw PeerConnectionException(e)
        }
    }

    fun setRemoteDescription(description: SessionDescription) {
        val future = SettableFuture<Boolean>()

        peerConnection!!.setRemoteDescription(object: SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                throw AssertionError()
            }

            override fun onCreateFailure(p0: String?) {
                throw AssertionError()
            }

            override fun onSetSuccess() {
                future.set(true)
            }

            override fun onSetFailure(error: String?) {
                future.setException(PeerConnectionException(error))
            }
        }, description)

        try {
            future.get()
        } catch (e: InterruptedException) {
            throw AssertionError(e)
        } catch (e: ExecutionException) {
            throw PeerConnectionException(e)
        }
    }

    fun createAnswer(mediaConstraints: MediaConstraints) : SessionDescription {
        val future = SettableFuture<SessionDescription>()

        peerConnection!!.createAnswer(object:SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                future.set(sdp)
            }

            override fun onSetSuccess() {
                throw AssertionError()
            }

            override fun onCreateFailure(p0: String?) {
                future.setException(PeerConnectionException(p0))
            }

            override fun onSetFailure(p0: String?) {
                throw AssertionError()
            }
        }, mediaConstraints)

        try {
            return correctSessionDescription(future.get())
        } catch (e: InterruptedException) {
            throw AssertionError()
        } catch (e: ExecutionException) {
            throw PeerConnectionException(e)
        }
    }

    private fun correctSessionDescription(sessionDescription: SessionDescription): SessionDescription {
        val updatedSdp = sessionDescription.description.replace("(a=fmtp:111 ((?!cbr=).)*)\r?\n".toRegex(), "$1;cbr=1\r\n")
                .replace(".+urn:ietf:params:rtp-hdrext:ssrc-audio-level.*\r?\n".toRegex(), "")

        return SessionDescription(sessionDescription.type, updatedSdp)
    }

    fun createOffer(mediaConstraints: MediaConstraints): SessionDescription {
        val future = SettableFuture<SessionDescription>()

        peerConnection!!.createOffer(object:SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                future.set(sdp)
            }

            override fun onSetSuccess() {
                throw AssertionError()
            }

            override fun onCreateFailure(p0: String?) {
                future.setException(PeerConnectionException(p0))
            }

            override fun onSetFailure(p0: String?) {
                throw AssertionError()
            }
        }, mediaConstraints)

        try {
            isInitiator = true
            return correctSessionDescription(future.get())
        } catch (e: InterruptedException) {
            throw AssertionError()
        } catch (e: ExecutionException) {
            throw PeerConnectionException(e)
        }
    }

    fun setLocalDescription(sdp: SessionDescription) {
        val future = SettableFuture<Boolean>()

        peerConnection!!.setLocalDescription(object: SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {

            }

            override fun onSetSuccess() {
                future.set(true)
            }

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(error: String?) {
                future.setException(PeerConnectionException(error))
            }
        }, sdp)

        try {
            future.get()
        } catch(e: InterruptedException) {
            throw AssertionError(e)
        } catch(e: ExecutionException) {
            throw PeerConnectionException(e)
        }
    }

    fun setCommunicationMode() {
        peerConnection?.setAudioPlayout(true)
        peerConnection?.setAudioRecording(true)
    }

    fun setAudioEnabled(isEnabled: Boolean) {
        audioTrack.setEnabled(isEnabled)
    }

    fun setDeviceRotation(rotation: Int) {
        Log.d("Loki", "rotation: $rotation")
        rotationVideoSink.rotation = rotation
    }

    fun setVideoEnabled(isEnabled: Boolean) {
        videoTrack?.let { track ->
            track.setEnabled(isEnabled)
            camera.enabled = isEnabled
        }
    }

    fun isVideoEnabled() = camera.enabled

    fun flipCamera() {
        camera.flip()
    }

    override fun onCameraSwitchCompleted(newCameraState: CameraState) {
        // mirror rotation offset
        rotationVideoSink.mirrored = newCameraState.activeDirection == CameraState.Direction.FRONT
        cameraEventListener.onCameraSwitchCompleted(newCameraState)
    }

    fun isInitiator(): Boolean = isInitiator
}