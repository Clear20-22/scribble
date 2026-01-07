# Scribble V2 Viva Preparation Pack

This README is a complete viva preparation pack aligned with the report structure.
It is based only on this repository.

## Project Map
- `scribble-v2/scribble-server/src/main/java/com/scribble/server/ServerMain.java`: server entry point and flag parsing.
- `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`: socket accept loop, client registry, room management, heartbeats.
- `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`: per-client I/O loop, message routing, rate limits, ping/pong.
- `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`: room state machine, rounds, word choice, drawing, guesses, scoring.
- `scribble-v2/scribble-server/src/main/java/com/scribble/game/Scoring.java`: score formula utilities.
- `scribble-v2/scribble-server/src/main/java/com/scribble/game/WordProvider.java`: word list loading and random selection.
- `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/Message.java`: base protocol message.
- `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/*`: all message types (draw, guess, chat, join, state).
- `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/Constants.java`: timing, limits, and tuning constants.
- `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/ValidationUtils.java`: name/room/chat validation.
- `scribble-v2/scribble-client/src/main/java/com/scribble/client/Client.java`: TCP client, reconnect, message handling.
- `scribble-v2/scribble-client/src/main/java/com/scribble/client/ui/LoginController.java`: login screen behavior.
- `scribble-v2/scribble-client/src/main/java/com/scribble/client/ui/LobbyController.java`: lobby, create/join room.
- `scribble-v2/scribble-client/src/main/java/com/scribble/client/ui/RoomController.java`: drawing UI, chat, word choice overlay.
- `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`: Admin Monitor UI.
- `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/*`: monitoring stats, snapshots, packet log.

## VIVA Question Bank (60 total)
Each question includes: difficulty, answer, files, and follow-ups.

### 1) Overview
1. Easy: What is Scribble V2?
   - Answer: A real-time multiplayer drawing and guessing game with a JavaFX client and a TCP socket server.
   - Files: `scribble-v2/README.md`, `scribble-v2/scribble-client/src/main/java/com/scribble/client/ClientApp.java`
   - Follow-ups: How many modules exist? What is the default port?
2. Easy: What are the main modules?
   - Answer: `scribble-client` for UI, `scribble-server` for gameplay and networking, and `scribble-shared` for protocol/utilities.
   - Files: `scribble-v2/settings.gradle`, `scribble-v2/README.md`
   - Follow-ups: Why is `scribble-shared` needed? What happens if protocol changes?
3. Medium: How does the Admin Monitor fit into the system?
   - Answer: It reads server metrics snapshots and visualizes throughput, RTT, and packet logs for observability.
   - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/ServerNetworkStats.java`
   - Follow-ups: What data structure stores packet logs? How often is UI updated?
4. Medium: Why use TCP sockets here?
   - Answer: TCP provides reliable, ordered delivery which is critical for drawing/guess synchronization.
   - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`, `scribble-v2/scribble-client/src/main/java/com/scribble/client/Client.java`
   - Follow-ups: What does the protocol use for serialization? What happens on disconnect?
