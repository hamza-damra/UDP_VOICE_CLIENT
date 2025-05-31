import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

class NetworkManager {
    private var socket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null
    private val selectorManager = SelectorManager(Dispatchers.IO)

    suspend fun connect(serverIp: String, port: Int = 8080) {
        try {
            // Validate IP address or domain name format
            if (!isValidIpAddressOrDomain(serverIp)) {
                throw Exception("Invalid IP address or domain name format")
            }
            
            // Create socket connection
            socket = aSocket(selectorManager).tcp().connect(serverIp, port)
            
            socket?.let { sock ->
                readChannel = sock.openReadChannel()
                writeChannel = sock.openWriteChannel(autoFlush = true)
            }
            
            // Send initial handshake
            writeChannel?.writeStringUtf8("VOICE_CLIENT_CONNECT\n")
            
            // Wait for server response
            val response = readChannel?.readUTF8Line()
            if (response != "VOICE_SERVER_READY") {
                throw Exception("Server handshake failed: $response")
            }

        } catch (e: Exception) {
            cleanup()
            throw Exception("Failed to connect to server: ${e.message}")
        }
    }
    
    suspend fun disconnect() {
        try {
            writeChannel?.writeStringUtf8("VOICE_CLIENT_DISCONNECT\n")
            delay(100) // Give time for message to be sent
        } catch (e: Exception) {
            // Ignore errors during disconnect
        } finally {
            cleanup()
        }
    }
    
    suspend fun sendAudioData(audioData: ByteArray) {
        try {
            writeChannel?.let { channel ->
                // Send audio data message first (required by protocol)
                channel.writeStringUtf8("AUDIO_DATA\n")

                // Send frame length in little-endian format (4 bytes)
                val lengthBytes = ByteArray(4)
                val length = audioData.size
                lengthBytes[0] = (length and 0xFF).toByte()
                lengthBytes[1] = ((length shr 8) and 0xFF).toByte()
                lengthBytes[2] = ((length shr 16) and 0xFF).toByte()
                lengthBytes[3] = ((length shr 24) and 0xFF).toByte()
                channel.writeFully(lengthBytes)

                // Send audio data
                channel.writeFully(audioData)
            }
        } catch (e: Exception) {
            throw Exception("Failed to send audio data: ${e.message}")
        }
    }
    

    
    fun isConnected(): Boolean {
        return socket?.isClosed == false
    }

    suspend fun ping(): Long {
        return try {
            val startTime = System.currentTimeMillis()

            // Send ping message
            writeChannel?.writeStringUtf8("PING\n")

            // Wait for pong response
            val response = readChannel?.readUTF8Line()
            val endTime = System.currentTimeMillis()

            if (response == "PONG") {
                endTime - startTime
            } else {
                throw Exception("Invalid ping response: $response")
            }
        } catch (e: Exception) {
            throw Exception("Ping failed: ${e.message}")
        }
    }

    suspend fun receiveAudioData(): ByteArray? {
        return try {
            readChannel?.let { channel ->
                // Read frame length in little-endian format (4 bytes)
                val lengthBytes = ByteArray(4)
                channel.readFully(lengthBytes)

                val length = (lengthBytes[0].toInt() and 0xFF) or
                           ((lengthBytes[1].toInt() and 0xFF) shl 8) or
                           ((lengthBytes[2].toInt() and 0xFF) shl 16) or
                           ((lengthBytes[3].toInt() and 0xFF) shl 24)

                if (length > 0 && length <= 65535) { // Max frame size check
                    val audioData = ByteArray(length)
                    channel.readFully(audioData)
                    audioData
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null // Return null on error, let caller handle
        }
    }

    private fun cleanup() {
        try {
            readChannel?.cancel()
        } catch (e: Exception) {
            println("Warning: Error cancelling read channel: ${e.message}")
        }

        try {
            writeChannel?.close()
        } catch (e: Exception) {
            println("Warning: Error closing write channel: ${e.message}")
        }

        try {
            socket?.close()
        } catch (e: Exception) {
            println("Warning: Error closing socket: ${e.message}")
        }

        readChannel = null
        writeChannel = null
        socket = null
    }
    
    private fun isValidIpAddress(ip: String): Boolean {
        if (ip.isEmpty()) return false

        val parts = ip.split(".")
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

    private fun isValidIpAddressOrDomain(address: String): Boolean {
        if (address.isEmpty()) return false

        // Check if it's a valid IP address
        if (isValidIpAddress(address)) return true

        // Check if it's a valid domain name
        return isValidDomainName(address)
    }

    private fun isValidDomainName(domain: String): Boolean {
        if (domain.isEmpty() || domain.length > 253) return false

        // Basic domain validation
        val domainPattern = Regex("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$")
        return domainPattern.matches(domain)
    }
    
    // Simulate server for testing purposes
    companion object {
        suspend fun startTestServer(port: Int = 8080) {
            try {
                val selectorManager = SelectorManager(Dispatchers.IO)
                val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", port)

                println("Test server started on port $port")

                while (true) {
                    val socket = serverSocket.accept()
                    println("Client connected")
                    
                    try {
                        val readChannel = socket.openReadChannel()
                        val writeChannel = socket.openWriteChannel(autoFlush = true)
                        
                        // Handle handshake
                        val handshake = readChannel.readUTF8Line()
                        if (handshake == "VOICE_CLIENT_CONNECT") {
                            writeChannel.writeStringUtf8("VOICE_SERVER_READY\n")
                            println("Handshake completed")
                        }
                        
                        // Handle client messages
                        while (true) {
                            try {
                                // Try to read a text message first (for ping/disconnect/audio_data)
                                val message = readChannel.readUTF8Line()
                                when (message) {
                                    "PING" -> {
                                        writeChannel.writeStringUtf8("PONG\n")
                                        println("Ping received, pong sent")
                                    }
                                    "VOICE_CLIENT_DISCONNECT" -> {
                                        println("Client disconnect received")
                                        break
                                    }
                                    "AUDIO_DATA" -> {
                                        // Read audio frame with little-endian length
                                        try {
                                            val lengthBytes = ByteArray(4)
                                            readChannel.readFully(lengthBytes)

                                            val length = (lengthBytes[0].toInt() and 0xFF) or
                                                       ((lengthBytes[1].toInt() and 0xFF) shl 8) or
                                                       ((lengthBytes[2].toInt() and 0xFF) shl 16) or
                                                       ((lengthBytes[3].toInt() and 0xFF) shl 24)

                                            if (length > 0 && length <= 65535) {
                                                val audioData = ByteArray(length)
                                                readChannel.readFully(audioData)
                                                println("Received audio frame: $length bytes")

                                                // Echo back with proper protocol
                                                writeChannel.writeFully(lengthBytes)
                                                writeChannel.writeFully(audioData)
                                            }
                                        } catch (e: Exception) {
                                            println("Audio read error: ${e.message}")
                                        }
                                    }
                                    else -> {
                                        println("Unknown message: $message")
                                    }
                                }
                            } catch (e: Exception) {
                                break
                            }
                        }
                        
                    } catch (e: Exception) {
                        println("Client error: ${e.message}")
                    } finally {
                        socket.close()
                        println("Client disconnected")
                    }
                }
            } catch (e: Exception) {
                println("Server error: ${e.message}")
            }
        }
    }
}
