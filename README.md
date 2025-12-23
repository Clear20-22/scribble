# Scribble V2

Real-time multiplayer drawing and guessing game (Pictionary/Skribbl style) built with Java 17, JavaFX, and TCP sockets.

## Features

- Real-time drawing synchronization
- Multiple rooms with customizable settings
- Spectator mode for full rooms
- Automatic reconnection on connection drops
- Cross-platform support (any device with Java 17+)
- Word hints with timed reveals
- Scoring system based on guess speed and order

## Modules

- `scribble-shared`: Shared message protocol and utilities
- `scribble-server`: Multiplayer game server
- `scribble-client`: JavaFX client UI

## Requirements

- Java 17 or higher
- Network access for Gradle wrapper to download dependencies
- For client: A display supporting JavaFX (most modern systems)

## Getting Started

### 1. Clone or Download the Project

Ensure you have the project files in a directory.

### 2. Build the Project

Open a terminal in the `scribble-v2` directory and run:

```bash
./gradlew clean build
```

This will compile all modules and download necessary dependencies.

### 3. Start the Server

To run the game server:

```bash
./gradlew :scribble-server:run
```

The server will start on the default port `5555`. You can specify a different port:

```bash
./gradlew :scribble-server:run --args="6000"
```

Keep the server running in one terminal window.

### 4. Start Clients

Each player needs to run their own client instance. Open a new terminal for each client:

```bash
./gradlew :scribble-client:run
```

In the login screen that appears:
- **Username**: Enter a unique name for the player
- **Host**: `localhost` (if running on the same machine) or the server's IP address
- **Port**: `5555` (or the custom port you specified)

Click "Connect" to join the game.

## Adding Multiple Users

To add more players:

1. **On the same machine**: Open multiple terminal windows and run `./gradlew :scribble-client:run` in each. Use different usernames.

2. **On different machines**: Ensure all machines are on the same network. Run the client on each machine, but use the server's IP address instead of `localhost` in the Host field.

The server supports multiple concurrent connections. Players can join existing rooms or create new ones.

## Running with Different Devices

Scribble V2 is cross-platform and can run on any device with Java 17+ installed:

### On Different Computers (Windows/Mac/Linux)

1. **Server Machine**: Run the server as described above. Note the machine's IP address (e.g., `192.168.1.100`).

2. **Client Machines**: 
   - Install Java 17+ if not already installed
   - Copy the entire `scribble-v2` project folder
   - Run `./gradlew :scribble-client:run` (use `gradlew.bat` on Windows)
   - In the login screen, enter the server's IP address as the Host

### Network Requirements

- All devices must be on the same local network (LAN)
- Firewall should allow TCP connections on the server port (default 5555)
- For internet play, port forwarding would be needed (not recommended for security reasons)

### Mobile Devices

While JavaFX is primarily desktop-oriented, you can run the client on:
- Linux-based mobile devices with Java support
- Android devices with Termux (requires additional setup for JavaFX)

Note: Mobile support is experimental and may require custom builds.

## Gameplay Guide

### Creating a Room

1. After connecting, click "Create Room"
2. Set room options:
   - Max players (2-10)
   - Round time (60-300 seconds)
   - Total rounds (1-20)

### Joining a Room

- Click "Join Room" and select from the list
- Or enter a specific room code if provided

### Game Flow

1. **Word Selection**: The drawer gets 3 word choices and 10 seconds to pick one
2. **Drawing Phase**: Drawer sketches the word while others guess
3. **Guessing**: Players type guesses in chat; correct guesses score points
4. **Hints**: Word hints are revealed gradually (e.g., _ _ _ _ _ → A _ _ _ _)
5. **Scoring**: Points awarded based on guess speed and order

### Special Features

- **Undo/Clear**: Drawer can correct mistakes
- **Spectator Mode**: Join full or in-progress rooms to watch and chat
- **Reconnection**: Clients automatically reconnect if connection drops

## Configuration

Key settings are in `scribble-shared/src/main/java/com/scribble/utils/Constants.java`:

- Default port, round durations, scoring multipliers
- Word reveal timings and hint system
- Connection timeouts and reconnect settings

## Troubleshooting

### Client Issues
- **Unstyled UI**: Ensure `style.css` is present and running via Gradle
- **Connection Failed**: Check server is running and port/host are correct
- **Word not loading**: Rebuild after protocol changes

### Server Issues
- **Port in use**: Choose a different port with `--args`
- **No connections**: Verify firewall allows the port

### Multi-Device Issues
- **Can't connect from other devices**: Use server's IP address, not localhost
- **Connection timeouts**: Check network connectivity and firewall settings

## Development

### Project Structure

```
scribble-v2/
├── scribble-shared/     # Common code
├── scribble-server/     # Server application
├── scribble-client/     # JavaFX client
├── build.gradle         # Root build file
└── settings.gradle      # Module definitions
```

### Adding Features

- Shared protocol changes require rebuilding all modules
- UI changes are in `scribble-client/src/main/resources/fxml/`
- Styling in `scribble-client/src/main/resources/css/style.css`

## License

[Add your license here]

## Contributing

[Add contribution guidelines here]
