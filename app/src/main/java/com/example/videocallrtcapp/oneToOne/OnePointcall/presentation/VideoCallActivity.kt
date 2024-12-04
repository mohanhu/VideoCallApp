package com.example.videocallrtcapp.oneToOne.OnePointcall.presentation

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videocallrtcapp.databinding.ActivityMainBinding
import com.example.videocallrtcapp.databinding.ActivityVideoCallBinding
import com.example.videocallrtcapp.oneToOne.OnePointcall.service.FirebaseClient
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.DataModel
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.DataModelType
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.MySdpObserver
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.USER_TABLE
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.UserStatus
import com.google.firebase.database.DatabaseReference
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class VideoCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoCallBinding

    private lateinit var videoCallAdapter :VideoCallAdapter

    private lateinit var peerConnectionFactory: PeerConnectionFactory

    private val peerConnections = mutableMapOf<String, CustomPeerConnection>()
    private val rootEglBase = EglBase.create().eglBaseContext

    lateinit var user :String

    private val _uiState = MutableStateFlow(listOf(VideoCallData(
        userId = "Mohan",
        isActivate = false
    ),VideoCallData(
        userId = "Kannan",
        isActivate = false
    )))

    private val uiState = _uiState.asStateFlow()

    lateinit var localVideoTrack: VideoTrack

    lateinit var videoCapturer: VideoCapturer
    lateinit var localSurfaceView: SurfaceViewRenderer

    private val mediaConstraint = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo","true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio","true"))
    }

    @Inject
    lateinit var dbRef : DatabaseReference

    @Inject
    lateinit var firebaseClient: FirebaseClient

    @Inject
    lateinit var gson: Gson

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        user = intent?.getStringExtra("userName")?:""

        println("VideoCallActivity>>>> user onboard >>$user")

        observeUserCurrentStatus()

        initPeerConnectionFactory()

        binding.bindLocalView()

        binding.bindRemoteView()

        observeUserInMeeting()
    }


    private fun observeUserCurrentStatus() {
        firebaseClient.listenForUserStatusChanges {
            println("VideoCallActivity>>>> observeUserCurrentStatus id >>$user>>>${it.type}")
            when(it.type){
                DataModelType.StartAudioCall -> Unit
                DataModelType.StartVideoCall -> Unit
                DataModelType.Offer -> {
                    peerConnections[it.target]?.peerConnection?.let { peer->
                        peer.setRemoteDescription(
                            object : SdpObserver{
                                override fun onCreateSuccess(p0: SessionDescription?) {
                                    println("VideoCallActivity>> Offer ${it.target}>>> done")
                                }

                                override fun onSetSuccess() {
                                    println("VideoCallActivity>> Offer ${it.target}>>> done")
                                }

                                override fun onCreateFailure(p0: String?) {
                                    println("VideoCallActivity>> Offer ${it.target}>>>create fail>$p0")
                                }

                                override fun onSetFailure(p0: String?) {
                                    println("VideoCallActivity>> Offer ${it.target}>>>set fail>$p0")
                                }
                            },
                            SessionDescription(
                                SessionDescription.Type.OFFER,
                                it.data.toString()
                            )
                        )
                    }
                    handleRemoteAnswer(it.sender,it.target)
                }
                DataModelType.Answer ->{
                    println("VideoCallActivity>> Answer ${it.target}>>> start>$peerConnections")
                    peerConnections[it.sender]?.peerConnection?.let { peer->
                        peer.setRemoteDescription(
                            object : SdpObserver{
                                override fun onCreateSuccess(p0: SessionDescription?) {
                                    println("VideoCallActivity>> Answer ${it.target}>>> done")
                                }

                                override fun onSetSuccess() {
                                    println("VideoCallActivity>> Answer ${it.target}>>> done")
                                }

                                override fun onCreateFailure(p0: String?) {
                                    println("VideoCallActivity>> Answer ${it.target}>>>create fail>$p0")
                                }

                                override fun onSetFailure(p0: String?) {
                                    println("VideoCallActivity>> Answer ${it.target}>>>set fail>$p0")
                                }
                            },
                            SessionDescription(
                                SessionDescription.Type.ANSWER,
                                it.data.toString()
                            )
                        )
                    }
                }
                DataModelType.IceCandidates -> {
                    val candidate: IceCandidate? = try {
                        gson.fromJson(it.data.toString(), IceCandidate::class.java)
                    }catch (e:Exception){
                        null
                    }
                    candidate?.let { p->
                        println("VideoCallActivity>> addPeerConnection done second >>>${it.target}>>>$it")
                        peerConnections[it.target]?.peerConnection?.let { peer->
                            peer.addIceCandidate(candidate)
                        }
                    }
                }

                DataModelType.EndCall -> Unit
            }
        }
    }

    private fun observeUserInMeeting() {
        firebaseClient.checkUserListWhoOneAddNew { userId, userDetails ->
            println("VideoCallActivity>>>> user check who one available>>$userDetails")
            if (userDetails.isEmpty()){
                dbRef.child(USER_TABLE).child(userId).child("userStatus")
                    .setValue(UserStatus.IN_CALL.name).addOnCompleteListener {  }

                addPeerConnection(userId)
                println("VideoCallActivity>>>> check local stream is null >>${localVideoTrack.toString()}")
                val localStream = peerConnectionFactory.createLocalMediaStream("${user}_Track")
                localStream.addTrack(localVideoTrack)
                peerConnections[userId]?.peerConnection?.let { peer->
                    peer.addStream(localStream)
                }
                println("VideoCallActivity>>>> user addPeer to This id >>$userId")
            }
            else {
                userDetails.forEach {
                    println("VideoCallActivity>>>> user addPeer to This id >>${it.deviceId}")
                    // Step 1: Create PeerConnection for the peer
                    addPeerConnection(it.deviceId)
                    val localStream = peerConnectionFactory.createLocalMediaStream("${user}_Track")
                    println("VideoCallActivity>>>> check local stream is null >>${localVideoTrack.toString()}")
                    localStream.addTrack(localVideoTrack)
                    peerConnections[it.deviceId]?.peerConnection?.let { peer->
                        peer.addStream(localStream)
                    }

                    // Step 2: Create the offer after setting up PeerConnection
                    createOffer(it.deviceId)
                }
            }
        }
    }

    private fun createOffer(peerId: String) {
        val peerConnection = peerConnections[peerId]?.peerConnection

        // Step 1: Create an offer for the peer connection
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                // Step 2: Set the local description with the SDP offer
                peerConnection.setLocalDescription(this, sdp)

                println("VideoCallActivity>>>> create offer done for user $user>>>$peerId>>>$peerConnections")
                // Step 3: Send the SDP offer to the remote peer via signaling
                firebaseClient.sendMessageToOtherClient(
                    DataModel(type = DataModelType.Offer,
                        sender = user,
                        target = peerId,
                        data = sdp.description)
                ){}
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) {
//                Log.e("WebRTC", "Offer creation failed: $error")
            }

            override fun onSetFailure(error: String) {
//                Log.e("WebRTC", "Failed to set local description: $error")
            }
        }, mediaConstraint)
    }

    private fun handleRemoteAnswer(sender:String,peerId: String) {
        println("VideoCallActivity>>>> $peerId answer $sender raised done $peerConnections")
        val peerConnection = peerConnections[peerId]?.peerConnection
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                println("VideoCallActivity>>>> Answer successfully")
                peerConnection.setLocalDescription(this,p0)
                firebaseClient.sendMessageToOtherClient(
                    DataModel(type = DataModelType.Answer,
                        sender = peerId,
                        target = sender,
                        data = p0?.description)
                ){}

            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {}
        }, mediaConstraint)
    }

    private fun initPeerConnectionFactory() {

        binding.remoteView.run {
            setMirror(false)
            setEnableHardwareScaler(true)
            init(rootEglBase, null)
        }

        // Initialize PeerConnectionFactory
        PeerConnectionFactory.initialize(PeerConnectionFactory
            .InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions())


        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(rootEglBase))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    rootEglBase, true, true
                )
            ).setOptions(
                options.apply {
                    disableNetworkMonitor = false
                    disableEncryption = false
                }
            )
            .createPeerConnectionFactory()
    }

    private fun addPeerConnection(peerId: String) {

        // Creating a STUN server
        val stunServer: PeerConnection.IceServer = PeerConnection.IceServer
            .builder("stun:stun4.l.google.com:19302")
            .createIceServer()

        // Creating a TURN server with credentials
        val turnServer: PeerConnection.IceServer = PeerConnection.IceServer
            .builder("turn:a.relay.metered.ca:443?transport=tcp")
            .setUsername("83eebabf8b4cce9d5dbcb649")
            .setPassword("2D7JvfkOQtBdYW3R")
            .createIceServer()

        val iceServers = listOf(stunServer,turnServer)

        val peerConnectionObserver = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                // Handle ICE candidates
                firebaseClient.findTargetName(peerId){model->
                    println("VideoCallActivity>> addPeerConnection done first >>>$peerId>>>$model")
                    candidate?.let { p->
                        peerConnections[model.target]?.peerConnection?.let { peer->
                            peer.addIceCandidate(candidate)
                        }
                    }
                    firebaseClient.sendMessageToOtherClient(
                    DataModel(type = DataModelType.IceCandidates,
                        sender = model.sender,
                        target = model.target,
                        data = gson.toJson(candidate)
                    )
                ){}}
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                super.onConnectionChange(newState)
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    // 1. change my status to in call
                    firebaseClient.setMeetingRoomId(user,UserStatus.IN_CALL){}
//                     2. clear latest event inside my user section in firebase database
                    firebaseClient.clearLatestEvent(user)
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {

            }

            override fun onAddStream(stream: MediaStream?) {
                // Handle incoming media stream (e.g., add to SurfaceView)
                println("VideoCallActivity>>>> onAddStream id <${stream?.id} >>${stream?.videoTracks?.get(0)}")
                stream?.videoTracks?.get(0)?.addSink(binding.remoteView)
//                try {
//                    val pId = stream?.id?.split('_')?.first()
//                    pId?.let {
//                        _uiState.value.map { state->
//                            if (state.userId == pId){
//                                    state.copy(mediaStream = stream.videoTracks.get(0), isActivate = true)
//                            }
//                            else{
//                                state.copy(isActivate = false)
//                            }
//                        }.also { data->
//                            _uiState.value = data
//                        }
//                    }
//                }catch (e:Exception){
//                    e.printStackTrace()
//                }
            }

            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        }

        val customPeerConnection = CustomPeerConnection(peerConnectionFactory, iceServers, peerConnectionObserver)
        peerConnections[peerId] = customPeerConnection
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dispose all peer connections
        peerConnections.forEach { (_, customPeerConnection) ->
            customPeerConnection.closeConnection()
        }
    }

    private fun ActivityVideoCallBinding.bindRemoteView() {
        videoCallAdapter = VideoCallAdapter(
            isRemoteView = true,
            bindCallToWebClient = { id,surface-> }
        )
//        remoteView.layoutManager= LinearLayoutManager(this@VideoCallActivity, LinearLayoutManager.VERTICAL,false)
//        remoteView.adapter = videoCallAdapter
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED){
//                uiState.collectLatest {
//                    println("VideoCallActivity>> adapter ?>>>>$it")
//                    videoCallAdapter.submitList(it)
//                }
//            }
//        }
    }

    /** Local view Set done*/
    private fun ActivityVideoCallBinding.bindLocalView() {
        println("VideoCallActivity>>> Local view settled done")
        val localAdapter = VideoCallAdapter(
            isRemoteView = false,
            bindCallToWebClient = { id,surface->
                localSurfaceView = surface
                startLocalVideoCapture()
            }
        )
        localView.layoutManager= LinearLayoutManager(this@VideoCallActivity, LinearLayoutManager.VERTICAL,false)
        localView.adapter = localAdapter
        localAdapter.submitList(listOf(VideoCallData()))
    }
    private fun startLocalVideoCapture() {
        // Initialize the local video renderer
        localSurfaceView.init(rootEglBase, null)
        localSurfaceView.setMirror(false)

        // Create video source and video track
        val videoSource = peerConnectionFactory.createVideoSource(false)
        // Use Camera2Enumerator if available
        createCameraCapturer(Camera1Enumerator(false))?.let {
            videoCapturer = it
        }

        // Start video capturer
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase)
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        videoCapturer.startCapture(1280, 720, 30)

        // Create local video track
        localVideoTrack = peerConnectionFactory.createVideoTrack("${user}_Video", videoSource)

        // Set the local renderer for the video track
        localVideoTrack.addSink(localSurfaceView)
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

}
