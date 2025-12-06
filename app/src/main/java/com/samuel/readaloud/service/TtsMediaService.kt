package com.samuel.readaloud.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.samuel.readaloud.MainActivity
import com.samuel.readaloud.R
import com.samuel.readaloud.domain.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TtsMediaService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var ttsManager: TtsManager

    companion object {
        const val CHANNEL_ID = "tts_playback_channel"
        const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Create Channel IMMEDIATELY
        createNotificationChannel()

        // 2. Start Foreground IMMEDIATELY
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Read Aloud")
            .setContentText("Initializing playback...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // CRITICAL: If startForeground fails, stop immediately to avoid system crash.
            stopSelf()
            return
        }

        // 3. Initialize dependencies synchronously to ensure they are ready for onStartCommand
        try {
            ttsManager = TtsManager.getInstance(applicationContext)

            mediaSession = MediaSessionCompat(this, "TtsMediaService").apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() { ttsManager.togglePlayPause() }
                    override fun onPause() { ttsManager.togglePlayPause() }
                    override fun onStop() {
                        ttsManager.stop()
                        stopSelf()
                    }
                })
                isActive = true
            }

            // 4. Start observing data (this is fine in a coroutine)
            observeTtsManager()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Now mediaSession is guaranteed to be initialized (or service stopped)
        if (::mediaSession.isInitialized) {
            MediaButtonReceiver.handleIntent(mediaSession, intent)
        }
        return START_NOT_STICKY
    }

    private fun observeTtsManager() {
        serviceScope.launch {
            ttsManager.isPlaying.collect { isPlaying ->
                updateMediaSessionState(isPlaying)
                // Use a safe call for title in case flow hasn't emitted yet, or default to app name
                val title = ttsManager.currentTitle.value
                updateNotification(isPlaying, title)
            }
        }
        serviceScope.launch {
            ttsManager.currentTitle.collect { title ->
                updateNotification(ttsManager.isPlaying.value, title)
                updateMediaMetadata(title)
            }
        }
    }

    // ... (Keep the rest of the file: updateMediaSessionState, updateMediaMetadata, updateNotification, buildRealNotification, createNotificationChannel, onDestroy, onBind)

    private fun updateMediaSessionState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .setActions(actions)
                .build()
        )
    }

    private fun updateMediaMetadata(title: String) {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Read Aloud")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                .build()
        )
    }

    private fun updateNotification(isPlaying: Boolean, title: String) {
        val notification = buildRealNotification(isPlaying, title)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildRealNotification(isPlaying: Boolean, title: String): Notification {
        var playPauseIntent: PendingIntent? = null
        try {
            val state = if (isPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY
            playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, state)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingContentIntent = PendingIntent.getActivity(
            this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(mediaStyle)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Reading Aloud")
            .setContentIntent(pendingContentIntent)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)

        if (playPauseIntent != null) {
            builder.addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows controls for text-to-speech playback"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (::mediaSession.isInitialized) {
            mediaSession.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}