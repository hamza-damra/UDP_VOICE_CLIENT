import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceCallApp(viewModel: VoiceCallViewModel = remember { VoiceCallViewModel() }) {
    
    // Cleanup when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cleanup()
        }
    }
    
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Voice Call Application",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Connection Section
                ConnectionSection(viewModel)

                Spacer(modifier = Modifier.height(16.dp))

                // DNS Information Section
                DnsSection(viewModel)

                Spacer(modifier = Modifier.height(16.dp))
                
                // Audio Controls Section
                AudioControlsSection(viewModel)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Status Section
                StatusSection(viewModel)

                // Add some bottom padding for better scrolling experience
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ConnectionSection(viewModel: VoiceCallViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Server Connection",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            // Server IP Input
            OutlinedTextField(
                value = viewModel.serverIp,
                onValueChange = viewModel::updateServerIp,
                label = { Text("Server IP or Domain") },
                placeholder = { Text("127.0.0.1 or example.com") },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.connectionStatus == ConnectionStatus.DISCONNECTED,
                singleLine = true
            )
            
            // Connect/Disconnect Button
            Button(
                onClick = {
                    when (viewModel.connectionStatus) {
                        ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> viewModel.connect()
                        ConnectionStatus.CONNECTED -> viewModel.disconnect()
                        ConnectionStatus.CONNECTING -> { /* Do nothing */ }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.connectionStatus != ConnectionStatus.CONNECTING,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = when (viewModel.connectionStatus) {
                        ConnectionStatus.CONNECTED -> Color(0xFFE57373) // Red for disconnect
                        else -> MaterialTheme.colors.primary // Blue for connect
                    }
                )
            ) {
                Icon(
                    imageVector = when (viewModel.connectionStatus) {
                        ConnectionStatus.CONNECTED -> Icons.Default.Close // Using Close instead of CallEnd
                        ConnectionStatus.CONNECTING -> Icons.Default.Refresh // Using Refresh instead of Sync
                        else -> Icons.Default.Call
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (viewModel.connectionStatus) {
                        ConnectionStatus.DISCONNECTED -> "Connect"
                        ConnectionStatus.CONNECTING -> "Connecting..."
                        ConnectionStatus.CONNECTED -> "Disconnect"
                        ConnectionStatus.ERROR -> "Retry Connection"
                    }
                )
            }
        }
    }
}

