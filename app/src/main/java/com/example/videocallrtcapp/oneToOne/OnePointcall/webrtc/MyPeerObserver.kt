package com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc

import android.util.Log
import org.webrtc.*

open class MyPeerObserver : PeerConnection.Observer {
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        Log.d("MyPeerObserver WebRTC", "Peer connection is $p0.")
    }

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
        when (newState) {
            PeerConnection.IceConnectionState.CONNECTED -> {
                // Connection is successful
                Log.d("MyPeerObserver WebRTC", "Peer connection is connected.")
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                // Connection got disconnected
                Log.d("MyPeerObserver WebRTC", "Peer connection is disconnected.")
            }
            PeerConnection.IceConnectionState.FAILED -> {
                // Connection has failed
                Log.d("MyPeerObserver WebRTC", "Peer connection has failed.")
                // Handle reconnection logic or show a failure message
            }
            PeerConnection.IceConnectionState.CLOSED -> {
                // Connection has been closed
                Log.d("MyPeerObserver WebRTC", "Peer connection is closed.")
            }
            else -> {
                // Other states: NEW, CHECKING, COMPLETED, etc.
                Log.d("MyPeerObserver WebRTC", "Peer connection state changed: $newState")
            }
        }
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
    }

    override fun onIceCandidate(p0: IceCandidate?) {
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
    }

    override fun onAddStream(p0: MediaStream?) {
    }

    override fun onRemoveStream(p0: MediaStream?) {
    }

    override fun onDataChannel(p0: DataChannel?) {
    }

    override fun onRenegotiationNeeded() {
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
    }
}