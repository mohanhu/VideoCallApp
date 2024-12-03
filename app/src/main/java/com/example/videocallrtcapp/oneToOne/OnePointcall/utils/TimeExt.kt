package com.example.videocallrtcapp.oneToOne.OnePointcall.utils

object TimeExt {
    fun Int.convertToHumanTime() : String{
        val seconds = this%60
        val minutes = this/60
        val secondsString = if (seconds<10) "0$seconds" else "$seconds"
        val minutesString = if (minutes < 10) "0$minutes" else "$minutes"
        return "$minutesString:$secondsString"
    }
}