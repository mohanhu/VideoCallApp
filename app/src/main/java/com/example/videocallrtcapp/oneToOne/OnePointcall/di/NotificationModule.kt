package com.example.videocallrtcapp.oneToOne.OnePointcall.di

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.videocallrtcapp.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped

@Module
@InstallIn(ServiceComponent::class)
object NotificationModule {

    @Provides
    @ServiceScoped
    fun provideNotificationBuilder(
        @ApplicationContext context: Context
    ) :NotificationCompat.Builder =
        NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)

    @Provides
    @ServiceScoped
    fun provideNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManager = context.getSystemService(NotificationManager::class.java)

}

const val NOTIFICATION_CHANNEL_ID = "1"