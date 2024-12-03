package com.example.videocallrtcapp

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videocallrtcapp.databinding.ActivityMainBinding
import com.example.videocallrtcapp.oneToOne.OnePointcall.presentation.VideoCallAdapter
import com.example.videocallrtcapp.oneToOne.OnePointcall.presentation.VideoCallData
import com.example.videocallrtcapp.oneToOne.OnePointcall.utils.TimeExt.convertToHumanTime
import com.example.videocallrtcapp.oneToOne.OnePointcall.ServiceCallRepository
import com.example.videocallrtcapp.oneToOne.OnePointcall.VideoCallRepository
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    @Inject
    lateinit var videoCallRepository: VideoCallRepository

    @Inject
    lateinit var serviceCallRepository: ServiceCallRepository

    @Inject
    lateinit var gson: Gson

    private var userName = ""

    private lateinit var audioManager :AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        audioManager  = getSystemService(AUDIO_SERVICE) as AudioManager

        init()
    }

    private fun init(){

        intent.getStringExtra("userName")?.let {
            this.userName = it
        }?: kotlin.run {
            finish()
        }

        println("MainActivity all data >>>$userName")

        binding.apply {
            callTitleTv.text = "Meeting Room : 123r56367"
            CoroutineScope(Dispatchers.IO).launch {
                for (i in 0..3600){
                    delay(1000)
                    withContext(Dispatchers.Main){
                        //convert this int to human readable time
                        callTimerTv.text = i.convertToHumanTime()
                    }
                }
            }

            bindLocalView()

           lifecycleScope.launch {
               delay(5000)
               bindRemoteView()
               serviceCallRepository.setupViews(
                   videoCall = true,
                   caller = false,
                   target = userName
               )
           }
        }
    }

    private fun ActivityMainBinding.bindRemoteView() {
        val adapter = VideoCallAdapter(
            isRemoteView = true,
            bindCallToWebClient = {id,surface->
                videoCallRepository.initRemoteSurfaceView(surface)
            }
        )
        remoteView.layoutManager=LinearLayoutManager(this@MainActivity,LinearLayoutManager.VERTICAL,false)
        remoteView.adapter = adapter
        videoCallRepository.uiState.onEach {
            println("remoteView.adapter = adapter >>>$it")
            adapter.submitList(it)
        }.flowWithLifecycle(lifecycle)
            .launchIn(lifecycleScope)
    }

    private fun ActivityMainBinding.bindLocalView() {
        val localAdapter = VideoCallAdapter(
            isRemoteView = false,
            bindCallToWebClient = { id,surface->
                videoCallRepository.initLocalSurfaceView(userName,surface,true)
            }
        )
        localView.layoutManager=LinearLayoutManager(this@MainActivity,LinearLayoutManager.VERTICAL,false)
        localView.adapter = localAdapter
        localAdapter.submitList(listOf(VideoCallData()))
    }

    override fun onBackPressed() {
        super.onBackPressed()
        serviceCallRepository.stopService()
    }

    private fun speakerOn(){
        audioManager.also {
            it.isSpeakerphoneOn = true
            it.mode = AudioManager.MODE_IN_COMMUNICATION
        }
    }
}