package com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc

data class MeetingDetails(
    val meetingId :String = "",
    val userDetails: UserDetails
)

data class UserDetails(
    val deviceId:String="" ,// userId also now
    val userStatus:UserStatus= UserStatus.ONLINE,
    val latestEvent : String = "",
)

const val LATEST_EVENT = "latestEvent"
const val MEETING_TABLE = "MEETING_TABLE"
const val USER_TABLE = "USER_TABLE"

enum class UserStatus {
    ONLINE,OFFLINE,IN_CALL
}
