import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.sound.sampled.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.random.Random

class AudioManager {
    private var targetDataLine: TargetDataLine? = null
    private var sourceDataLine: SourceDataLine? = null
    private var microphoneLevel = 0.5f
    private var speakerLevel = 0.5f
    private var isMicrophoneMuted = false
    private var currentAudioFormat: AudioFormat? = null
    private var isAudioSupported = false

    // Callback for sending audio data over network
    private var onAudioDataCallback: ((ByteArray) -> Unit)? = null

    // Multiple audio formats to try, from most preferred to least
    private val supportedFormats = listOf(
        // Standard CD quality
        AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 1, 2, 44100f, false),
        // Lower quality but more compatible
        AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050f, 16, 1, 2, 22050f, false),
        // Even lower quality
        AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 11025f, 16, 1, 2, 11025f, false),
        // 8-bit formats
        AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 8, 1, 1, 44100f, false),
        AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050f, 8, 1, 1, 22050f, false),
        // Stereo formats
        AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f, false),
        AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050f, 16, 2, 4, 22050f, false)
    )
    
    suspend fun initialize() {
        var lastException: Exception? = null

        // Try each audio format until one works
        for (format in supportedFormats) {
            try {
                println("Trying audio format: ${format.sampleRate}Hz, ${format.sampleSizeInBits}-bit, ${format.channels} channel(s)")

                // Try to initialize microphone (input)
                val micInfo = DataLine.Info(TargetDataLine::class.java, format)
                if (!AudioSystem.isLineSupported(micInfo)) {
                    println("Microphone not supported for format: $format")
                    continue
                }

                val tempTargetLine = AudioSystem.getLine(micInfo) as TargetDataLine
                tempTargetLine.open(format)

                // Try to initialize speaker (output)
                val speakerInfo = DataLine.Info(SourceDataLine::class.java, format)
                if (!AudioSystem.isLineSupported(speakerInfo)) {
                    println("Speaker not supported for format: $format")
                    tempTargetLine.close()
                    continue
                }

                val tempSourceLine = AudioSystem.getLine(speakerInfo) as SourceDataLine
                tempSourceLine.open(format)

                // If we get here, both lines opened successfully
                targetDataLine = tempTargetLine
                sourceDataLine = tempSourceLine
                currentAudioFormat = format
                isAudioSupported = true

                println("Successfully initialized audio with format: ${format.sampleRate}Hz, ${format.sampleSizeInBits}-bit, ${format.channels} channel(s)")
                return

            } catch (e: Exception) {
                println("Failed to initialize audio with format $format: ${e.message}")
                lastException = e
                // Clean up any partially opened lines
                try {
                    targetDataLine?.close()
                    sourceDataLine?.close()
                } catch (cleanupException: Exception) {
                    // Ignore cleanup errors
                }
                targetDataLine = null
                sourceDataLine = null
            }
        }

        // If we get here, no audio format worked
        isAudioSupported = false
        println("Audio not supported on this system. Voice call will work without audio.")

        // Don't throw an exception, just log that audio is not available
        // This allows the application to continue working without audio
    }
    
    fun setAudioDataCallback(callback: (ByteArray) -> Unit) {
        onAudioDataCallback = callback
    }

    suspend fun startStreaming(onVolumeUpdate: (Float) -> Unit) {
        if (!isAudioSupported) {
            // Simulate audio streaming with fake data when audio is not supported
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                // Simulate volume level updates
                val fakeVolumeLevel = if (isMicrophoneMuted) 0f else Random.nextFloat() * 0.3f * microphoneLevel
                onVolumeUpdate(fakeVolumeLevel)
                delay(100) // Update every 100ms
            }
            return
        }

        targetDataLine?.start()
        sourceDataLine?.start()

        val bufferSize = currentAudioFormat?.let { format ->
            // Calculate appropriate buffer size based on format
            (format.sampleRate * format.frameSize / 10).toInt() // 100ms buffer
        } ?: 1024

        val buffer = ByteArray(bufferSize)

        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                // Read from microphone
                val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0

                if (bytesRead > 0) {
                    // Calculate volume level for visualization
                    val volumeLevel = calculateVolumeLevel(buffer, bytesRead)
                    onVolumeUpdate(if (isMicrophoneMuted) 0f else volumeLevel * microphoneLevel)

                    // Apply microphone level and mute
                    if (!isMicrophoneMuted) {
                        applyGain(buffer, bytesRead, microphoneLevel)

                        // Send audio data over network
                        val audioData = buffer.copyOf(bytesRead)
                        onAudioDataCallback?.invoke(audioData)
                    }
                }

                // Small delay to prevent excessive CPU usage
                delay(10)

            } catch (e: Exception) {
                println("Audio streaming error: ${e.message}")
                // Don't throw exception, just log and continue
                delay(100)
            }
        }
    }
    
    fun setMicrophoneLevel(level: Float) {
        microphoneLevel = level.coerceIn(0f, 1f)
    }
    
    fun setSpeakerLevel(level: Float) {
        speakerLevel = level.coerceIn(0f, 1f)
        // Apply speaker level to the output line
        sourceDataLine?.let { line ->
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gainControl = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val range = gainControl.maximum - gainControl.minimum
                val gain = gainControl.minimum + (range * speakerLevel)
                gainControl.value = gain
            }
        }
    }
    
    fun setMicrophoneMuted(muted: Boolean) {
        isMicrophoneMuted = muted
    }

    fun playReceivedAudio(audioData: ByteArray) {
        if (isAudioSupported && sourceDataLine != null) {
            try {
                // Apply speaker level
                val processedData = audioData.copyOf()
                applyGain(processedData, processedData.size, speakerLevel)

                // Play the audio
                sourceDataLine?.write(processedData, 0, processedData.size)
            } catch (e: Exception) {
                println("Error playing received audio: ${e.message}")
            }
        }
    }

    fun isAudioAvailable(): Boolean {
        return isAudioSupported
    }

    fun getAudioFormatInfo(): String {
        return if (isAudioSupported && currentAudioFormat != null) {
            val format = currentAudioFormat!!
            "${format.sampleRate.toInt()}Hz, ${format.sampleSizeInBits}-bit, ${format.channels} channel(s)"
        } else {
            "Audio not available"
        }
    }

    private fun calculateVolumeLevel(buffer: ByteArray, length: Int): Float {
        val format = currentAudioFormat ?: return 0f

        var sum = 0.0
        val sampleSizeInBytes = format.sampleSizeInBits / 8
        val channels = format.channels

        when (format.sampleSizeInBits) {
            16 -> {
                // 16-bit samples
                for (i in 0 until length step (sampleSizeInBytes * channels)) {
                    if (i + 1 < length) {
                        val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                        sum += abs(sample.toDouble())
                    }
                }
                val average = sum / (length / (sampleSizeInBytes * channels))
                return (average / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
            }
            8 -> {
                // 8-bit samples
                for (i in 0 until length step channels) {
                    val sample = buffer[i].toInt()
                    sum += abs(sample.toDouble())
                }
                val average = sum / (length / channels)
                return (average / 127.0).toFloat().coerceIn(0f, 1f)
            }
            else -> {
                // Fallback for other bit depths
                for (i in buffer.indices) {
                    sum += abs(buffer[i].toDouble())
                }
                val average = sum / buffer.size
                return (average / 127.0).toFloat().coerceIn(0f, 1f)
            }
        }
    }
    
    private fun applyGain(buffer: ByteArray, length: Int, gain: Float) {
        for (i in 0 until length step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            val amplified = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = (amplified and 0xFF).toByte()
            buffer[i + 1] = ((amplified shr 8) and 0xFF).toByte()
        }
    }
    
    fun cleanup() {
        try {
            targetDataLine?.stop()
        } catch (e: Exception) {
            println("Warning: Error stopping target data line: ${e.message}")
        }

        try {
            targetDataLine?.close()
        } catch (e: Exception) {
            println("Warning: Error closing target data line: ${e.message}")
        }

        try {
            sourceDataLine?.stop()
        } catch (e: Exception) {
            println("Warning: Error stopping source data line: ${e.message}")
        }

        try {
            sourceDataLine?.close()
        } catch (e: Exception) {
            println("Warning: Error closing source data line: ${e.message}")
        }

        targetDataLine = null
        sourceDataLine = null
        onAudioDataCallback = null
        isAudioSupported = false
        currentAudioFormat = null
    }
}
