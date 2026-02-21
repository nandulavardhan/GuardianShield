package com.guardianshield.app.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class BurstCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "BurstCapture"
    }

    suspend fun captureBurstSilent(lifecycleOwner: LifecycleOwner) {
        val evidenceDir = File(context.filesDir, "evidence/offline_photos")
        evidenceDir.mkdirs()

        capturePhotos(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, evidenceDir, "offline_back")
        delay(1000)
        capturePhotos(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, evidenceDir, "offline_front")
    }

    private suspend fun capturePhotos(
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector,
        outDir: File,
        baseName: String
    ) = withContext(Dispatchers.Main) {
        try {
            val cameraProvider = getCameraProvider(context)
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

            for (i in 1..3) {
                val file = File(outDir, "${baseName}_${System.currentTimeMillis()}_$i.jpg")
                takeSinglePhoto(imageCapture, file)
                delay(300)
            }

            cameraProvider.unbind(imageCapture)
        } catch (e: Exception) {
            Log.e(TAG, "Failed burst capture for $baseName", e)
        }
    }

    private suspend fun getCameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cont.resume(providerFuture.get())
        }, ContextCompat.getMainExecutor(context))
    }

    private suspend fun takeSinglePhoto(imageCapture: ImageCapture, file: File): Boolean = suspendCancellableCoroutine { cont ->
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    if (cont.isActive) cont.resume(false)
                }
            }
        )
    }
}
