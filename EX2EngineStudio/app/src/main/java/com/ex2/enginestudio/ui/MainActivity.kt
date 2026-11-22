package com.ex2.enginestudio.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ex2.enginestudio.R
import com.ex2.enginestudio.audio.EngineAudioEngine
import com.ex2.enginestudio.service.EngineSoundService
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private var service: EngineSoundService? = null
    private var bound = false

    private lateinit var rpmValue: TextView
    private lateinit var speedValue: TextView
    private lateinit var gearValue: TextView
    private lateinit var throttleValue: TextView
    private lateinit var vehicleStatus: TextView
    private lateinit var audioStatus: TextView
    private lateinit var btnStartStop: MaterialButton
    private lateinit var btnInside: MaterialButton
    private lateinit var btnOutside: MaterialButton
    private lateinit var btnBoth: MaterialButton
    private lateinit var outsideWarning: TextView
    private lateinit var intensitySlider: Slider

    private var currentMode = EngineAudioEngine.OutputMode.INSIDE
    private val uiHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUiFromService()
            uiHandler.postDelayed(this, 50)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as EngineSoundService.LocalBinder
            service = local.getService()
            bound = true
            service?.setOutputMode(currentMode)
            updateUiFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rpmValue = findViewById(R.id.rpmValue)
        speedValue = findViewById(R.id.speedValue)
        gearValue = findViewById(R.id.gearValue)
        throttleValue = findViewById(R.id.throttleValue)
        vehicleStatus = findViewById(R.id.vehicleStatus)
        audioStatus = findViewById(R.id.audioStatus)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnInside = findViewById(R.id.btnInside)
        btnOutside = findViewById(R.id.btnOutside)
        btnBoth = findViewById(R.id.btnBoth)
        outsideWarning = findViewById(R.id.outsideWarning)
        intensitySlider = findViewById(R.id.intensitySlider)

        btnStartStop.setOnClickListener {
            val svc = service ?: return@setOnClickListener
            if (svc.isEngineRunning) {
                svc.stopEngine()
                btnStartStop.text = getString(R.string.start_engine)
                audioStatus.text = "Audio: Stopped"
            } else {
                svc.startEngine()
                btnStartStop.text = getString(R.string.stop_engine)
                audioStatus.text = "Audio: Running (Inside)"
            }
        }

        btnInside.setOnClickListener { setMode(EngineAudioEngine.OutputMode.INSIDE) }
        btnOutside.setOnClickListener { setMode(EngineAudioEngine.OutputMode.OUTSIDE) }
        btnBoth.setOnClickListener { setMode(EngineAudioEngine.OutputMode.BOTH) }

        intensitySlider.addOnChangeListener { _, value, _ ->
            service?.setVolume(value / 100f)
        }

        // Start service
        val intent = Intent(this, EngineSoundService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun setMode(mode: EngineAudioEngine.OutputMode) {
        currentMode = mode
        service?.setOutputMode(mode)

        // Visual feedback
        btnInside.isChecked = mode == EngineAudioEngine.OutputMode.INSIDE
        btnOutside.isChecked = mode == EngineAudioEngine.OutputMode.OUTSIDE
        btnBoth.isChecked = mode == EngineAudioEngine.OutputMode.BOTH

        outsideWarning.visibility = if (mode != EngineAudioEngine.OutputMode.INSIDE) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }

        val modeLabel = when (mode) {
            EngineAudioEngine.OutputMode.INSIDE -> "Inside"
            EngineAudioEngine.OutputMode.OUTSIDE -> "Outside (Experimental)"
            EngineAudioEngine.OutputMode.BOTH -> "Both (Experimental)"
        }
        audioStatus.text = "Audio: $modeLabel"
    }

    private fun updateUiFromService() {
        val svc = service ?: return
        val sim = svc.simulator

        rpmValue.text = sim.currentRpm.toInt().toString()
        // Speed will come from vehicle data later; for now show 0 or estimated
        // (the service loop already feeds real speed into the simulator when available)
        gearValue.text = when (sim.currentGear) {
            -1 -> "R"
            0 -> "N"
            else -> sim.currentGear.toString()
        }
        throttleValue.text = "${(sim.throttlePercent * 100).toInt()}%"

        if (svc.isEngineRunning) {
            btnStartStop.text = getString(R.string.stop_engine)
        } else {
            btnStartStop.text = getString(R.string.start_engine)
        }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(updateRunnable)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(updateRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }
}
