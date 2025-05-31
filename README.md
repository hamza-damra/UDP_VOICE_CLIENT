# UDP Voice Client

A Kotlin Compose Desktop application for voice communication over UDP protocol.

## Features

- **Voice Communication**: Real-time voice transmission over UDP
- **DNS Resolution**: Built-in DNS resolver for hostname resolution
- **Audio Management**: Microphone input and speaker output control
- **Network Management**: TCP connection handling with handshake protocol
- **Modern UI**: Built with Jetpack Compose for Desktop
- **Cross-Platform**: Runs on Windows, macOS, and Linux

## Architecture

The application follows a clean architecture pattern with the following components:

- **VoiceCallApp**: Main UI component with Compose
- **VoiceCallViewModel**: Business logic and state management
- **NetworkManager**: Handles TCP connections and protocol communication
- **AudioManager**: Manages audio input/output operations
- **DnsResolver**: Resolves hostnames to IP addresses

## Protocol

The voice client uses a custom TCP protocol with the following message types:

- `VOICE_CLIENT_CONNECT`: Initial connection handshake
- `VOICE_SERVER_READY`: Server acknowledgment
- `PING`/`PONG`: Keep-alive mechanism (every 30 seconds)
- `AUDIO_DATA`: Audio frame transmission with length header
- `VOICE_CLIENT_DISCONNECT`: Graceful disconnection

## Building

### Prerequisites

- JDK 11 or higher
- Gradle 7.0 or higher

### Build Commands

```bash
# Build the application
./gradlew build

# Run the application
./gradlew run

# Create MSI installer (Windows)
./gradlew createDistributable
./build-msi.bat
```

## Installation

### Windows

1. Download the latest MSI installer from the releases page
2. Run the installer and follow the setup wizard
3. Launch the application from the Start Menu

### Manual Installation

1. Clone the repository
2. Build the application using Gradle
3. Run the generated executable

## Usage

1. Launch the UDP Voice Client
2. Enter the server IP address or hostname
3. Click "Connect" to establish connection
4. Use the microphone controls to transmit audio
5. Adjust audio levels as needed
6. Click "Disconnect" when finished

## Development

### Project Structure

```
src/main/kotlin/
├── Main.kt                 # Application entry point
├── VoiceCallApp.kt        # Main UI component
├── VoiceCallViewModel.kt  # State management
├── NetworkManager.kt      # Network operations
├── AudioManager.kt        # Audio handling
├── DnsResolver.kt         # DNS resolution
└── TestServer.kt          # Test server implementation
```

### Building MSI Installer

The project includes WiX Toolset configuration for creating Windows MSI installers:

1. Install WiX Toolset 3.11
2. Run `./gradlew createDistributable`
3. Execute `build-msi.bat`
4. Find the MSI file in the `installer` directory

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is open source. Please check the license file for details.

## Support

For issues and questions, please use the GitHub Issues page.
