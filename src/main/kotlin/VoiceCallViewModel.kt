import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class VoiceCallViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // UI State
    var serverIp by mutableStateOf("127.0.0.1")
        private set
    
    var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
        private set
    
    var microphoneLevel by mutableStateOf(0.5f)
        private set
    
    var speakerLevel by mutableStateOf(0.5f)
        private set
    
    var isMicrophoneMuted by mutableStateOf(false)
        private set
    
    var microphoneVolumeLevel by mutableStateOf(0f)
        private set
    
    var errorMessage by mutableStateOf("")
        private set

    var connectionDuration by mutableStateOf(0L)
        private set

    var pingLatency by mutableStateOf(0L)
        private set

    var connectionQuality by mutableStateOf("Unknown")
        private set

    var lastPingTime by mutableStateOf("")
        private set

    var dnsResult by mutableStateOf<DnsResult?>(null)
        private set

    var isResolvingDns by mutableStateOf(false)
        private set

    // Jobs
    private var connectionJob: Job? = null
    private var audioJob: Job? = null
    private var audioReceptionJob: Job? = null
    private var pingJob: Job? = null
    private var connectionTimerJob: Job? = null
    private var dnsJob: Job? = null

    // Managers
    private val audioManager = AudioManager()
    private val networkManager = NetworkManager()
    private val dnsResolver = DnsResolver()
    
    fun updateServerIp(ip: String) {
        serverIp = ip
        // Auto-resolve DNS when user types a domain
        if (ip.isNotEmpty() && !isIpAddress(ip)) {
            resolveDns(ip)
        } else {
            dnsResult = null
        }
    }

    fun resolveDns(domain: String = serverIp) {
        dnsJob?.cancel()
        dnsJob = viewModelScope.launch {
            isResolvingDns = true
            try {
                val result = dnsResolver.resolveDomain(domain)
                dnsResult = result
            } catch (e: Exception) {
                dnsResult = DnsResult(
                    domain = domain,
                    resolvedIps = emptyList(),
                    isSuccessful = false,
                    errorMessage = "DNS resolution failed: ${e.message}"
                )
            } finally {
                isResolvingDns = false
            }
        }
    }

    fun performReverseDnsLookup(ipAddress: String) {
        dnsJob?.cancel()
        dnsJob = viewModelScope.launch {
            isResolvingDns = true
            try {
                val result = dnsResolver.reverseLookup(ipAddress)
                dnsResult = result
            } catch (e: Exception) {
                dnsResult = DnsResult(
                    domain = ipAddress,
                    resolvedIps = emptyList(),
                    isSuccessful = false,
                    errorMessage = "Reverse DNS lookup failed: ${e.message}"
                )
            } finally {
                isResolvingDns = false
            }
        }
    }

    private fun isIpAddress(address: String): Boolean {
        if (address.isEmpty()) return false
        val parts = address.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            try {
                val num = part.toInt()
                num in 0..255
            } catch (e: NumberFormatException) {
                false
            }
        }
    }
    
    fun connect() {
        if (connectionStatus == ConnectionStatus.CONNECTED || 
            connectionStatus == ConnectionStatus.CONNECTING) {
            return
        }
        
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            try {
                connectionStatus = ConnectionStatus.CONNECTING
                errorMessage = ""
                
                // Initialize audio (non-blocking)
                try {
                    audioManager.initialize()
                    if (!audioManager.isAudioAvailable()) {
                        println("Audio not supported on this system. Connection will work without audio.")
                    } else {
                        println("Audio initialized successfully: ${audioManager.getAudioFormatInfo()}")

                        // Set up audio data callback to send over network
                        audioManager.setAudioDataCallback { audioData ->
                            viewModelScope.launch {
                                try {
                                    networkManager.sendAudioData(audioData)
                                } catch (e: Exception) {
                                    println("Failed to send audio data: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Audio initialization failed: ${e.message}")
                    // Continue without audio
                }

                // Connect to server
                networkManager.connect(serverIp)

                connectionStatus = ConnectionStatus.CONNECTED

                // Start connection monitoring
                startConnectionMonitoring()

                // Start audio streaming (will work with or without audio support)
                startAudioStreaming()

                // Note: Audio reception disabled temporarily to prevent crashes
                // TODO: Implement proper audio reception that doesn't block the main thread
                
            } catch (e: Exception) {
                connectionStatus = ConnectionStatus.ERROR
                errorMessage = e.message ?: "Connection failed"
            }
        }
    }
    
    fun disconnect() {
        // First, update the connection status to stop any ongoing operations
        connectionStatus = ConnectionStatus.DISCONNECTED

        // Cancel all coroutines safely
        try {
            connectionJob?.cancel()
            audioJob?.cancel()
            audioReceptionJob?.cancel()
            pingJob?.cancel()
            connectionTimerJob?.cancel()
            dnsJob?.cancel()
        } catch (e: Exception) {
            println("Warning: Error cancelling coroutines: ${e.message}")
        }

        // Clear job references
        connectionJob = null
        audioJob = null
        audioReceptionJob = null
        pingJob = null
        connectionTimerJob = null
        dnsJob = null

        // Perform cleanup in a separate coroutine with proper exception handling
        viewModelScope.launch {
            try {
                // Cleanup network connection first
                networkManager.disconnect()
            } catch (e: Exception) {
                println("Warning: Error during network disconnect: ${e.message}")
            }

            try {
                // Cleanup audio resources
                audioManager.cleanup()
            } catch (e: Exception) {
                println("Warning: Error during audio cleanup: ${e.message}")
            }

            // Reset state
            try {
                errorMessage = ""
                resetConnectionStats()
            } catch (e: Exception) {
                println("Warning: Error resetting stats: ${e.message}")
                errorMessage = "Disconnect completed with warnings"
            }
        }
    }
    
    fun updateMicrophoneLevel(level: Float) {
        microphoneLevel = level.coerceIn(0f, 1f)
        audioManager.setMicrophoneLevel(microphoneLevel)
    }
    
    fun updateSpeakerLevel(level: Float) {
        speakerLevel = level.coerceIn(0f, 1f)
        audioManager.setSpeakerLevel(speakerLevel)
    }
    
    fun toggleMicrophoneMute() {
        isMicrophoneMuted = !isMicrophoneMuted
        audioManager.setMicrophoneMuted(isMicrophoneMuted)
    }
    
    private fun startAudioStreaming() {
        audioJob?.cancel()
        audioJob = viewModelScope.launch {
            try {
                // Only start audio streaming if audio is available
                if (audioManager.isAudioAvailable()) {
                    audioManager.startStreaming { volumeLevel ->
                        microphoneVolumeLevel = volumeLevel
                    }
                } else {
                    println("Audio streaming skipped - audio not available")
                }
            } catch (e: Exception) {
                println("Audio streaming error: ${e.message}")
                // Don't set error message for audio issues, just log them
                // The connection can still work without audio
            }
        }
    }

    private fun startAudioReception() {
        audioReceptionJob?.cancel()
        audioReceptionJob = viewModelScope.launch {
            try {
                while (connectionStatus == ConnectionStatus.CONNECTED && isActive) {
                    try {
                        val audioData = networkManager.receiveAudioData()
                        if (audioData != null && connectionStatus == ConnectionStatus.CONNECTED) {
                            audioManager.playReceivedAudio(audioData)
                        }
                    } catch (e: Exception) {
                        println("Audio reception error: ${e.message}")
                        // Don't break the loop for audio errors, but check if we should continue
                        if (connectionStatus == ConnectionStatus.CONNECTED && isActive) {
                            kotlinx.coroutines.delay(100)
                        } else {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                println("Audio reception loop terminated: ${e.message}")
            }
        }
    }

    private fun startConnectionMonitoring() {
        // Start connection timer
        connectionTimerJob?.cancel()
        connectionTimerJob = viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                while (connectionStatus == ConnectionStatus.CONNECTED && isActive) {
                    connectionDuration = (System.currentTimeMillis() - startTime) / 1000
                    kotlinx.coroutines.delay(1000)
                }
            } catch (e: Exception) {
                println("Connection monitoring stopped: ${e.message}")
            }
        }

        // Start ping monitoring
        startPingMonitoring()
    }

    private fun startPingMonitoring() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            try {
                // Wait a bit before starting ping to let connection stabilize
                kotlinx.coroutines.delay(2000)

                while (connectionStatus == ConnectionStatus.CONNECTED && isActive) {
                    try {
                        val latency = networkManager.ping()
                        pingLatency = latency
                        connectionQuality = when {
                            latency < 50 -> "Excellent"
                            latency < 100 -> "Good"
                            latency < 200 -> "Fair"
                            else -> "Poor"
                        }
                        lastPingTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date())

                        println("Ping: ${latency}ms")

                    } catch (e: Exception) {
                        println("Ping failed: ${e.message}")
                        connectionQuality = "Error"
                        // Don't break the loop, just try again later
                    }

                    // Ping every 10 seconds
                    kotlinx.coroutines.delay(10000)
                }
            } catch (e: Exception) {
                println("Ping monitoring stopped: ${e.message}")
            }
        }
    }

    private fun resetConnectionStats() {
        connectionDuration = 0L
        pingLatency = 0L
        connectionQuality = "Unknown"
        lastPingTime = ""
    }

    fun cleanup() {
        disconnect()
    }
}
