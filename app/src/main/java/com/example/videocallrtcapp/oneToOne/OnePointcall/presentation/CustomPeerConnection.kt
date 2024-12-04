package com.example.videocallrtcapp.oneToOne.OnePointcall.presentation

import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import javax.inject.Singleton

@Singleton
class CustomPeerConnection(
    private val peerConnectionFactory: PeerConnectionFactory,
    private val iceServers: List<PeerConnection.IceServer>,
    private val observer: PeerConnection.Observer
) {
    lateinit var peerConnection: PeerConnection

    init {
        createPeerConnection()
    }

    private fun createPeerConnection() {
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, observer) ?: return
    }

    // Additional methods for SDP offer/answer, ICE candidate handling, etc.
    fun closeConnection() {
        peerConnection.dispose()
    }
}
