package com.synapseLink.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

// This class represents the central processing unit where Logos integrates
// and synchronizes multi-modal biological signals.
// It also serves as the conceptual interface for Pathos.
class SynapseLinkProcessor(private val onBioSignalDataReady: (BioSignalData) -> Unit) {

    // Buffers to store recent visual and audio data. Logos uses these for
    // 'Historical Learning' to find temporal correlations.
    private val visualDataBuffer = ConcurrentLinkedQueue<CameraFrameAnalyzer.FrameData>()
    private val audioDataBuffer = ConcurrentLinkedQueue<AudioBufferAnalyzer.AudioData>()

    // Configuration for synchronization (Pathos might influence these parameters later)
    private val MAX_BUFFER_SIZE = 5 // Store up to 5 recent frames/audio chunks
    private val SYNC_TOLERANCE_MS = 100L // Max time difference for considering data "synchronous"

    // Coroutine for periodic synchronization attempts
    private var syncJob: Job? = null
    private val syncScope = CoroutineScope(Dispatchers.Default) // Use Default for CPU-bound tasks

    companion object {
        private const val TAG = "SynapseLinkProcessor"
    }

    // Call this to start the periodic synchronization process
    fun startProcessing() {
        if (syncJob?.isActive == true) {
            Log.d(TAG, "SynapseLinkProcessor already running.")
            return
        }
        syncJob = syncScope.launch {
            // Logos will periodically attempt to synchronize data
            while (true) {
                attemptSynchronization()
                delay(SYNC_TOLERANCE_MS / 2) // Check more frequently than tolerance
            }
        }
        Log.d(TAG, "SynapseLinkProcessor started.")
    }

    // Call this to stop the periodic synchronization process
    fun stopProcessing() {
        syncJob?.cancel()
        syncJob = null
        visualDataBuffer.clear()
        audioDataBuffer.clear()
        Log.d(TAG, "SynapseLinkProcessor stopped. Buffers cleared.")
    }


    // Logos receives processed visual data from CameraFrameAnalyzer
    fun onNewFrameData(frameData: CameraFrameAnalyzer.FrameData) {
        if (visualDataBuffer.size >= MAX_BUFFER_SIZE) {
            visualDataBuffer.poll() // Remove oldest if buffer is full
        }
        visualDataBuffer.offer(frameData) // Add newest
        Log.v(TAG, "Received frame data. Buffer size: ${visualDataBuffer.size}")
        // Pathos might generate a "visual data influx" intuitive marker here.
    }

    // Logos receives processed audio data from AudioBufferAnalyzer
    fun onNewAudioData(audioData: AudioBufferAnalyzer.AudioData) {
        if (audioDataBuffer.size >= MAX_BUFFER_SIZE) {
            audioDataBuffer.poll() // Remove oldest if buffer is full
        }
        audioDataBuffer.offer(audioData) // Add newest
        Log.v(TAG, "Received audio data. Buffer size: ${audioDataBuffer.size}")
        // Pathos might generate an "audio data influx" intuitive marker here.
    }

    // Logos's 'calculated action' for synchronizing multi-modal data.
    // This method attempts to find the best matching visual and audio data within the tolerance.
    private fun attemptSynchronization() {
        val currentVisual = visualDataBuffer.peek() // Get oldest in buffer without removing
        val currentAudio = audioDataBuffer.peek()

        if (currentVisual == null || currentAudio == null) {
            return // Not enough data to synchronize yet
        }

        val timeDiff = abs(currentVisual.timestamp - currentAudio.timestamp)

        if (timeDiff <= SYNC_TOLERANCE_MS) {
            // Found a match within tolerance!
            val combinedData = BioSignalData(
                timestamp = (currentVisual.timestamp + currentAudio.timestamp) / 2, // Average timestamp
                visualSignals = visualDataBuffer.poll(), // Remove after using
                audioSignals = audioDataBuffer.poll() // Remove after using
            )
            Log.d(TAG, "Synchronized data at ${combinedData.timestamp}. Visual-Audio diff: $timeDiff ms")

            // Pass the synchronized data to the next stage of Logos's higher-level analysis
            // AND to Pathos for intuitive assessment and saliency tagging.
            onBioSignalDataReady(combinedData)

            // Pathos might generate a "synchronization success" or "data coherence" intuitive marker.

        } else if (currentVisual.timestamp < currentAudio.timestamp - SYNC_TOLERANCE_MS) {
            // Visual data is too old, discard it and try to find a newer visual match.
            Log.v(TAG, "Visual data too old (${timeDiff}ms diff), discarding: ${visualDataBuffer.poll()?.timestamp}")
            // Pathos might generate a "visual data lag" intuitive marker here.
        } else if (currentAudio.timestamp < currentVisual.timestamp - SYNC_TOLERANCE_MS) {
            // Audio data is too old, discard it and try to find newer audio match.
            Log.v(TAG, "Audio data too old (${timeDiff}ms diff), discarding: ${audioDataBuffer.poll()?.timestamp}")
            // Pathos might generate an "audio data lag" intuitive marker here.
        }
        // If neither is too old, wait for new data to arrive in buffers
    }
}
