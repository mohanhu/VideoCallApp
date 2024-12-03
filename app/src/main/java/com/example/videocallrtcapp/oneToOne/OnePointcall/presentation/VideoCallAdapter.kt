package com.example.videocallrtcapp.oneToOne.OnePointcall.presentation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.videocallrtcapp.databinding.VideoRemoteSurfaceViewBinding
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.time.Instant

class VideoCallAdapter(
    val isRemoteView:Boolean = false,
    val bindCallToWebClient:(SurfaceViewRenderer)->Unit
) : ListAdapter<VideoCallData, VideoCallAdapter.ViewHolder>(DIFFER_VIDEO_CALL){

    inner class ViewHolder(private val binding:VideoRemoteSurfaceViewBinding):RecyclerView.ViewHolder(binding.root) {
        fun bind(data: VideoCallData){
            if (isRemoteView){
                if (data.isActivate){
                    bindCallToWebClient.invoke(binding.remoteView)
                    data.mediaStream?.addSink(binding.remoteView)
                }
            }
            else{
                bindCallToWebClient.invoke(binding.remoteView)
            }
//            binding.remoteView.init(data.eglContext, null)
            // Bind the remote video stream to the SurfaceViewRenderer
//            data.mediaStream?.videoTracks?.get(0)?.addSink(binding.remoteView)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(binding = VideoRemoteSurfaceViewBinding.inflate(LayoutInflater.from(parent.context),parent,false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
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