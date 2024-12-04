package com.example.videocallrtcapp.oneToOne.OnePointcall.service

import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.DataModel
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.LATEST_EVENT
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.USER_TABLE
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.UserDetails
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.UserStatus
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseClient @Inject constructor(
    private val dbRef: DatabaseReference,
    private val gson: Gson
) {

    private var userId = ""

    private fun setUserId(user:String){
        userId = user
    }

    fun setMeetingRoomId(meetingId: String,userStatus: UserStatus,connect:()->Unit) {
        dbRef.child(USER_TABLE).child(meetingId).setValue(
            UserDetails(
                deviceId = meetingId,
                userStatus = userStatus,
            )
        ).addOnCompleteListener {
            connect.invoke()
            setUserId(meetingId)
        }
    }

    fun findTargetName(userName: String,target:(String)->Unit) {
        dbRef.child(USER_TABLE).child(userName).get().addOnCompleteListener {
//            try {
                val user = it.result.getValue(UserDetails::class.java)
                println("initializeWebrtcClient findTargetName $userName >>>>${it.result}")
                println("initializeWebrtcClient findTargetName $userName >>>>$user")
                val latest = gson.fromJson(user?.latestEvent.toString(), DataModel::class.java)
                target.invoke(latest.sender)
//            }
//            catch (e:Exception){}
        }
    }

    fun checkUserListWhoOneAddNew(userList : (user:String,List<UserDetails>)->Unit) {
        val userStatusRef = dbRef.child(USER_TABLE)
        var userDetails = mutableListOf<UserDetails>()
        userStatusRef.get().addOnCompleteListener { snap->
            try {
                val user = snap.result
                for (u in user.children) {
                    val data = u.getValue(UserDetails::class.java)
                    data?.let { userDetails.add(it) }
                }
                userDetails = userDetails.filter { it.deviceId != userId }
                    .filter { it.userStatus == UserStatus.IN_CALL }.toMutableList()
                userList.invoke(userId,userDetails)
            }catch (_:Exception){
            }
            println("checkUserListWhoOneAddNew status >>>$userId> $userDetails")
            if (userDetails.isEmpty() || !snap.result.exists()){
                userList.invoke(userId, listOf())
                dbRef.child(USER_TABLE).child(userId).child("userStatus")
                    .setValue(UserStatus.IN_CALL)
                    .addOnCompleteListener {  }
            }
        }
    }

    fun listenForUserStatusChanges(callBack: (DataModel) -> Unit) {
        val userStatusRef = dbRef.child(USER_TABLE).child(userId).child(LATEST_EVENT)
        // Adding a ValueEventListener to listen for changes in userStatus
        userStatusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val userStatus = snapshot.getValue(String::class.java) ?: ""
                    println("UserStatus for $userId has changed: $userStatus")
                    val event = try {
                            gson.fromJson(snapshot.value.toString(), DataModel::class.java)
                        }catch (e:Exception){
                            e.printStackTrace()
                            null
                        }
                        event?.let {
                            callBack.invoke(it)
                        }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                println("Error: ${error.message}")
            }
        })
    }


    /**Only Target user will receive this message*/
    fun sendMessageToOtherClient(message: DataModel, success: (Boolean) -> Unit) {
        println("initializeWebrtcClient sendMessageToOtherClient >>>$message>>>")
        val db = dbRef.child(USER_TABLE).child(message.target?:"").child(LATEST_EVENT)
        val convertedMessage = gson.toJson(message)
        db.setValue(convertedMessage)
            .addOnCompleteListener {
                success.invoke(true)
            }.addOnFailureListener {
                success.invoke(false)
            }
    }

    fun clearLatestEvent(userName: String) {
        dbRef.child(USER_TABLE).child(userName).child(LATEST_EVENT).setValue(null)
    }
}