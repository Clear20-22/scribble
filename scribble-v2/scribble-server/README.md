# Scribble Server Network Monitoring

A server-side monitoring system for Scribble (Skribbl.io clone). When the
server starts, an Admin Monitor dashboard (JavaFX) opens unless `--headless`
is set. The dashboard shows live metrics, graphs, client connections, room
activity, and packet logs.

## Features

- Global server stats (connections, messages, bytes, throughput, message types)
- Per-client stats (session duration, room, TX/RX counts, bytes, RTT)
- Per-room stats (players, broadcasts, draws, guesses, rounds)
- Packet log ring buffers (global + per-client)
- JVM telemetry (heap, threads, GC time)
- Live graphs for throughput, connections, and JVM memory

## Architecture (high level)

```
+------------------+            +---------------------------+
| ClientHandler    |  TX/RX     | ServerNetworkStats        |
| (per socket)     |----------->| - global counters         |
| - send/receive   |            | - time-series buffers     |
| - ping/pong RTT  |            | - per-client snapshots    |
+--------+---------+            | - per-room snapshots      |
         |                      +-------------+-------------+
         |                                    |
         v                                    v
+------------------+                +-----------------------+
| Room             |                | AdminMonitorApp (FX)  |
| - broadcasts     |                | - polls snapshots     |
| - game events    |                | - renders tables/graphs|
+------------------+                +-----------------------+
```

## How metrics are collected

- Accept connection: `Server.start()` -> `recordConnectionAccepted()`
- Disconnect: `Server.unregisterClient()` -> `recordDisconnect()` + cleanup
- RX decode: `ClientHandler.run()` -> `recordMessageReceived()`
- TX send: `ClientHandler` writer thread -> `recordMessageSent()`
- Room broadcast: `Room.broadcast()` / `broadcastToOthers()` -> `recordBroadcast()`
- Game events: `Room.handleDraw()` / `handleGuess()` / `startRoundLocked()`
- Ping/pong: `Server.sendHeartbeats()` -> `ClientHandler.sendPing()` and
  `ClientHandler` handles `PongMessage` to update RTT

## Run

Prereqs:
- Java 17
- Gradle wrapper (included)

Default (UI enabled when monitoring is on):

```
./gradlew :scribble-server:run
```

Headless (no UI):

```
./gradlew :scribble-server:run --args="--headless"
```

Custom port:

```
./gradlew :scribble-server:run --args="--port 6000"
```

Disable monitoring (also disables the UI):

```
./gradlew :scribble-server:run --args="--no-monitoring"
```

## Config flags

- `--headless` / `-h` : disable JavaFX UI
- `--port` / `-p <port>` : set server port
- `--no-monitoring` / `--disable-monitoring` : disable monitoring entirely
- `--monitor-interval-ms <ms>` : stats update interval (default 1000ms)
- `--monitor-ring-size <n>` : time-series ring buffer size for server/room/client
  (defaults: server 300, room/client 60; override applies to all)
- `--monitor-log-size <n>` : packet log size cap (defaults: global 200,
  per-client 50; override applies to both)

## Troubleshooting

- JavaFX not launching
  - Ensure Java 17 is used and Gradle can resolve JavaFX 21 dependencies.
  - Use `--headless` if running on a server without a display.
- No metrics updating
  - Verify monitoring is not disabled and the server is running.
  - Connect a client and send messages; counters update only with traffic.
  - Check logs for exceptions from the stats updater thread.
- Ping/pong RTT not updating
  - Client must respond to `PingMessage` with `PongMessage`.
  - If clients are timing out, verify `PING_INTERVAL_SECONDS` and
    `PING_TIMEOUT_SECONDS` in `Constants`.

## Performance notes

- Per-message size uses Java serialization to estimate payload size in
  `ClientHandler`, which adds overhead at high message rates.
- Room broadcast byte counts use a lightweight heuristic to avoid extra
  serialization overhead.
- Larger ring/log sizes increase memory usage and UI update cost.

## Screenshots

- (add screenshots here)
