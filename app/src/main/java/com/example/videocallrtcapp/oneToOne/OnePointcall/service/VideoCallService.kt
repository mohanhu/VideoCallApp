package com.example.videocallrtcapp.oneToOne.OnePointcall.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.videocallrtcapp.oneToOne.OnePointcall.di.NOTIFICATION_CHANNEL_ID
import com.example.videocallrtcapp.oneToOne.OnePointcall.VideoCallRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VideoCallService @Inject constructor(
) : Service() {

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var videoCallRepository: VideoCallRepository

    @Inject
    lateinit var notificationBuild: NotificationCompat.Builder

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        println("VideoCallService >>>>>>>>> ${intent?.action}")
       intent?.action?.let {
           when(it){
               MainServiceActions.START_SERVICE.name -> {
                   startServiceWithNotification(intent)
               }
               MainServiceActions.SETUP_VIEWS.name->{
                   handleUiSetup(intent)
               }
               MainServiceActions.STOP_SERVICE.name -> {
                   stopForeGroundService()
               }
           }
       }
        return START_STICKY
    }

    private fun handleUiSetup(intent: Intent) {
        val userName = intent.getStringExtra("userName")
//        videoCallRepository.startCall()
    }

    private fun startServiceWithNotification(intent: Intent) {
        val userName = intent.getStringExtra("userName")?:""

        videoCallRepository.setWebInit(userName)
        videoCallRepository.checkUserDetailsCall()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "foreground", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(notificationChannel)
            notificationManager.notify(1,notificationBuild.build())
            startForeground(1, notificationBuild.build())
        }
    }

    private fun stopForeGroundService(){
        videoCallRepository.endCall()
//        videoCallRepository.setWebInit("")
        stopSelf()
    }
}