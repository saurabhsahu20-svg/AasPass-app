package com.aaspass.app.calls

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Live P2P WebRTC session; never records or stores voice/video content.
 * Must be created after Android microphone/camera permission is granted.
 * Peer connection uses DTLS-SRTP; TURN credentials are provided by a backend.
 */
class RtcSession(
    context: Context, private val video: Boolean,
    iceServers: List<PeerConnection.IceServer>,
    onIceCandidateFound: (IceCandidate) -> Unit,
    private val onConnectionChanged: (String) -> Unit,
    private val onRemoteVideo: (VideoTrack) -> Unit
) {
    companion object {
        @Volatile private var initialized = false
        @Synchronized private fun initialize(context: Context) {
            if (!initialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .createInitializationOptions()
                )
                initialized = true
            }
        }
    }

    val egl: EglBase = run { initialize(context); EglBase.create() }
    private val factory: PeerConnectionFactory
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val previousMode = audioManager.mode
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack
    private var videoSource: VideoSource? = null
    val localVideoTrack: VideoTrack?
    private var captureHelper: SurfaceTextureHelper? = null
    private var capturer: CameraVideoCapturer? = null
    private var remoteVideoTrack: VideoTrack? = null
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var remoteSdpApplied = false
    private val peer: PeerConnection
    private var closed = false

    init {
        initialize(context)
        val encoder = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(egl.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder).createPeerConnectionFactory()
        val constraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        audioSource = factory.createAudioSource(constraints)
        audioTrack = factory.createAudioTrack("AasPassAudio", audioSource)
        localVideoTrack = if (video) {
            val enumerator = Camera2Enumerator(context)
            val camera = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
                ?: throw IllegalStateException("This device has no available camera")
            capturer = enumerator.createCapturer(camera, null) as? CameraVideoCapturer
                ?: throw IllegalStateException("Cannot open the camera")
            captureHelper = SurfaceTextureHelper.create("AasPassCapture", egl.eglBaseContext)
            videoSource = factory.createVideoSource(false)
            capturer?.initialize(requireNotNull(captureHelper), context, requireNotNull(videoSource).capturerObserver)
            capturer?.startCapture(640, 480, 20) // Network adaptation is handled by WebRTC.
            factory.createVideoTrack("AasPassVideo", requireNotNull(videoSource))
        } else null
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                onConnectionChanged(newState.name)
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) { onIceCandidateFound(candidate) }
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(dataChannel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onTrack(transceiver: RtpTransceiver) {
                (transceiver.receiver.track() as? VideoTrack)?.let { track ->
                    remoteVideoTrack = track
                    onRemoteVideo(track)
                }
            }
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
                (receiver.track() as? VideoTrack)?.let { track ->
                    remoteVideoTrack = track
                    onRemoteVideo(track)
                }
            }
        }
        peer = requireNotNull(factory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }, observer)) { "Cannot create a WebRTC connection" }
        peer.addTrack(audioTrack, listOf("aaspass"))
        localVideoTrack?.let { peer.addTrack(it, listOf("aaspass")) }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = video
    }

    fun setMuted(muted: Boolean) { audioTrack.setEnabled(!muted) }
    fun setCameraEnabled(enabled: Boolean) { localVideoTrack?.setEnabled(enabled) }
    fun setSpeaker(enabled: Boolean) { audioManager.isSpeakerphoneOn = enabled }
    fun switchCamera() { capturer?.switchCamera(null) }
    fun attachLocalRenderer(view: SurfaceViewRenderer) { localVideoTrack?.addSink(view) }
    fun attachRemoteRenderer(view: SurfaceViewRenderer) { remoteVideoTrack?.addSink(view) }
    fun detachLocalRenderer(view: SurfaceViewRenderer) { localVideoTrack?.removeSink(view) }
    fun detachRemoteRenderer(view: SurfaceViewRenderer) { remoteVideoTrack?.removeSink(view) }

    suspend fun offer(): String {
        val sdp = createSdp(true)
        setLocal(sdp)
        return sdp.description
    }
    suspend fun acceptOffer(offer: String): String {
        setRemote(SessionDescription(SessionDescription.Type.OFFER, offer))
        val answer = createSdp(false)
        setLocal(answer)
        return answer.description
    }
    suspend fun receiveAnswer(answer: String) {
        setRemote(SessionDescription(SessionDescription.Type.ANSWER, answer))
    }
    fun addRemoteIce(candidate: IceCandidate) {
        if (closed) return
        if (remoteSdpApplied) peer.addIceCandidate(candidate)
        else pendingCandidates.add(candidate)
    }

    private suspend fun createSdp(offer: Boolean): SessionDescription = suspendCancellableCoroutine { cont ->
        val observer = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                if (cont.isActive) cont.resume(desc)
            }
            override fun onCreateFailure(message: String) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(message))
            }
            override fun onSetSuccess() = Unit
            override fun onSetFailure(message: String) = Unit
        }
        val constraints = MediaConstraints()
        if (offer) peer.createOffer(observer, constraints)
        else peer.createAnswer(observer, constraints)
    }
    private suspend fun setLocal(sdp: SessionDescription) = setSdp(sdp, local = true)
    private suspend fun setRemote(sdp: SessionDescription) {
        setSdp(sdp, local = false)
        remoteSdpApplied = true
        pendingCandidates.forEach { peer.addIceCandidate(it) }
        pendingCandidates.clear()
    }
    private suspend fun setSdp(sdp: SessionDescription, local: Boolean): Unit =
        suspendCancellableCoroutine { cont ->
            val observer = object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) = Unit
                override fun onCreateFailure(message: String) = Unit
                override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
                override fun onSetFailure(message: String) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(message))
                }
            }
            if (local) peer.setLocalDescription(observer, sdp)
            else peer.setRemoteDescription(observer, sdp)
        }

    fun close() {
        if (closed) return
        closed = true
        try { capturer?.stopCapture() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        try { capturer?.dispose() } catch (_: Exception) { }
        peer.close()
        peer.dispose()
        localVideoTrack?.dispose()
        videoSource?.dispose()
        captureHelper?.dispose()
        audioTrack.dispose()
        audioSource.dispose()
        factory.dispose()
        audioManager.mode = previousMode
        audioManager.isSpeakerphoneOn = false
        egl.release()
    }
}
