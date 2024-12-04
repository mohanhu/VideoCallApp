package com.example.videocallrtcapp.oneToOne.OnePointcall.presentation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.videocallrtcapp.databinding.VideoRemoteSurfaceViewBinding
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.time.Instant

class VideoCallAdapter(
    val isRemoteView:Boolean = false,
    val bindCallToWebClient:(String,SurfaceViewRenderer)->Unit
) : ListAdapter<VideoCallData, VideoCallAdapter.ViewHolder>(DIFFER_VIDEO_CALL){

    val eglBase: EglBase = EglBase.create()

    inner class ViewHolder(private val binding:VideoRemoteSurfaceViewBinding):RecyclerView.ViewHolder(binding.root) {
        fun bind(data: VideoCallData){
            if (isRemoteView) {
                if (data.isActivate && data.mediaStream != null) {
                    binding.remoteView.run {
                        // Initialize if not done
                        init(eglBase.eglBaseContext, null)
                        setEnableHardwareScaler(true)
                        setMirror(false)

                        // Attach the media stream (VideoTrack)
                        data.mediaStream.addSink(this)
                    }
                }
            }
            else{
                bindCallToWebClient.invoke(data.userId,binding.remoteView)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(binding = VideoRemoteSurfaceViewBinding.inflate(LayoutInflater.from(parent.context),parent,false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
    }
}

object DIFFER_VIDEO_CALL :DiffUtil.ItemCallback<VideoCallData>(){
    override fun areItemsTheSame(oldItem: VideoCallData, newItem: VideoCallData): Boolean {
        return oldItem.userId == newItem.userId
    }

    override fun areContentsTheSame(oldItem: VideoCallData, newItem: VideoCallData): Boolean {
        return oldItem == newItem
    }

}


data class VideoCallData(
    val userId:String = Instant.now().toEpochMilli().toString(),
    val mediaStream: VideoTrack?= null,
    val isActivate : Boolean = false
)