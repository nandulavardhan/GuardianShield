package com.guardianshield.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class VideoCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "VideoCaptureParams"
    }

    @SuppressLint("MissingPermission")
    suspend fun recordVideoSilent(lifecycleOwner: LifecycleOwner, durationMillis: Long) {
        val moviesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
        val evidenceDir = File(moviesDir, "GuardianShield")
        evidenceDir.mkdirs()
        
        val videoFile = File(evidenceDir, "offline_video_${System.currentTimeMillis()}.mp4")

        withContext(Dispatchers.Main) {
            try {
                val cameraProvider = getCameraProvider(context)
                
                val qualitySelector = QualitySelector.from(Quality.SD)
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture
                )

                val fileOutputOptions = FileOutputOptions.Builder(videoFile).build()

                Log.d(TAG, "Starting video recording for ${durationMillis}ms")

                var recording: Recording? = null
                
                // Suspend until recording is started, then we will delay and stop
                suspendCancellableCoroutine<Unit> { cont ->
                    val pendingRecording = videoCapture.output
                        .prepareRecording(context, fileOutputOptions)
                    
                    recording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                            when (recordEvent) {
                                is VideoRecordEvent.Start -> {
                                    Log.d(TAG, "Video recording started")
                                    if (cont.isActive) cont.resume(Unit)
                                }
                                is VideoRecordEvent.Finalize -> {
                                    if (!recordEvent.hasError()) {
                                        Log.d(TAG, "Video recording finished successfully: \${recordEvent.outputResults.outputUri}")
                                    } else {
                                        Log.e(TAG, "Video recording error: \${recordEvent.error}")
                                    }
                                }
                            }
                        }
                }

                // Strobe the flashlight natively through CameraX while recording
                val strobeJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    var isTorchOn = false
                    while (true) {
                        try {
                            isTorchOn = !isTorchOn
                            camera.cameraControl.enableTorch(isTorchOn)
                            Thread.sleep(200)
                        } catch (e: Exception) {
                            Thread.sleep(200)
                        }
                    }
                }

                // Wait for the specified duration (e.g., 10 seconds)
                delay(durationMillis)
                
                // Stop the recording and strobe
                strobeJob.cancel()
                try { camera.cameraControl.enableTorch(false) } catch (e: Exception) {}
                recording?.stop()
                Log.d(TAG, "Video recording stopped")

                // Give it a brief moment to finalize the file out completely before unbinding
                delay(1000)
                cameraProvider.unbindAll()

            } catch (e: Exception) {
                Log.e(TAG, "Failed video capture", e)
            }
        }
    }

    private suspend fun getCameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cont.resume(providerFuture.get())
        }, ContextCompat.getMainExecutor(context))
    }
}
