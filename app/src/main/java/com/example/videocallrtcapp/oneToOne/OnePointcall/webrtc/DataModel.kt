package com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc

enum class DataModelType {
    StartAudioCall,StartVideoCall,Offer,Answer,IceCandidates,EndCall
}


/** Only for latest event action */

data class DataModel(
    val sender:String="",
    val target:String="",
    val type: DataModelType,
    val data:String?=null,
    val timeStamp:Long = System.currentTimeMillis()
)

fun DataModel.isValid(): Boolean {
    return System.currentTimeMillis() - this.timeStamp < 60000
}

