package com.guardianshield.app.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.guardianshield.app.data.SupabaseProvider
import com.guardianshield.app.data.models.EvidenceLog
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BurstCaptureManager(private val context: Context) {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null

    companion object {
        private const val TAG = "BurstCapture"
        private const val BURST_COUNT = 3
    }

    /**
     * Capture burst photos from both cameras.
     * Runs front camera first (3 shots), then rear camera (3 shots).
     * All capture is silent — no preview surface needed.
     */
    suspend fun captureBurstPhotos(
        lifecycleOwner: LifecycleOwner,
        sosEventId: String
    ): List<File> = withContext(Dispatchers.Main) {
        val allPhotos = mutableListOf<File>()

        try {
            // Front camera burst
            val frontPhotos = captureFromCamera(
                lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA,
                sosEventId, "front"
            )
            allPhotos.addAll(frontPhotos)

            // Small delay between camera switches
            delay(500)

            // Rear camera burst
            val rearPhotos = captureFromCamera(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                sosEventId, "rear"
            )
            allPhotos.addAll(rearPhotos)
        } catch (e: Exception) {
            Log.e(TAG, "Burst capture failed", e)
        }

        allPhotos
    }

    private suspend fun captureFromCamera(
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector,
        sosEventId: String,
        cameraType: String
    ): List<File> = withContext(Dispatchers.Main) {
        val photos = mutableListOf<File>()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        try {
            val cameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
                cameraProviderFuture.addListener({
                    cont.resumeWith(Result.success(cameraProviderFuture.get()))
                }, ContextCompat.getMainExecutor(context))
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, imageCapture
            )

            // Wait for camera to initialize
            delay(300)

            // Capture burst
            for (i in 1..BURST_COUNT) {
                val photoFile = File(
                    context.filesDir,
                    "evidence/${sosEventId}_${cameraType}_$i.jpg"
                ).apply { parentFile?.mkdirs() }

                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                val savedFile = suspendCancellableCoroutine<File?> { cont ->
                    imageCapture?.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                Log.d(TAG, "Photo saved: ${photoFile.name}")
                                cont.resumeWith(Result.success(photoFile))
                            }

                            override fun onError(exc: ImageCaptureException) {
                                Log.e(TAG, "Photo capture failed: ${exc.message}")
                                cont.resumeWith(Result.success(null))
                            }
                        }
                    )
                }

                savedFile?.let { photos.add(it) }
                delay(200) // Brief delay between shots
            }

            cameraProvider.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Camera $cameraType failed", e)
        }

        photos
    }

    /**
     * Upload captured photos to Supabase Storage.
     */
    suspend fun uploadEvidence(
        photos: List<File>,
        sosEventId: String
    ) = withContext(Dispatchers.IO) {
        // Auth guard — uploads will fail without a valid session
        val currentUser = SupabaseProvider.client.auth.currentUserOrNull()
        if (currentUser == null) {
            Log.e(TAG, "Upload skipped — user not authenticated")
            return@withContext
        }
        Log.d(TAG, "Authenticated as ${currentUser.id}, uploading ${photos.size} photos")

        val bucket = SupabaseProvider.client.storage["evidence-photos"]

        photos.forEach { photo ->
            try {
                val storagePath = "sos/$sosEventId/${photo.name}"
                bucket.upload(storagePath, photo.readBytes())

                // Log to evidence_logs table
                SupabaseProvider.client.postgrest["evidence_logs"].insert(
                    EvidenceLog(
                        sosEventId = sosEventId,
                        type = if (photo.name.contains("front")) "photo_front" else "photo_rear",
                        storagePath = storagePath,
                        fileSize = photo.length()
                    )
                )

                Log.d(TAG, "Uploaded: $storagePath")
            } catch (e: Exception) {
                Log.e(TAG, "UPLOAD FAILED for ${photo.name}: ${e.message}", e)
                Log.e(TAG, "Exception class: ${e::class.simpleName}")
            }
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
