# 🎨 Scribble - Real-Time Drawing Game

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue)](https://maven.apache.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-green)](https://openjfx.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

A **real-time multiplayer drawing game** built with Java, featuring client-server architecture, live drawing synchronization, chat, and turn-based gameplay. Inspired by Pictionary, players take turns drawing words while others guess in real-time!

![Scribble Game Demo](demo-screenshot.png) <!-- Add a screenshot if available -->

## ✨ Features

### 🎮 Core Gameplay

- **Real-Time Drawing**: Smooth, synchronized drawing across all players in a room
- **Turn-Based Rounds**: Random player selection for drawing, 60-second timer per round
- **Word Guessing**: Players chat to guess the word; correct guesses award points
- **Multiple Rooms**: Create or join different game rooms simultaneously
- **Scoring System**: Points for correct guesses, leaderboard display

### 💬 Communication

- **In-Room Chat**: Real-time messaging for all players in the same room
- **System Messages**: Automatic notifications for game events (e.g., correct guesses, round starts)

### 🎨 Drawing Tools

- **Color Palette**: 6 colors (Black, Red, Blue, Green, Yellow, Purple)
- **Brush Size Control**: Adjustable stroke width (1-20 pixels)
- **Canvas Clearing**: Drawer can clear the canvas, synced to all players
- **Responsive Canvas**: 800x600 drawing area with mouse drag support

### 🌐 Networking

- **TCP Socket Communication**: Reliable, ordered message delivery
- **Thread-Safe Architecture**: Concurrent handling of multiple clients and rooms
- **LAN Support**: Play across multiple computers on a local network
- **Graceful Disconnections**: Automatic cleanup when players leave

### 🖥️ User Interface

- **JavaFX GUI**: Modern, responsive interface
- **Player List**: Real-time display of connected players and scores
- **Game Info Panel**: Shows current drawer, word (hidden for guessers), timer
- **Intuitive Controls**: Easy-to-use color buttons, slider, and clear button

## 🏗️ Architecture

### Client-Server Model

```
┌─────────────────┐    TCP Sockets    ┌─────────────────┐
│   Client GUI    │◄─────────────────►│     Server      │
│  (JavaFX App)   │                   │                 │
│                 │                   │ ┌─────────────┐ │
│ ┌─────────────┐ │                   │ │   Rooms     │ │
│ │   Canvas    │ │                   │ │ ┌─────────┐ │ │
│ │   Chat      │ │                   │ │ │ Players │ │ │
│ │   Controls  │ │                   │ │ └─────────┘ │ │
│ └─────────────┘ │                   │ └─────────────┘ │
└─────────────────┘                   └─────────────────┘
```

### Key Components

- **Server**: Manages connections, rooms, and game logic
- **Room**: Handles players, game state, broadcasting
- **ClientHandler**: Processes individual client messages
- **Client**: Sends/receives messages, updates GUI
- **ClientGUI**: JavaFX interface for drawing and interaction

### Message Types

- `JoinMessage`: Join/create rooms
- `DrawMessage`: Drawing strokes
- `ChatMessage`: Player messages and guesses
- `GameStateMessage`: Round info, scores
- `ClearMessage`: Canvas clearing
- `PlayerListMessage`: Connected players

## 🚀 Quick Start

### Prerequisites

- **Java 21** or higher
- **Maven 3.9+**
- Network connectivity for multiplayer

### Installation

1. **Clone the repository**:

   ```bash
   git clone https://github.com/Mim1726/scribble.git
   cd scribble/scribble-game
   ```

2. **Build the project**:
   ```bash
   mvn clean compile
   ```

### Running the Game

#### Start the Server

```bash
mvn exec:java -Dexec.mainClass="com.scribble.Server"
```

- Server starts on port 12345
- Keeps running to accept client connections

#### Start Clients

Open new terminals for each player:

```bash
mvn javafx:run
```

- Enter your player name
- Enter room name (e.g., "Room1")
- GUI opens - start drawing and chatting!

### LAN Multiplayer

1. On the server machine, find your IP address:

   ```bash
   ipconfig  # Windows
   ifconfig  # Linux/Mac
   ```

2. In `ClientGUI.java`, change `"localhost"` to the server's IP:

   ```java
   if (!client.connect("192.168.1.100", 12345)) {  // Replace with server IP
   ```

3. Run server on one machine, clients on others using the IP.

## 📁 Project Structure

```
scribble-game/
├── pom.xml                          # Maven configuration
├── README.md                        # This file
└── src/main/java/com/scribble/
    ├── Main.java                    # Client launcher
    ├── Server.java                  # Server main class
    ├── Room.java                    # Game room logic
    ├── ClientHandler.java           # Client connection handler
    ├── Client.java                  # Client communication
    ├── ClientGUI.java               # JavaFX GUI
    ├── Message.java                 # Message base class
    ├── JoinMessage.java             # Join room message
    ├── DrawMessage.java             # Drawing message
    ├── ChatMessage.java             # Chat message
    ├── GameStateMessage.java        # Game state message
    ├── ClearMessage.java            # Clear canvas message
    ├── GuessMessage.java            # Guess message
    └── PlayerListMessage.java       # Player list message
```

## 🛠️ Technologies Used

- **Java 21**: Core language and runtime
- **JavaFX 21**: GUI framework for client interface
- **Maven**: Build automation and dependency management
- **TCP Sockets**: Network communication
- **Object Streams**: Message serialization
- **ExecutorService**: Thread pool for concurrent clients
- **ConcurrentHashMap**: Thread-safe data structures

## 🎯 How to Play

1. **Join a Room**: Enter name and room name
2. **Wait for Players**: Minimum 1 player to start
3. **Drawing Turn**: Randomly selected player draws the word
4. **Guessing**: Others type guesses in chat
5. **Scoring**: Correct guess = 10 points
6. **Round End**: Timer expires or word guessed
7. **Repeat**: New drawer selected automatically

### Controls

- **Draw**: Click and drag on canvas
- **Colors**: Click color buttons to change
- **Brush Size**: Drag slider (1-20)
- **Clear**: Click "Clear Canvas" (drawer only)
- **Chat**: Type in text field, press Enter

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature-name`
3. Commit changes: `git commit -am 'Add feature'`
4. Push to branch: `git push origin feature-name`
5. Submit a Pull Request

### Development Setup

```bash
# After cloning
mvn clean compile
mvn exec:java -Dexec.mainClass="com.scribble.Server"  # Terminal 1
mvn javafx:run  # Terminal 2 (Client)
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👥 Authors

- **Jubayer Ahmed Sojib** - _Initial work_ - [Mim1726](https://github.com/Mim1726)

## 🙏 Acknowledgments

- Inspired by Pictionary and similar drawing games
- Built with JavaFX for modern GUI experience
- Thanks to the open-source community for Java and Maven

## 📞 Support

If you encounter issues:

1. Check that Java 21+ and Maven are installed
2. Ensure server is running before starting clients
3. For LAN play, verify firewall allows port 12345
4. Check console output for error messages

---

**Enjoy drawing and guessing with friends! 🎨🤝**
