# Scribble V2

Real-time multiplayer drawing and guessing game (Pictionary/Skribbl style) built with Java 17, JavaFX, and TCP sockets.

## Modules

- `scribble-shared`: shared message protocol + utilities
- `scribble-server`: multiplayer server
- `scribble-client`: JavaFX client UI

## Requirements

- Java 17+
- Network access for Gradle wrapper to download Gradle and JavaFX

## Build

```bash
cd scribble-v2
./gradlew clean build
```

## Run Server

```bash
./gradlew :scribble-server:run
```

Default port: `5555`

You can override the port:

```bash
./gradlew :scribble-server:run --args="6000"
```

## Run Client

```bash
./gradlew :scribble-client:run
```

In the login screen, enter username, host, and port. Use `localhost` and `5555` for local play.

## Gameplay

- Create rooms with settings (max players, round time, total rounds).
- At round start, the drawer chooses 1 of 3 words within 10 seconds.
- Guessers see masked words with timed hints; drawer sees the full word.
- Correct guesses award points based on time remaining and guess order.
- Drawer can undo/clear; drawings are synced to late-joiners.
- Spectators can join full or in-progress rooms (chat-only, no scoring).
- Clients attempt to reconnect automatically if the connection drops.

## Configuration

Key settings live in `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/Constants.java`:

- Port, round duration, scoring, rate limits
- Word reveal timings
- Ping/pong heartbeat + reconnect backoff

## Troubleshooting

- If the client UI looks unstyled, verify `scribble-client/src/main/resources/css/style.css` is present and the app is launched via Gradle.
- If word choices or hints don't appear, ensure both server and client are rebuilt after protocol changes.
- If you see connection timeouts, verify the server is running and the port matches the client login.

## Notes

- Words are loaded from `words.txt` (200+ words).
- Rooms are removed when empty.