5. Hard: What is the highest-level game state machine?
   - Answer: Room phases: WAITING -> CHOOSING_WORD -> RUNNING -> INTERMISSION -> FINISHED.
   - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`, `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/GamePhase.java`
   - Follow-ups: What transitions reset the room? How are rounds advanced?

### 2) Motivation
6. Easy: Why build this project?
   - Answer: To practice real-time networking, concurrency, and UI synchronization.
   - Files: `PROJECT_REPORT.md`
   - Follow-ups: What part is hardest? What concept was new?
7. Easy: Why JavaFX?
   - Answer: It provides a desktop UI framework integrated with Java.
   - Files: `scribble-v2/scribble-client/src/main/resources/fxml/*.fxml`
   - Follow-ups: Where is CSS applied? What UI views exist?
8. Medium: What networking concepts were the main focus?
   - Answer: TCP socket handling, reliable delivery, heartbeats, timeouts, and app-level flow control.
   - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/Constants.java`
   - Follow-ups: How is RTT measured? Where are rate limits enforced?
9. Medium: Why include a monitoring UI?
   - Answer: To make network behavior observable (throughput, RTT, packet logs) for analysis and debugging.
   - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`
   - Follow-ups: What metrics are shown in Overview? How are graphs populated?
10. Hard: What trade-off does serialization create?
    - Answer: Java serialization is simple but not ideal for versioning/security.
    - Files: `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/Message.java`
    - Follow-ups: What would you replace it with? How would you migrate?

### 3) Problem Statement
11. Easy: State the core problem.
    - Answer: Build a reliable, real-time multiplayer drawing game with synchronized state and fair scoring.
    - Files: `PROJECT_REPORT.md`
    - Follow-ups: What is the minimum player count? Where is it defined?
12. Easy: Why must state be consistent?
    - Answer: All clients must see the same drawing, word hints, and scores.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
    - Follow-ups: How do late joiners sync? Which message sends history?
13. Medium: How is disconnect handling part of the problem?
    - Answer: The server must handle timeouts and allow reconnects without corrupting game state.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`, `scribble-v2/scribble-client/src/main/java/com/scribble/client/Client.java`
    - Follow-ups: What triggers a timeout? How is reconnection delayed?
14. Medium: Why is rate limiting necessary?
    - Answer: To prevent spamming that harms fairness and network stability.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/Constants.java`
    - Follow-ups: What is the draw rate limit? What happens on violation?
15. Hard: What makes drawing sync non-trivial?
    - Answer: Draws are continuous streams, so batching and stroke history are required for late joiners.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`, `scribble-v2/scribble-client/src/main/java/com/scribble/client/ui/RoomController.java`
    - Follow-ups: How are strokes identified? How does undo work?

### 4) Design Goals/Objectives
16. Easy: Name two goals.
    - Answer: Real-time sync and fair scoring.
    - Files: `PROJECT_REPORT.md`, `scribble-v2/scribble-server/src/main/java/com/scribble/game/Scoring.java`
    - Follow-ups: How is scoring based on time? How are rounds timed?
17. Easy: What is the UI goal?
    - Answer: Provide an easy, responsive JavaFX interface.
    - Files: `scribble-v2/scribble-client/src/main/resources/fxml/*.fxml`
    - Follow-ups: Where is the lobby? Where is the room screen?
18. Medium: How is robustness achieved?
    - Answer: Heartbeats, timeouts, validation, and rate limiting.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/ValidationUtils.java`
    - Follow-ups: What validation rules exist? Where is ping/pong handled?
19. Medium: How is scalability addressed in current design?
    - Answer: Multiple rooms are supported on a single server instance.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`
    - Follow-ups: What is the max players per room? How are rooms tracked?
20. Hard: How does monitoring support the goals?
    - Answer: It exposes throughput/RTT so performance issues can be diagnosed quickly.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/ServerNetworkStats.java`
    - Follow-ups: What is the update interval? Where are metrics sampled?

### 5) Project Features
21. Easy: List two gameplay features.
    - Answer: Word choice timer and hint reveal.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
    - Follow-ups: How many word choices? What is hint mode?
22. Easy: What room features exist?
    - Answer: Multiple rooms, custom settings, spectator mode.
    - Files: `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/RoomSettings.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
    - Follow-ups: When is someone forced to spectator? Can spectators draw?
23. Medium: How does undo and clear work?
    - Answer: The drawer can remove last stroke or clear the board; server rebroadcasts history.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
    - Follow-ups: What data stores strokes? What message is broadcast on clear?
24. Medium: How does the monitoring UI log packets?
    - Answer: Server logs recent TX/RX packets into a ring buffer and UI lists them.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/PacketLogEntry.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/ServerNetworkStats.java`
    - Follow-ups: How many entries are kept? How are broadcast packets marked?
25. Hard: What feature supports late joiners?
    - Answer: Stroke history sync via `StrokeHistoryMessage`.
    - Files: `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/StrokeHistoryMessage.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
    - Follow-ups: When is history sent? How is it stored?

### 6) Block Diagram / Workflow Diagram
26. Easy: Describe the high-level architecture.
    - Answer: JavaFX clients connect via TCP to a central server that manages rooms and game state.
    - Files: `scribble-v2/README.md`
    - Follow-ups: What is the default port? Where is it defined?
27. Easy: What is the gameplay flow?
    - Answer: Login -> Lobby -> Create/Join room -> Word choice -> Draw/Guess -> Intermission -> Next round.
    - Files: `scribble-v2/scribble-client/src/main/java/com/scribble/client/ui/RoomController.java`
    - Follow-ups: Where is the word choice overlay? How is intermission triggered?
28. Medium: Where does the server track room state?
    - Answer: In `Room.java` using phase fields and timers.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
    - Follow-ups: What starts a round? What ends a round?
29. Medium: Where is client-server message routing?
    - Answer: `ClientHandler.handleMessage` routes each message to room/server logic.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`
    - Follow-ups: How are unknown messages handled? Where are rate limits applied?
30. Hard: How is monitoring data produced?
    - Answer: ServerNetworkStats aggregates counters and time-series and AdminMonitorApp renders them every second.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/ServerNetworkStats.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`
    - Follow-ups: Where is `ServerStatsSnapshot` created? How is UI thread safety ensured?

### 7) Tools & Technologies
31. Easy: What language and UI toolkit?
    - Answer: Java 17 and JavaFX.
    - Files: `scribble-v2/README.md`
    - Follow-ups: Where are FXML files? Where is CSS?
32. Easy: What build system is used?
    - Answer: Gradle wrapper.
    - Files: `scribble-v2/build.gradle`, `scribble-v2/gradlew`
    - Follow-ups: How to run client? How to run server?
33. Medium: What protocol format is used?
    - Answer: Java ObjectInputStream/ObjectOutputStream serialization.
    - Files: `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/Message.java`
    - Follow-ups: What are risks of serialization? How are messages versioned?
34. Medium: What data structures are used for stats?
    - Answer: `LongAdder`, concurrent maps, ring buffers for time series.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/ServerNetworkStats.java`
    - Follow-ups: Why use LongAdder? How is ring size configured?
35. Hard: What is the threading model?
    - Answer: Server has accept loop, per-client handler thread, and a writer thread per client plus schedulers.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
    - Follow-ups: Where is the writer thread created? What schedules room ticks?

### 8) Applied Networking Concepts
36. Easy: Where are TCP sockets created?
    - Answer: In `Server` for listening and `Client` for connecting.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`, `scribble-v2/scribble-client/src/main/java/com/scribble/client/Client.java`
    - Follow-ups: What port is used by default? How is it configured?
37. Easy: How is reliability achieved?
    - Answer: TCP ensures reliable ordered delivery; app tracks message counts.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/ServerNetworkStats.java`
    - Follow-ups: How are bytes counted? Where are message types tracked?
38. Medium: How are heartbeats implemented?
    - Answer: Server sends Ping, client responds with Pong; RTT is measured.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/PingMessage.java`
    - Follow-ups: When is timeout checked? Where is RTT stored?
39. Medium: What is app-level flow control here?
    - Answer: Rate limits on draw/chat/guess and batching of draw points.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/Constants.java`
    - Follow-ups: What are the millisecond limits? How are violations handled?
40. Hard: How does the monitor prove broadcast fan-out?
    - Answer: Room stats count broadcasts and Admin UI shows fan-out per player and packet logs with recipient counts.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/RoomNetworkStats.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`
    - Follow-ups: How is recipientCount logged? Where is fan-out computed?

### 9) Implementation Details
41. Easy: How are rooms created?
    - Answer: Client sends `CreateRoomMessage`, server validates and creates `Room`.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`
    - Follow-ups: How is room name validated? Where are settings stored?
42. Easy: How are word choices sent?
    - Answer: Server picks 3 words and sends `WordChoicesMessage` to the drawer.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
    - Follow-ups: What happens on timeout? Where is word list loaded?
43. Medium: How are guesses processed?
    - Answer: Room checks exact match for scoring and uses edit distance for close guesses.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
    - Follow-ups: How are guesses normalized? Where is close guess hint sent?
44. Medium: How is scoring computed?
    - Answer: Base points plus time-based bonus and first-guess bonus, capped by round time.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Scoring.java`, `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/Constants.java`
    - Follow-ups: Who gets drawer bonus? Where is it awarded?
45. Hard: How is room phase advanced?
    - Answer: Timers trigger transitions between choosing, running, intermission, and finish.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
    - Follow-ups: What happens if players leave? How does play-again reset?

### 10) Result Analysis
46. Easy: What is the best UI screenshot for joining?
    - Answer: Login screen and lobby list showing room entries.
    - Files: `scribble-v2/scribble-client/src/main/resources/fxml/Login.fxml`, `scribble-v2/scribble-client/src/main/resources/fxml/Lobby.fxml`
    - Follow-ups: What fields are in login? How is room list requested?
47. Easy: What shows drawing sync?
    - Answer: Room screen with live strokes and chat guesses.
    - Files: `scribble-v2/scribble-client/src/main/resources/fxml/Room.fxml`
    - Follow-ups: Where is the canvas? Where are chats displayed?
48. Medium: What shows word choice?
    - Answer: Word choice overlay with countdown timer.
    - Files: `scribble-v2/scribble-client/src/main/java/com/scribble/client/ui/RoomController.java`
    - Follow-ups: How is timer updated? Where are buttons created?
49. Medium: What shows monitoring results?
    - Answer: Admin Monitor Overview + Graphs + Packet Log.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`
    - Follow-ups: Which graph shows msgs/sec? How to filter packet logs?
50. Hard: What screen proves fan-out?
    - Answer: Rooms table with Fan-out/Player and broadcast counts.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`
    - Follow-ups: How is fan-out computed? What does it imply?

### 11) Summary
51. Easy: Summarize the project in one line.
    - Answer: A real-time, TCP-based multiplayer drawing game with monitoring.
    - Files: `scribble-v2/README.md`
    - Follow-ups: Which module runs the UI? Which module runs the server?
52. Easy: What is the biggest success?
    - Answer: Stable real-time sync and structured message protocol.
    - Files: `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/Message.java`
    - Follow-ups: How are messages decoded? Where is message routing?
53. Medium: How does the project show networking knowledge?
    - Answer: It uses heartbeats, rate limits, and monitoring to demonstrate network behavior.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`
    - Follow-ups: What rate limits exist? What does RTT measure?
54. Medium: What would you highlight in a demo?
    - Answer: Word choice, live drawing, scoring, and monitor graphs.
    - Files: `scribble-v2/scribble-client/src/main/java/com/scribble/client/ui/RoomController.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`
    - Follow-ups: Which graph indicates flow control? Which view shows scores?
55. Hard: What is the biggest architectural risk?
    - Answer: Java serialization limitations for security/versioning and single-server scaling.
    - Files: `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/Message.java`
    - Follow-ups: How would you migrate protocol? How to scale rooms?

### 12) Limitations & Future Plan
56. Easy: Name one limitation.
    - Answer: No authentication or encryption.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`
    - Follow-ups: What would you add? Where would token check go?
57. Easy: Name a future plan.
    - Answer: Replace serialization with JSON or Protobuf.
    - Files: `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/Message.java`
    - Follow-ups: How would you version messages? How to handle old clients?
58. Medium: What scaling issue exists?
    - Answer: Single server instance handles all rooms; no sharding or clustering.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`
    - Follow-ups: How would you shard by room? What state must be shared?
59. Medium: What observability gap remains?
    - Answer: No persistent metrics storage; only live view.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/ServerNetworkStats.java`
    - Follow-ups: How would you log to file? Which metrics are most important?
60. Hard: How would you add persistence?
    - Answer: Add storage for player accounts and game history, decoupled from real-time path.
    - Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java` (integration point)
    - Follow-ups: What data schema would you store? How to avoid latency impact?

## Networking Concept Mapping (Section 8)
Concept | Where in code | How it works | What the monitor proves
---|---|---|---
TCP sockets | `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`, `scribble-v2/scribble-client/src/main/java/com/scribble/client/Client.java` | ServerSocket accepts clients, clients connect with Socket | Connections count and session durations
Serialization protocol | `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/Message.java` | ObjectInput/Output streams send typed messages | Packet log shows message types/bytes
Reliability & ordering | TCP stack + message counts | TCP guarantees ordered delivery | Message sent/received totals and bytes
Heartbeat | `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java` | PingMessage, PongMessage update RTT | RTT values in Clients table
Timeout | `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/Constants.java` | If no pong in time, disconnect | Disconnect counters and session end
Reconnection | `scribble-v2/scribble-client/src/main/java/com/scribble/client/Client.java` | Exponential backoff reconnect | Reconnect count and connection timeline
Flow control (app-level) | `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java` | Rate limits on draw/chat/guess | Rate limited counters and msgs/sec graphs
Broadcast fan-out | `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java` | Server broadcasts to room players | Rooms table fan-out/Player + packet log recipient counts
Congestion control vs throttling | TCP internal vs app limits | We do not modify TCP congestion control; we implement application-level flow control, while TCP handles congestion internally. | Monitor shows app throttling separate from TCP

## Implementation Walkthrough (Section 9)
Each algorithm references exact code locations.

### Room lifecycle (create/join/start)
Algorithm:
1. Client sends CreateRoom or Join message.
2. Server validates room name and settings.
3. Server creates room or fetches existing.
4. Player is added; may become spectator if full or in-progress.
5. If enough players, room starts a round.
Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`

### Word selection phase (timer + autopick)
Algorithm:
1. Room enters CHOOSING_WORD and sends 3 choices.
2. Countdown decrements per second.
3. If drawer picks, word is selected.
4. If time expires, server auto-selects a word.
Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/game/WordProvider.java`

### Drawing sync (batching + broadcast + undo/clear + history)
Algorithm:
1. Client batches points and sends DrawMessage.
2. Server validates and stores strokes by ID.
3. Server broadcasts to all other players.
4. Undo removes last stroke and rebroadcasts history.
5. Clear clears strokes and broadcasts ClearMessage.
6. Late joiners request stroke history and receive it.
Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`, `scribble-v2/scribble-client/src/main/java/com/scribble/client/ui/RoomController.java`

### Guess handling + close guess
Algorithm:
1. GuessMessage arrives, validated.
2. If exact match and not already guessed, award points.
3. If close, send GuessHintMessage.
4. Broadcast guess chat message.
Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`

### Scoring + round summary
Algorithm:
1. Compute points based on time left and order.
2. Add drawer bonus for correct guesses.
3. Intermission broadcasts round summary.
Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Scoring.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`

### Heartbeat ping/pong + timeout
Algorithm:
1. Server sends PingMessage at interval.
2. Client replies with PongMessage.
3. Server updates RTT; if timeout exceeded, disconnect.
Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/Constants.java`

### Auto reconnect with exponential backoff
Algorithm:
1. On disconnect, client schedules reconnect.
2. Delay doubles each time up to max.
3. On success, delay resets and JoinMessage is sent.
Files: `scribble-v2/scribble-client/src/main/java/com/scribble/client/Client.java`

### Rate limiter logic (draw/chat/guess)
Algorithm:
1. Store last timestamp for each message type.
2. If new message arrives too soon, block and record rate limit.
3. On repeated violations, warn/mute/kick.
Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`, `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/Constants.java`

### Admin monitor metrics pipeline
Algorithm:
1. Server stats counters updated on send/receive/broadcast.
2. Periodic scheduler samples throughput and system metrics.
3. Snapshot created for UI.
4. Admin UI updates graphs and tables every second.
Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/ServerNetworkStats.java`, `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`

## Result Analysis Guide (Section 10)
Suggested screenshots and captions:
- Login screen: "User enters name, host, and port for TCP connection."  
  Concept: connection setup.  
  Files: `scribble-v2/scribble-client/src/main/resources/fxml/Login.fxml`
- Lobby room list: "Active rooms and settings before joining."  
  Concept: room discovery.  
  Files: `scribble-v2/scribble-client/src/main/resources/fxml/Lobby.fxml`
- Room word choice overlay: "Drawer selects word with countdown timer."  
  Concept: timed control messages.  
  Files: `scribble-v2/scribble-client/src/main/java/com/scribble/client/ui/RoomController.java`
- Drawing canvas + chat: "Live drawing broadcast with chat guesses."  
  Concept: real-time sync and reliable delivery.  
  Files: `scribble-v2/scribble-client/src/main/resources/fxml/Room.fxml`
- Scoreboard/intermission: "Round summary and scores update."  
  Concept: consistent state and scoring.  
  Files: `scribble-v2/scribble-server/src/main/java/com/scribble/game/Room.java`
- Admin Monitor Overview: "Server-wide throughput and connection stats."  
  Concept: flow control and session management.  
  Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`
- Admin Monitor Graphs: "Messages/sec and throughput over time."  
  Concept: flow control/throughput analysis.  
  Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`
- Packet Log: "Recent packets with direction and size."  
  Concept: protocol visibility and ordering.  
  Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/PacketLogEntry.java`
- Rooms table: "Broadcast counts and fan-out per player."  
  Concept: multicast/fan-out cost.  
  Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/admin/AdminMonitorApp.java`

## Limitations & Future Plan (Section 12)
Limitations grounded in code:
- Java serialization is not ideal for versioning/security.  
  Files: `scribble-v2/scribble-shared/src/main/java/com/scribble/network/messages/Message.java`
- Single server instance; no horizontal scaling.  
  Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`
- No authentication or TLS encryption.  
  Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/ClientHandler.java`
- No persistence for users/scores/history.  
  Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/Server.java`
- Admin monitor is live-only without persistent logs.  
  Files: `scribble-v2/scribble-server/src/main/java/com/scribble/server/monitor/ServerNetworkStats.java`

Future plan ideas tied to current architecture:
- Replace serialization with JSON/Protobuf + versioning, keep same message routing.
- Add TLS and authentication tokens in `ClientHandler`.
- Persist scores and match history via a DB layer triggered on round end.
- Add room sharding (multi-server) with a lobby coordinator.
- Export monitoring data to CSV/JSON periodically for analysis.
