package com.example.videocallrtcapp.oneToOne.OnePointcall

import android.content.Context
import com.example.videocallrtcapp.oneToOne.OnePointcall.presentation.VideoCallData
import com.example.videocallrtcapp.oneToOne.OnePointcall.service.FirebaseClient
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.DataModel
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.DataModelType
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.MyPeerObserver
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.USER_TABLE
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.UserDetails
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.UserStatus
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.WebRTCClient
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.isValid
import com.google.firebase.database.DatabaseReference
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoCallRepository @Inject constructor(
    @ApplicationContext val context: Context,
    private val firebaseClient: FirebaseClient,
    private val webRTCClient: WebRTCClient,
    private var gson: Gson,
    private val dbRed: DatabaseReference,
) : WebRTCClient.WebRTCClientListener {

//    private var targetName  = ""
    private lateinit var remoteSurfaceView: SurfaceViewRenderer

    private val userList = listOf(
        VideoCallData(userId = "Mohan"),
        VideoCallData(userId = "Kannan"),
        VideoCallData(userId = "Naveen"),
    )

    private val _uiState = MutableStateFlow(userList)
    val uiState = _uiState.asStateFlow()

    fun setMeetingRoomId(meetingId:String, userStatus: UserStatus, connect:()->Unit){
        firebaseClient.setMeetingRoomId(meetingId,userStatus,connect)
    }

    fun checkUserDetailsCall() {
        firebaseClient.listenForUserStatusChanges { dataModel ->
            println("initializeWebrtcClient user details >>>user>${dataModel.sender}>${dataModel.type}")
            when(dataModel.type){
                DataModelType.StartAudioCall -> {
                }
                DataModelType.StartVideoCall -> {
                }
                DataModelType.Offer -> {
                    println("initializeWebrtcClient onCreateSuccess >>participantId>Offer>received>>>${dataModel.sender}>${dataModel.target}")
                    webRTCClient.onRemoteSessionReceived(
                        dataModel.target,
                        SessionDescription(
                            SessionDescription.Type.OFFER,
                            dataModel.data.toString()
                        )
                    )
                    webRTCClient.answer(dataModel.target,dataModel.sender)
                }
                DataModelType.Answer -> {
                    println("initializeWebrtcClient onCreateSuccess >>participantId>answer>received>>>${dataModel.sender}>${dataModel.target}")
                    webRTCClient.onRemoteSessionReceived(
                        dataModel.target,
                        SessionDescription(
                            SessionDescription.Type.ANSWER,
                            dataModel.data.toString()
                        )
                    )
                }
                DataModelType.IceCandidates -> {
                    println("initializeWebrtcClient onCreateSuccess >>participantId>IceCandidates>received>>>${dataModel.sender}>${dataModel.target}")
                    val candidate: IceCandidate? = try {
                        gson.fromJson(dataModel.data.toString(), IceCandidate::class.java)
                    }catch (e:Exception){
                        null
                    }
                    candidate?.let {
                        webRTCClient.addIceCandidateToPeer(dataModel.sender,it)
                    }
                }
                DataModelType.EndCall -> {
                    endCall()
                }
            }
        }
    }

    fun checkUserListWhoOneAddNew() {
        firebaseClient.checkUserListWhoOneAddNew { userId, whoAvailable ->
            println("checkUserListWhoOneAddNew Firebase 0 >>User>>$userId")
            if (whoAvailable.isNotEmpty()) {
                whoAvailable.forEach { user ->
                    try {
                        // Only one sendConnectionRequest might be needed in certain use cases
                        sendConnectionRequest(userName = userId, targetName = user.deviceId) {
                            // Handle callback if needed
                        }
                        // If both directions are needed, keep both requests
                        sendConnectionRequest(userName = user.deviceId, targetName = userId) {
                            // Handle callback if needed
                        }

                        startCall(userId, user.deviceId)
                    } catch (e: Exception) {
                        // Log or handle the exception properly
                        e.printStackTrace()
                    }
                }
            } else {
                println("No available users found.")
            }
        }
    }

    fun createPeerConnectionInit(userName: String) {
        webRTCClient.createPeerConnection(
            object : MyPeerObserver() {

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    println("webRTCClient.initializeWebrtcClient >>>>$p0>")
                    try {
                        val pId = p0?.id?.split('_')?.first()
                        val pState = p0?.videoTracks?.get(0)?.state()
                        pId?.let {
                            _uiState.value.map { state->
                                if (state.userId == pId && (state.mediaStream ?: MediaStreamTrack.State.LIVE) == MediaStreamTrack.State.LIVE
                                ){
                                    state.copy(mediaStream = p0.videoTracks.get(0), isActivate = true)
                                }
                                else{
                                    state.copy(isActivate = false)
                                }
                            }.also { data->
                                _uiState.value = data
                            }
                        }
                    }catch (e:Exception){
                        e.printStackTrace()
                    }
                }

                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    p0?.let {
                        firebaseClient.findTargetName(userName){target->
                            println("initializeWebrtcClient onIceCandidate >>$userName> $target")
                            webRTCClient.sendIceCandidate(userName,target, it)
                        }
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    super.onConnectionChange(newState)
                    if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                        // 1. change my status to in call
                        firebaseClient.setMeetingRoomId(userName,UserStatus.IN_CALL){}
//                     2. clear latest event inside my user section in firebase database
                        clearLatestEvent(userName)
                    }
                }
            }
        )
    }

    fun setWebInit(userName: String) {
        println("initializeWebrtcClient >>> 3.0 >>>setWebInit >>$userName")
        webRTCClient.listener = this
        webRTCClient.initializeWebrtcClient(participantId = userName)
    }

    fun initLocalSurfaceView(userId:String,view: SurfaceViewRenderer, isVideoCall: Boolean) {
        webRTCClient.initLocalSurfaceView(userId,view, isVideoCall)
    }

    fun initRemoteSurfaceView(view: SurfaceViewRenderer) {
        webRTCClient.initRemoteSurfaceView(view)
        this.remoteSurfaceView = view
    }

    private fun startCall(userName: String, target:String) {
        webRTCClient.call(userName,target)
    }

    fun endCall() {
        webRTCClient.closeConnection()
    }

    fun sendConnectionRequest(userName: String,targetName:String,success: (Boolean) -> Unit) {
        firebaseClient.sendMessageToOtherClient(
            DataModel(
                type = DataModelType.StartVideoCall ,
                sender = userName,
                target = targetName
            ), success
        )
    }

    fun clearLatestEvent(userName: String){
//        firebaseClient.clearLatestEvent(userName = userName)
    }

    override fun onTransferEventToSocket(data: DataModel) {
        firebaseClient.sendMessageToOtherClient(
            data
        ){}
    }
}