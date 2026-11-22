package com.ex2.enginestudio.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ex2.enginestudio.R
import com.ex2.enginestudio.audio.EngineAudioEngine
import com.ex2.enginestudio.engine.EngineSimulator
import com.ex2.enginestudio.engine.EngineProfiles
import com.ex2.enginestudio.ui.MainActivity
import com.ex2.enginestudio.vehicle.VehicleDataProvider
import kotlinx.coroutines.*

class EngineSoundService : Service() {

    companion object {
        private const val TAG = "EngineSoundService"
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "engine_sound_channel"
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val simulator = EngineSimulator(EngineProfiles.FERRARI_V8)
    private lateinit var audioEngine: EngineAudioEngine
    private lateinit var vehicleData: VehicleDataProvider

    @Volatile
    var isEngineRunning = false
        private set

    inner class LocalBinder : Binder() {
        fun getService(): EngineSoundService = this@EngineSoundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        audioEngine = EngineAudioEngine(simulator)
        vehicleData = VehicleDataProvider(this)
        vehicleData.connect()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Engine Studio ready"))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopEngine()
        vehicleData.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    fun startEngine() {
        if (isEngineRunning) return
        isEngineRunning = true
        simulator.start()
        audioEngine.start()
        startSimulationLoop()
        updateNotification("Engine running")
        Log.i(TAG, "Engine started")
    }

    fun stopEngine() {
        if (!isEngineRunning) return
        isEngineRunning = false
        simulator.stop()
        audioEngine.stop()
        updateNotification("Engine stopped")
        Log.i(TAG, "Engine stopped")
    }

    fun setOutputMode(mode: EngineAudioEngine.OutputMode) {
        audioEngine.setOutputMode(mode)
    }

    fun setVolume(volume01: Float) {
        audioEngine.masterVolume = volume01.coerceIn(0f, 1f)
    }

    fun setProfile(profileId: String) {
        val profile = EngineProfiles.ALL.find { it.id == profileId } ?: return
        simulator.setProfile(profile)
    }

    private fun startSimulationLoop() {
        scope.launch {
            while (isEngineRunning) {
                val snap = vehicleData.getSnapshot()
                simulator.update(
                    speedKmh = snap.speedKmh,
                    accelPedal = snap.accelPedal,
                    brakeActive = snap.brakeActive,
                    inReverse = snap.gear == -1
                )
                delay(33) // ~30 Hz
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Engine Sound",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "EX2 Engine Studio playback"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EX2 Engine Studio")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
