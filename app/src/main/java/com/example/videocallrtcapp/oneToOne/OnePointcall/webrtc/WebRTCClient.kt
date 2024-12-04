package com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) {
    //class variables
    var listener: WebRTCClientListener? = null

    var localId = Instant.now().toString()

    //webrtc variables
    private val eglBaseContext = EglBase.create().eglBaseContext
    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }

    private var peerConnectionList:MutableMap<String,PeerConnection> = mutableMapOf()

    // Creating a STUN server
    private val stunServer: PeerConnection.IceServer = PeerConnection.IceServer
        .builder("stun:stun4.l.google.com:19302")
        .createIceServer()

    // Creating a TURN server with credentials
    private val turnServer: PeerConnection.IceServer = PeerConnection.IceServer
        .builder("turn:a.relay.metered.ca:443?transport=tcp")
        .setUsername("83eebabf8b4cce9d5dbcb649")
        .setPassword("2D7JvfkOQtBdYW3R")
        .createIceServer()

    private val iceServers = listOf(stunServer,turnServer)

    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints())}
    private val videoCapture = getVideoCapturer(context)
    private var surfaceTextureHelper:SurfaceTextureHelper?=null
    private val mediaConstraint = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo","true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio","true"))
    }

    //call variables
    private lateinit var localSurfaceView: SurfaceViewRenderer
    private lateinit var remoteSurfaceView: SurfaceViewRenderer
    private var localStream: MediaStream? = null
    private var localTrackId = ""
    private var localStreamId = ""
    private var localAudioTrack:AudioTrack?=null
    private var localVideoTrack:VideoTrack?=null

    //screen casting
    private var permissionIntent:Intent?=null
    private var screenCapture:VideoCapturer?=null
    private val localScreenVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private var localScreenShareVideoTrack:VideoTrack?=null

    //installing requirements section
    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setInjectableLogger({ message, severity, label ->
                Log.i("initPeerConnectionFactory WebRTC", "$label: $message")
            }, Logging.Severity.LS_VERBOSE)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(eglBaseContext)
            ).setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBaseContext, true, true
                )
            ).setOptions(PeerConnectionFactory.Options().apply {
                disableNetworkMonitor = false
                disableEncryption = false
            }).createPeerConnectionFactory()
    }

    fun initializeWebrtcClient(
        participantId: String, observer: PeerConnection.Observer
    ) {
        println("initializeWebrtcClient >version 3.0 start>> $participantId")
        localTrackId = "${participantId}_track"
        localStreamId = "${participantId}_stream"
        val peerConnection = createPeerConnection(observer)
        peerConnection?.let {
            peerConnectionList[localId] = it
        }
    }

    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(iceServers, observer)
    }

    //negotiation section
    fun call(userId:String,target:String){
        println("initializeWebrtcClient >version 3.0 call>> $target>>>$peerConnectionList")
        val peerConnection = peerConnectionList[localId]
        peerConnection?.createOffer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        println("initializeWebrtcClient Firebase 0 >>User>offer called>user>>>$userId>>>$target")
                        listener?.onTransferEventToSocket(
                            DataModel(type = DataModelType.Offer,
                                sender = userId,
                                target = target,
                                data = desc?.description)
                        )
                    }
                },desc)
            }

            override fun onSetFailure(p0: String?) {
                super.onSetFailure(p0)
                println("initializeWebrtcClient failure $p0")
            }

            override fun onCreateFailure(p0: String?) {
                super.onCreateFailure(p0)
                println("initializeWebrtcClient failure $p0")
            }
        },mediaConstraint)
    }

    fun answer(userId: String,target:String){
        println("initializeWebrtcClient >version 3.0 answer>> $userId")
        println("initializeWebrtcClient onCreateSuccess >>participantId>answer>$userId >>>$peerConnectionList")
        val peerConnection = peerConnectionList[localId]
        peerConnection?.createAnswer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        println("initializeWebrtcClient onCreateSuccess >>participantId>answer>success")
                        listener?.onTransferEventToSocket(
                            DataModel(
                                type = DataModelType.Answer,
                                sender = userId,
                                target = target,
                                data = desc?.description
                            )
                        )
                    }
                },desc)
            }
        },mediaConstraint)
    }

    fun onRemoteSessionReceived(participantId: String,sessionDescription: SessionDescription){
        println("initializeWebrtcClient >version 3.0 remotereceived>> $participantId")
        peerConnectionList[localId]?.setRemoteDescription(MySdpObserver(),sessionDescription)
    }

    fun addIceCandidateToPeer(participantId:String,iceCandidate: IceCandidate){
        println("initializeWebrtcClient >version 3.0 remoteroffer>> $participantId")
        peerConnectionList[localId]?.addIceCandidate(iceCandidate)
    }

    fun sendIceCandidate(participantId: String,target: String,iceCandidate: IceCandidate){
        addIceCandidateToPeer(participantId,iceCandidate)
        println("initializeWebrtcClient >version 3.0 Ice state>> $participantId")
        listener?.onTransferEventToSocket(
            DataModel(
                type = DataModelType.IceCandidates,
                sender = participantId,
                target = target,
                data = gson.toJson(iceCandidate)
            )
        )
    }

    fun closeConnection(){
        try {
            videoCapture.dispose()
            screenCapture?.dispose()
            localStream?.dispose()
            peerConnectionList[localId]?.close()
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    fun switchCamera(){
        videoCapture.switchCamera(null)
    }

    fun toggleAudio(shouldBeMuted:Boolean){
        if (shouldBeMuted){
            localStream?.removeTrack(localAudioTrack)
        }else{
            localStream?.addTrack(localAudioTrack)
        }
    }

    fun toggleVideo(shouldBeMuted: Boolean){
        try {
            if (shouldBeMuted){
                stopCapturingCamera()
            }else{
                startCapturingCamera(localSurfaceView)
            }
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    //streaming section
    private fun initSurfaceView(view: SurfaceViewRenderer) {
        view.run {
            setMirror(false)
            setEnableHardwareScaler(true)
            init(eglBaseContext, null)
        }
    }
    fun initRemoteSurfaceView(view:SurfaceViewRenderer){
        this.remoteSurfaceView = view
        initSurfaceView(view)
    }
    fun initLocalSurfaceView(userId:String,localView: SurfaceViewRenderer, isVideoCall: Boolean) {
        this.localSurfaceView = localView
        initSurfaceView(localView)
        startLocalStreaming(userId,localView, isVideoCall)
    }
    private fun startLocalStreaming(userId:String,localView: SurfaceViewRenderer, isVideoCall: Boolean) {

        println("initializeWebrtcClient >version 3.0 local stream >>>> $userId")

        localStream = peerConnectionFactory.createLocalMediaStream(localStreamId)
        if (isVideoCall){
            startCapturingCamera(localView)
        }

        localAudioTrack = peerConnectionFactory.createAudioTrack(localTrackId+"_audio",localAudioSource)
        localStream?.addTrack(localAudioTrack)
        peerConnectionList[localId]?.addStream(localStream)
    }
    private fun startCapturingCamera(localView: SurfaceViewRenderer){
        surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name,eglBaseContext
        )

        videoCapture.initialize(
            surfaceTextureHelper,context,localVideoSource.capturerObserver
        )

        videoCapture.startCapture(
            720,480,20
        )

        localVideoTrack = peerConnectionFactory.createVideoTrack(localTrackId+"_video",localVideoSource)
        localVideoTrack?.addSink(localView)
        localStream?.addTrack(localVideoTrack)
    }
    private fun getVideoCapturer(context: Context):CameraVideoCapturer =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it,null)
            }?:throw IllegalStateException()
        }
    private fun stopCapturingCamera(){

        videoCapture.dispose()
        localVideoTrack?.removeSink(localSurfaceView)
        localSurfaceView.clearImage()
        localStream?.removeTrack(localVideoTrack)
        localVideoTrack?.dispose()
    }

    //screen capture section

    fun setPermissionIntent(screenPermissionIntent: Intent) {
        this.permissionIntent = screenPermissionIntent
    }

    fun startScreenCapturing() {
        val displayMetrics = DisplayMetrics()
        val windowsManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowsManager.defaultDisplay.getMetrics(displayMetrics)

        val screenWidthPixels = displayMetrics.widthPixels
        val screenHeightPixels = displayMetrics.heightPixels

        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name,eglBaseContext
        )

        screenCapture = createScreenCapturer()
        screenCapture!!.initialize(
            surfaceTextureHelper,context,localScreenVideoSource.capturerObserver
        )
        screenCapture!!.startCapture(screenWidthPixels,screenHeightPixels,15)

        localScreenShareVideoTrack =
            peerConnectionFactory.createVideoTrack(localTrackId+"_video",localScreenVideoSource)
        localScreenShareVideoTrack?.addSink(localSurfaceView)
        localStream?.addTrack(localScreenShareVideoTrack)
//        peerConnection?.addStream(localStream)

    }

    fun stopScreenCapturing() {
        screenCapture?.stopCapture()
        screenCapture?.dispose()
        localScreenShareVideoTrack?.removeSink(localSurfaceView)
        localSurfaceView.clearImage()
        localStream?.removeTrack(localScreenShareVideoTrack)
        localScreenShareVideoTrack?.dispose()

    }

    private fun createScreenCapturer():VideoCapturer {
        return ScreenCapturerAndroid(permissionIntent, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.d("permissions", "onStop: permission of screen casting is stopped")
            }
        })
    }
    interface WebRTCClientListener {
        fun onTransferEventToSocket(data: DataModel)
    }
}
