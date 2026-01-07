# Scribble V2 Project Report

## 1. Overview
Scribble V2 is a real-time multiplayer drawing and guessing game (Pictionary/Skribbl style). It is built with Java 17, a JavaFX desktop client, and a TCP socket server. The system supports multiple rooms, live drawing synchronization, timed word hints, spectator mode, scoring based on guess speed, and reconnection handling. The codebase is split into three modules: `scribble-client` (UI + networking), `scribble-server` (game server + monitoring), and `scribble-shared` (shared message protocol and utilities).

## 2. Motivation
The project was chosen to practice building a real-time networked application that combines concurrent server design, consistent game state management, and a responsive user interface. It also provides a practical way to apply networking concepts like socket programming, latency handling, rate limiting, and reliable data transfer.

## 3. Problem Statement
Design and implement a reliable multiplayer drawing/guessing game where multiple clients connect to a central server, create or join rooms, synchronize drawing actions in real time, and play timed rounds with fair scoring. The system must handle disconnects, keep state consistent for all players, and protect against spamming or abuse.

## 4. Design Goals/Objectives
- Real-time drawing synchronization with minimal delay.
- Room-based gameplay with configurable settings.
- Fair scoring based on guess speed and order.
- Robust client/server communication with timeouts and reconnects.
- Smooth, user-friendly JavaFX interface.
- Monitoring and statistics for network performance.

## 5. Project Features
- Live drawing broadcast with undo and clear tools.
- Word choice phase with three options and a timer.
- Timed hint reveals during drawing.
- Multiple rooms with configurable settings.
- Spectator mode for full rooms or late joiners.
- Chat-based guessing with close-guess hints.
- Scoring based on guess timing and order + drawer bonus.
- Auto-reconnect with exponential backoff.
- Rate limiting and mute/kick system for spam control.
- Stroke history sync for late joiners.
- Client and server network monitoring dashboards.

## 6. Block Diagram / Workflow Diagram

High-level architecture:
```
+-------------------+         TCP Socket          +---------------------+
| JavaFX Client     | <-------------------------> | Scribble Server     |
| - UI controllers  |     ObjectInput/Output      | - ServerSocket      |
| - Draw batching   |                             | - ClientHandler     |
| - Reconnect       |                             | - Room/Game logic   |
+-------------------+                             +---------------------+
        ^                                                     |
        | (metrics subscription)                              |
        v                                                     v
+-------------------+                             +---------------------+
| Client Monitor UI |                             | Admin Monitor UI    |
+-------------------+                             +---------------------+
```

Gameplay flow:
```
Login -> Lobby -> (Create/Join Room)
   -> Word Choice (drawer selects word)
   -> Drawing + Guessing (timed)
   -> Intermission (score summary)
   -> Next Round / Finish
   -> Play Again / Exit
```

## 7. Tools & Technologies
- Language: Java 17
- UI: JavaFX (FXML + CSS)
- Build: Gradle
- Networking: TCP sockets (Java ServerSocket/Socket)
- Serialization: Java ObjectInputStream/ObjectOutputStream
- Shared Protocol: custom serializable message classes

## 8. Applied Networking Concepts
- Client-Server Model: a central server manages rooms and game state.
- TCP Sockets: reliable, ordered delivery via `ServerSocket` and `Socket`.
- Reliable Data Transfer: TCP guarantees delivery; ping/pong confirms liveness.
- Flow Control:
  - Draw batching (`DRAW_BATCH_INTERVAL_MS` and `DRAW_BATCH_SIZE`).
  - Rate limiting on draw/chat/guess messages.
  - Per-client outgoing queue in the server.
- Congestion Handling: app-level throttling and batching reduce message bursts.
- Heartbeats & Timeouts: ping/pong with disconnect after timeouts.
- Reconnection Strategy: exponential backoff and auto rejoin.
- Latency Measurement: pong timestamp to compute round-trip time.
- Monitoring: server tracks bytes/messages per client/room; client displays stats.

## 9. Implementation Details

### 9.1 Modules
- `scribble-shared`: shared protocol messages, constants, validation helpers.
- `scribble-server`: server entry, client handler, room/game state logic.
- `scribble-client`: JavaFX UI, network client, event handling.

### 9.2 Message Protocol
All communication is done via serializable message classes (e.g., `JoinMessage`, `DrawMessage`, `GuessMessage`, `GameStateMessage`). This ensures consistent message structures for both client and server.

### 9.3 Server Architecture
- `Server` listens for connections and spawns a `ClientHandler` per client.
- `ClientHandler` runs a reader loop and a writer thread with an outgoing queue.
- A heartbeat scheduler pings clients and disconnects timed-out sockets.
- Rooms are created on demand and stored in a concurrent map.

### 9.4 Room/Game State Machine
Phases: WAITING -> CHOOSING_WORD -> RUNNING -> INTERMISSION -> FINISHED

Step-by-step:
1. If active players >= 2 and phase is WAITING, start a round.
2. Select a drawer and send 3 word choices with a countdown.
3. Drawer selects a word (or server auto-selects on timeout).
4. RUNNING: draw messages broadcast and guesses evaluated.
5. Round ends when all guess or timer expires.
6. Intermission shows summary, then next round or finish.
7. Finished state supports play-again voting.

### 9.5 Scoring Algorithm
```
points = GUESS_BASE_POINTS + timeLeft * GUESS_TIME_MULTIPLIER
if first guess: add FIRST_GUESS_BONUS
cap at max based on total round time
```
Drawer bonus: `DRAWER_BONUS_PER_GUESS` for each correct guess.

### 9.6 Hint Reveal Algorithm
- Hints reveal at configured percentages of remaining time.
- Modes: OFF, LIGHT (one reveal), NORMAL (two reveals).

### 9.7 Drawing Synchronization
- Client batches points to reduce network load.
- Each stroke has a unique ID; server stores stroke history.
- Late joiners receive stroke history to reconstruct the board.
- Undo removes the last stroke and rebroadcasts history.

### 9.8 Guess Processing
- Guesses are normalized before comparison.
- Exact match = correct guess + score update.
- Close guess detected using edit distance, sends a hint.

### 9.9 Robustness & Abuse Control
- Name/room/message validation.
- Rate limiting for chat/guess/draw.
- Violation system escalates to warnings, mute, or kick.

### 9.10 Monitoring
- Server collects network metrics per client/room.
- Client shows latency, throughput, and message statistics.

## 10. Result Analysis
Provide screenshots with captions such as:
- Login screen: user enters name, host, port.
- Lobby screen: create/join room list.
- Room screen: drawing, chat, timer, and word hints.
- Word choice overlay: drawer selecting from 3 words.
- Intermission/leaderboard: round summary and scores.
- Client network stats panel: latency and throughput.
- Server admin monitor: live server metrics dashboard.

## 11. Summary of the Project
Scribble V2 is a complete real-time multiplayer drawing and guessing game with robust networking, state management, and UI. The project integrates concurrent server architecture, a shared message protocol, and a feature-rich client while applying practical networking principles such as reliable transport, latency measurement, and rate control.

## 12. Limitations and Future Plan
Limitations:
- Java serialization for protocol (not ideal for versioning/security).
- Single server instance; no scaling support.
- No authentication or encryption.
- No persistent storage for users or game history.
- Mobile support is experimental.

Future Plan:
- Replace serialization with JSON/Protobuf + versioning.
- Add authentication and TLS encryption.
- Add persistence for scores and history.
- Build a web or mobile client.
- Improve moderation and configurable word lists.
- Scale server using multiple instances/sharding.
