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

- Create or join rooms in the lobby.
- The drawer can draw and clear; others can guess in chat.
- Rounds start at 2+ players. Correct guesses award points.
- Clients attempt to reconnect automatically if the connection drops.

## Configuration

Key settings live in `scribble-v2/scribble-shared/src/main/java/com/scribble/utils/Constants.java`:

- Port, round duration, scoring, rate limits
- Word reveal timings
- Ping/pong heartbeat + reconnect backoff

## Notes

- Words are loaded from `words.txt` (200+ words).
- Rooms are removed when empty.
