package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AmbientRecognitionService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var isRecording = false
    private lateinit var db: SongDatabase
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastDetectedSongTitle: String? = null
    private var lastDetectedSongArtist: String? = null
    private var lastDetectedSongTime: Long = 0L

    companion object {
        const val CHANNEL_ID = "AmbientSongChannel"
        const val NOTIFICATION_ID = 1
        var isServiceRunning = false
        val serviceStatus = kotlinx.coroutines.flow.MutableStateFlow("Idle")
    }

    override fun onCreate() {
        super.onCreate()
        db = SongDatabase.getDatabase(applicationContext)
        createNotificationChannel()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AmbientSong::BackgroundWakeLock")
        wakeLock?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        isServiceRunning = true

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startRecognitionLoop()

        return START_STICKY
    }

    private var recognitionJob: Job? = null

    private fun startRecognitionLoop() {
        recognitionJob?.cancel()
        recognitionJob = serviceScope.launch {
            while (isActive && isServiceRunning) {
                val tempFile = File(cacheDir, "ambient_audio.mp4")
                var recorder: MediaRecorder? = null
                try {
                    // Record audio
                    serviceStatus.value = "Listening..."
                    isRecording = true
                    recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(this@AmbientRecognitionService)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }

                        recorder.apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setAudioEncodingBitRate(256000)
                            setAudioSamplingRate(48000)
                            setOutputFile(tempFile.absolutePath)
                            prepare()
                            start()
                        }

                        Log.d("AmbientSong", "Recording started...")
                        delay(15000) // record for 15 seconds
                        Log.d("AmbientSong", "Recording stopped.")

                    try {
                        recorder?.stop()
                    } catch (e: RuntimeException) {
                        // Thrown if no valid audio data has been received
                        Log.e("AmbientSong", "MediaRecorder.stop() failed", e)
                    }
                    try {
                        recorder?.release()
                    } catch (e: Exception) {
                        Log.e("AmbientSong", "MediaRecorder.release() failed", e)
                    }
                    recorder = null
                    isRecording = false

                    // Identify song
                    serviceStatus.value = "Identifying..."
                    val result = AuddClient.identifySong(tempFile)
                    if (result != null) {
                        Log.d("AmbientSong", "Found song: ${result.title} by ${result.artist}")
                        serviceStatus.value = "Found: ${result.title}"
                        val now = System.currentTimeMillis()
                        
                        val isDuplicate = result.title.equals(lastDetectedSongTitle, ignoreCase = true) &&
                                          result.artist.equals(lastDetectedSongArtist, ignoreCase = true)

                        if (isDuplicate) {
                            Log.d("AmbientSong", "Song is same as last detected. Skipping duplicate notification.")
                        } else {
                            handleFoundSong(result)
                            lastDetectedSongTitle = result.title
                            lastDetectedSongArtist = result.artist
                            lastDetectedSongTime = now
                        }
                        // Wait a short time before next listen
                        serviceStatus.value = "Waiting..."
                        delay(5000) // 5 seconds
                    } else {
                        Log.d("AmbientSong", "No song found.")
                        // Wait before trying again
                        serviceStatus.value = "No match. Waiting..."
                        delay(5000) // 5 seconds
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("AmbientSong", "Error in recognition loop: ${e.message}", e)
                    isRecording = false
                    if (e.message?.contains("Rate Limit") == true) {
                        serviceStatus.value = "API Limit Reached. Waiting..."
                        delay(15000) // wait longer if rate limited
                    } else {
                        serviceStatus.value = "Error. Retrying..."
                        delay(5000) // 5 seconds backoff on error
                    }
                } finally {
                    try {
                        recorder?.release()
                    } catch (e: Exception) {
                        Log.e("AmbientSong", "Failed to release MediaRecorder", e)
                    }
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            }
        }
    }

    private suspend fun handleFoundSong(result: AuddClient.SongResult) {
        // Insert to DB and keep only latest 10
        db.songDao().insertSong(Song(title = result.title, artist = result.artist, artworkUrl = result.artworkUrl))
        db.songDao().keepOnlyLatest10()

        // Fetch artwork as a bitmap if available
        var artworkBitmap: android.graphics.Bitmap? = null
        if (!result.artworkUrl.isNullOrEmpty()) {
            try {
                val imageLoader = ImageLoader(this)
                val request = ImageRequest.Builder(this)
                    .data(result.artworkUrl)
                    .allowHardware(false) // required to convert to Bitmap safely
                    .build()
                val imageResult = imageLoader.execute(request)
                if (imageResult is SuccessResult) {
                    artworkBitmap = imageResult.drawable.toBitmap()
                }
            } catch (e: Exception) {
                Log.e("AmbientService", "Failed to load artwork bitmap", e)
            }
        }

        // Show Notification
        val searchUrl = "https://www.google.com/search?q=${Uri.encode(result.title + " " + result.artist)}"
        val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
        val pendingIntent = PendingIntent.getActivity(
            this, 
            result.title.hashCode(), 
            searchIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notifManager = getSystemService(NotificationManager::class.java)
        val songNotificationBuilder = NotificationCompat.Builder(this, "SongFoundChannel")
            .setContentTitle(result.title)
            .setContentText(result.artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (artworkBitmap != null) {
            songNotificationBuilder.setLargeIcon(artworkBitmap)
        }

        val songNotification = songNotificationBuilder.build()

        notifManager.notify(result.title.hashCode(), songNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Foreground service channel (min importance)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Music Recognition",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Runs in the background to detect music"
            }
            manager.createNotificationChannel(channel)

            // Song found channel (high importance)
            val foundChannel = NotificationChannel(
                "SongFoundChannel",
                "Identified Songs",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a new song is identified"
            }
            manager.createNotificationChannel(foundChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        isServiceRunning = false
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
