package com.synapselink.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.synapselink.app.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null // For real-time frame analysis
    private var audioRecord: AudioRecord? = null
    private var isAudioRecording = false
    private var audioRecordingJob: Job? = null

    companion object {
        private const val TAG = "SynapseLinkApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()

        // Audio configuration for raw biological signal capture
        private const val AUDIO_SAMPLE_RATE = 44100 // Hz, standard for high quality audio
        private const val AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_BUFFER_SIZE_FACTOR = 2 // Factor for AudioRecord buffer
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera and audio permissions
        if (allPermissionsGranted()) {
            startSynapseLinkSensors()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        viewBinding.toggleProcessingButton.setOnClickListener {
            if (isAudioRecording) {
                stopProcessing()
            } else {
                startProcessing()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startSynapseLinkSensors()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startSynapseLinkSensors() {
        // Initialize camera and audio recording components
        startCamera()
        updateStatus("Camera and Microphone Initialized. Ready for Processing.")
    }

    private fun startProcessing() {
        viewBinding.toggleProcessingButton.text = "Stop Processing"
        startAudioRecording()
        // Here we would also activate the ImageAnalysis UseCase if not already active
        // and start feeding data into our IAI-IPS (Logos & Pathos)
        updateStatus("Processing biological signals...")
    }

    private fun stopProcessing() {
        viewBinding.toggleProcessingButton.text = "Start Processing"
        stopAudioRecording()
        // Deactivate ImageAnalysis or pause its processing if needed
        updateStatus("Processing stopped. Ready to restart.")
    }

    // --- Camera Initialization and Setup (Logos: Data Acquisition) ---
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // Image Capture (if we need to save frames)
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Image Analysis (Crucial for real-time biological signal extraction)
            [span_0](start_span)// This is where Logos will perform its 'Deeper Learning'[span_0](end_span)
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST_FROM_PIPELINE)
                // Set a resolution appropriate for detailed analysis, e.g., 720p or 1080p
                .setTargetResolution(android.util.Size(1280, 720))
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer { imageProxy ->
                        // Process camera frames here.
                        // This is where raw visual bio-signals (micro-expressions, pupil changes)
                        // will be extracted by Logos's analytical engine.
                        // For now, we just log a message.
                        Log.d(TAG, "Analyzing camera frame: ${imageProxy.format} ${imageProxy.width}x${imageProxy.height}")
                        // Pathos might generate "frame stability" intuitive markers here.
                        // Remember to close the imageProxy when done.
                        imageProxy.close()
                    })
                }

            // Select back camera as a default, or front camera if preferred for facial signals
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA // Or DEFAULT_BACK_CAMERA

            try {
                // Unbind any previous use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalysis
                )

                Log.d(TAG, "Camera initialized successfully with preview and analysis.")

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                updateStatus("Camera initialization failed: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // Custom ImageAnalysis Analyzer for processing frames
    private class ImageAnalyzer(private val listener: (ImageProxy) -> Unit) : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            // Here, you would typically convert ImageProxy to a more usable format (e.g., Bitmap, ByteBuffer)
            // and then pass it to your Logos processing unit.
            listener(imageProxy)
        }
    }

    // --- Audio Recording Setup (Logos: Data Acquisition) ---
    private fun startAudioRecording() {
        // Calculate the minimum buffer size required for the AudioRecord instance.
        // This ensures efficient capture and minimizes data loss.
        val bufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AUDIO_CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * AUDIO_BUFFER_SIZE_FACTOR // Add a factor for robustness

        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "AudioRecord: Invalid buffer size or error occurred.")
            updateStatus("Audio recording setup failed: Invalid buffer size.")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // Optimized for human voice
                AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord: Initialization failed.")
                updateStatus("Audio recording initialization failed.")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isAudioRecording = true
            Log.d(TAG, "Audio recording started with buffer size: $bufferSize bytes")
            updateStatus("Audio recording active...")

            // Start a coroutine to continuously read audio data
            audioRecordingJob = CoroutineScope(Dispatchers.IO).launch {
                val audioBuffer = ShortArray(bufferSize / 2) // Short array for PCM_16BIT
                while (isActive && isAudioRecording) {
                    val shortsRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (shortsRead > 0) {
                        // Process the audioBuffer (Logos: Deeper Learning)
                        // This is where raw auditory bio-signals (vocal nuances, breathing)
                        // will be extracted by Logos's analytical engine.
                        // For now, we just log the data size.
                        Log.d(TAG, "Read $shortsRead shorts of audio data.")
                        // Pathos might generate "audio clarity" intuitive markers here.
                        // Here you would pass audioBuffer to your IAI-IPS processing unit.
                    }
                }
                Log.d(TAG, "Audio recording job finished.")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Audio recording permission not granted.", e)
            updateStatus("Audio recording failed: Permission denied.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            updateStatus("Audio recording failed: ${e.message}")
        }
    }

    private fun stopAudioRecording() {
        isAudioRecording = false
        audioRecordingJob?.cancel() // Cancel the coroutine
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        audioRecord = null
        Log.d(TAG, "Audio recording stopped.")
        updateStatus("Audio recording stopped.")
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            viewBinding.statusTextView.text = message
            Log.i(TAG, message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopAudioRecording() // Ensure audio resources are released on destroy
    }
}
