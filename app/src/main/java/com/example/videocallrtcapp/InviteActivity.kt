package com.example.videocallrtcapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.videocallrtcapp.databinding.ActivityInviteBinding
import com.example.videocallrtcapp.oneToOne.OnePointcall.ServiceCallRepository
import com.example.videocallrtcapp.oneToOne.OnePointcall.VideoCallRepository
import com.example.videocallrtcapp.oneToOne.OnePointcall.presentation.VideoCallActivity
import com.example.videocallrtcapp.oneToOne.OnePointcall.webrtc.UserStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class InviteActivity : AppCompatActivity() {

    private val binding: ActivityInviteBinding by lazy { ActivityInviteBinding.inflate(layoutInflater) }

    @Inject
    lateinit var videoCallRepository: VideoCallRepository

    @Inject
    lateinit var serviceCallRepository: ServiceCallRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.CAMERA
        ),1)

        var userName = "Kannan"


        binding.evJoinId.setOnClickListener {
            userName ="Mohan"
            videoCallRepository.setMeetingRoomId("Mohan",UserStatus.ONLINE){}
        }

        binding.evKannanId.setOnClickListener {
            userName ="Kannan"
            videoCallRepository.setMeetingRoomId("Kannan",UserStatus.ONLINE){}
        }

        binding.joinMeeting.setOnClickListener {
           lifecycleScope.launch {
               startActivity(Intent(this@InviteActivity,VideoCallActivity::class.java).apply {
                   putExtra("userName",userName)
               })
           }
        }

//        binding.createMeeting.setOnClickListener {
//            videoCallRepository.setMeetingRoomId(userName){
//                serviceCallRepository.startService(meetingId = userName)
//                videoCallRepository.sendConnectionRequest(userName){
//                    startActivity(Intent(this@InviteActivity,MainActivity::class.java).apply {
//                        putExtra("meetingId",userName)
//                        putExtra("isVideoCall",true)
//                        putExtra("isCaller",true)
//                    })
//                }
//            }
//        }
    }

//    private fun listUpdates() {
//        if (currentSecond>20){
//            job.cancel()
//            return
//        }
//        job = CoroutineScope(Dispatchers.IO).launch {
//            println("List refresh between each 5 seconds>>>>$currentSecond")
//            delay(5000)
//            currentSecond+=5
//            listUpdates()
//        }
//    }

    override fun onStop() {
        super.onStop()
    }
}