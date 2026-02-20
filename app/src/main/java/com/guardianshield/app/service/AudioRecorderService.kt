package com.guardianshield.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

class AudioRecorderService : Service() {

    private var recorder: MediaRecorder? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "AudioRecorder"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_OUTPUT_PATH = "output_path"

        fun recordClip(context: Context, durationMs: Long = 5000, outputPath: String): Intent {
            return Intent(context, AudioRecorderService::class.java).apply {
                putExtra(EXTRA_DURATION_MS, durationMs)
                putExtra(EXTRA_OUTPUT_PATH, outputPath)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val duration = intent?.getLongExtra(EXTRA_DURATION_MS, 5000) ?: 5000
        val outputPath = intent?.getStringExtra(EXTRA_OUTPUT_PATH)
            ?: "${filesDir}/evidence/audio_${System.currentTimeMillis()}.3gp"

        serviceScope.launch {
            try {
                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()

                recorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(outputPath)
                    setMaxDuration(duration.toInt())
                    prepare()
                    start()
                }

                Log.d(TAG, "Recording started: $outputPath")
                delay(duration)

                recorder?.apply {
                    stop()
                    release()
                }
                recorder = null
                Log.d(TAG, "Recording complete: $outputPath")
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            recorder?.apply { stop(); release() }
        } catch (_: Exception) {}
        serviceScope.cancel()
    }
}
