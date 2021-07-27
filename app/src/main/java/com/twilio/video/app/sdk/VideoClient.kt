package com.twilio.video.app.sdk

import android.content.Context
import com.twilio.video.LocalDataTrack
import com.twilio.video.Room
import com.twilio.video.Video

class VideoClient(
    private val context: Context,
    private val connectOptionsFactory: ConnectOptionsFactory
) {

    suspend fun connect(
        identity: String,
        roomName: String,
        roomListener: Room.Listener,
        localDataTrack: LocalDataTrack
    ): Room {

        return Video.connect(
            context,
            connectOptionsFactory.newInstance(identity, roomName, localDataTrack),
            roomListener)
    }
}