@Composable
private fun DnsSection(viewModel: VoiceCallViewModel) {
    // Only show DNS section if there's DNS information or if resolving
    if (viewModel.dnsResult != null || viewModel.isResolvingDns) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DNS Information",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (viewModel.isResolvingDns) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Manual resolve button
                    IconButton(
                        onClick = { viewModel.resolveDns() },
                        enabled = !viewModel.isResolvingDns && viewModel.serverIp.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Resolve DNS",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                viewModel.dnsResult?.let { result ->
                    // Domain/IP being resolved
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Domain",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colors.primary
                        )
                        Text(
                            text = "Domain: ${result.domain}",
                            fontSize = 14.sp
                        )
                    }

                    // Resolution status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (result.isSuccessful) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = "Status",
                            modifier = Modifier.size(16.dp),
                            tint = if (result.isSuccessful) Color.Green else Color.Red
                        )
                        Text(
                            text = if (result.isSuccessful) "Resolved successfully" else "Resolution failed",
                            fontSize = 14.sp,
                            color = if (result.isSuccessful) Color.Green else Color.Red
                        )
                    }

                    // Resolution time
                    if (result.resolutionTimeMs > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Time",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Gray
                            )
                            Text(
                                text = "Resolution time: ${result.resolutionTimeMs}ms",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    // Resolved IP addresses
                    if (result.resolvedIps.isNotEmpty()) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Resolved IP Addresses:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        result.resolvedIps.forEach { ip ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "IP",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colors.primary
                                )
                                Text(
                                    text = ip,
                                    fontSize = 13.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                // Reverse DNS lookup button
                                IconButton(
                                    onClick = { viewModel.performReverseDnsLookup(ip) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Reverse lookup",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Error message
                    result.errorMessage?.let { error ->
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Red
                            )
                            Text(
                                text = error,
                                fontSize = 12.sp,
                                color = Color.Red
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioControlsSection(viewModel: VoiceCallViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Audio Controls",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            // Microphone Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (viewModel.isMicrophoneMuted) Icons.Default.Clear else Icons.Default.Check, // Using Clear/Check for mic mute state
                    contentDescription = "Microphone",
                    tint = if (viewModel.isMicrophoneMuted) Color.Red else MaterialTheme.colors.primary
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("Microphone Level", fontSize = 14.sp)
                    Slider(
                        value = viewModel.microphoneLevel,
                        onValueChange = viewModel::updateMicrophoneLevel,
                        enabled = !viewModel.isMicrophoneMuted,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                IconButton(
                    onClick = viewModel::toggleMicrophoneMute
                ) {
                    Icon(
                        imageVector = if (viewModel.isMicrophoneMuted) Icons.Default.Clear else Icons.Default.Check,
                        contentDescription = "Toggle Mute",
                        tint = if (viewModel.isMicrophoneMuted) Color.Red else MaterialTheme.colors.primary
                    )
                }
            }
            
            // Microphone Volume Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Input Level:", fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = viewModel.microphoneVolumeLevel,
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = if (viewModel.microphoneVolumeLevel > 0.8f) Color.Red 
                           else if (viewModel.microphoneVolumeLevel > 0.6f) Color.Yellow 
                           else Color.Green
                )
            }
            
            Divider()
            
            // Speaker Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow, // Using PlayArrow for Speaker
                    contentDescription = "Speaker",
                    tint = MaterialTheme.colors.primary
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("Speaker Level", fontSize = 14.sp)
                    Slider(
                        value = viewModel.speakerLevel,
                        onValueChange = viewModel::updateSpeakerLevel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusSection(viewModel: VoiceCallViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Status",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            // Connection Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = when (viewModel.connectionStatus) {
                                ConnectionStatus.CONNECTED -> Color.Green
                                ConnectionStatus.CONNECTING -> Color.Yellow
                                ConnectionStatus.ERROR -> Color.Red
                                ConnectionStatus.DISCONNECTED -> Color.Gray
                            },
                            shape = RoundedCornerShape(6.dp)
                        )
                )
                
                Text(
                    text = when (viewModel.connectionStatus) {
                        ConnectionStatus.CONNECTED -> "Connected to ${viewModel.serverIp}"
                        ConnectionStatus.CONNECTING -> "Connecting to ${viewModel.serverIp}..."
                        ConnectionStatus.ERROR -> "Connection Error"
                        ConnectionStatus.DISCONNECTED -> "Disconnected"
                    },
                    fontSize = 14.sp
                )
            }
            
            // Connection Details (only when connected)
            if (viewModel.connectionStatus == ConnectionStatus.CONNECTED) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Connection Duration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Duration",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colors.primary
                    )
                    Text(
                        text = "Duration: ${formatDuration(viewModel.connectionDuration)}",
                        fontSize = 12.sp
                    )
                }

                // Ping Information
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Ping",
                        modifier = Modifier.size(16.dp),
                        tint = when (viewModel.connectionQuality) {
                            "Excellent" -> Color.Green
                            "Good" -> Color(0xFF4CAF50)
                            "Fair" -> Color(0xFFFF9800)
                            "Poor" -> Color.Red
                            else -> Color.Gray
                        }
                    )
                    Text(
                        text = "Ping: ${viewModel.pingLatency}ms (${viewModel.connectionQuality})",
                        fontSize = 12.sp
                    )
                }

                // Last Ping Time
                if (viewModel.lastPingTime.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Last Ping",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                        Text(
                            text = "Last ping: ${viewModel.lastPingTime}",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Error Message
            if (viewModel.errorMessage.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Red
                    )
                    Text(
                        text = viewModel.errorMessage,
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// Helper function to format duration
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%02d:%02d", minutes, secs)
    }
}
