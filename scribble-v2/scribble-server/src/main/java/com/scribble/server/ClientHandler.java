package com.scribble.server;

import com.scribble.game.Room;
import com.scribble.network.messages.*;
import com.scribble.utils.Constants;
import com.scribble.utils.Logger;
import com.scribble.utils.ValidationUtils;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(ClientHandler.class);

    private final Socket socket;
    private final Server server;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private final BlockingQueue<Message> outgoing;
    private final AtomicBoolean running;
    private Thread writerThread;
    private int messagesSinceReset;

    private String playerName;
    private Room room;
    private long lastDrawAt;
    private long lastChatAt;
    private long lastGuessAt;
    private volatile long lastPongAt;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.outgoing = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean(true);
        this.lastPongAt = System.currentTimeMillis();
        initializeStreams();
        startWriter();
    }

    private void initializeStreams() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            logger.error("Failed to initialize streams", e);
            disconnect("Stream init failed");
        }
    }

    private void startWriter() {
        writerThread = new Thread(() -> {
            while (running.get()) {
                try {
                    Message message = outgoing.take();
                    synchronized (out) {
                        out.writeObject(message);
                        out.flush();
                        messagesSinceReset++;
                        if (messagesSinceReset >= 200) {
                            out.reset();
                            messagesSinceReset = 0;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    disconnect("Write failed");
                }
            }
        }, "ClientWriter-" + socket.getPort());
        writerThread.setDaemon(true);
        writerThread.start();
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                Message message = (Message) in.readObject();
                handleMessage(message);
            }
        } catch (SocketException | EOFException e) {
            disconnect("Client disconnected");
        } catch (Exception e) {
            logger.error("Client handler error", e);
            disconnect("Client error");
        }
    }

    public void send(Message message) {
        if (!running.get()) {
            return;
        }
        outgoing.offer(message);
    }

    public void sendPing() {
        send(new PingMessage(System.currentTimeMillis()));
    }

    public boolean isTimedOut() {
        long now = System.currentTimeMillis();
        return now - lastPongAt > Constants.PING_TIMEOUT_SECONDS * 1000L;
    }

    private void handleMessage(Message message) {
        if (message == null) {
            return;
        }
        if (message instanceof PongMessage pong) {
            lastPongAt = System.currentTimeMillis();
            return;
        }
        if (message instanceof PingMessage ping) {
            send(new PongMessage(ping.getTimestamp()));
            return;
        }
        if (message instanceof ListRoomsMessage) {
            server.sendRoomList(this);
            return;
        }
        if (message instanceof CreateRoomMessage createRoom) {
            handleCreateRoom(createRoom);
            return;
        }
        if (message instanceof ChooseWordMessage chooseWord) {
            handleChooseWord(chooseWord);
            return;
        }
        if (message instanceof JoinMessage joinMessage) {
            handleJoin(joinMessage);
            return;
        }
        if (message instanceof RequestStrokeHistoryMessage) {
            handleStrokeHistoryRequest();
            return;
        }
        if (message instanceof LeaveRoomMessage) {
            leaveRoom();
            return;
        }
        if (message instanceof DrawMessage drawMessage) {
            handleDraw(drawMessage);
            return;
        }
        if (message instanceof ClearMessage) {
            handleClear();
            return;
        }
        if (message instanceof UndoMessage) {
            handleUndo();
            return;
        }
        if (message instanceof GuessMessage guessMessage) {
            handleGuess(guessMessage.getText());
            return;
        }
        if (message instanceof ChatMessage chatMessage) {
            handleChat(chatMessage.getText());
            return;
        }
    }

    private void handleCreateRoom(CreateRoomMessage message) {
        ValidationUtils.ValidationResult result = ValidationUtils.validateRoomName(message.getRoomName());
        if (!result.isValid()) {
            send(new ErrorMessage("ROOM_INVALID", result.getErrorMessage()));
            return;
        }
        RoomSettings settings = sanitizeSettings(message.getSettings());
        server.getOrCreateRoom(message.getRoomName().trim(), settings);
        server.broadcastRoomList();
    }

    private void handleJoin(JoinMessage message) {
        if (room != null) {
            send(new JoinResultMessage(false, "Already in a room", room.getName(), playerName, false));
            return;
        }
        ValidationUtils.ValidationResult nameValidation = ValidationUtils.validatePlayerName(message.getPlayerName());
        if (!nameValidation.isValid()) {
            send(new JoinResultMessage(false, nameValidation.getErrorMessage(), message.getRoomName(), message.getPlayerName(), false));
            return;
        }
        ValidationUtils.ValidationResult roomValidation = ValidationUtils.validateRoomName(message.getRoomName());
        if (!roomValidation.isValid()) {
            send(new JoinResultMessage(false, roomValidation.getErrorMessage(), message.getRoomName(), message.getPlayerName(), false));
            return;
        }
        Room targetRoom = server.getOrCreateRoom(message.getRoomName().trim(), null);
        String trimmedName = message.getPlayerName().trim();
        if (!targetRoom.addPlayer(trimmedName, this, message.isSpectator())) {
            send(new JoinResultMessage(false, "Name taken", targetRoom.getName(), trimmedName, false));
            return;
        }
        this.playerName = trimmedName;
        this.room = targetRoom;
        boolean spectator = targetRoom.isSpectator(playerName);
        send(new JoinResultMessage(true, spectator ? "Joined as spectator" : "Joined", room.getName(), playerName, spectator));
        logger.info("Player " + playerName + " joined room " + room.getName());
        server.broadcastRoomList();
    }

    private RoomSettings sanitizeSettings(RoomSettings settings) {
        if (settings == null) {
            return new RoomSettings(Constants.MAX_PLAYERS, Constants.ROUND_DURATION_SECONDS, Constants.DEFAULT_TOTAL_ROUNDS, false);
        }
        int maxPlayers = Math.max(Constants.MIN_PLAYERS, Math.min(Constants.MAX_PLAYERS, settings.getMaxPlayers()));
        int roundTime = Math.max(30, Math.min(120, settings.getRoundTimeSeconds()));
        int totalRounds = Math.max(1, Math.min(10, settings.getTotalRounds()));
        return new RoomSettings(maxPlayers, roundTime, totalRounds, settings.isPrivateRoom());
    }

    private void handleChooseWord(ChooseWordMessage message) {
        if (room != null) {
            room.handleChooseWord(playerName, message.getWord());
        }
    }

    private void handleDraw(DrawMessage drawMessage) {
        long now = System.currentTimeMillis();
        if (now - lastDrawAt < Constants.DRAW_RATE_LIMIT_MS) {
            return;
        }
        lastDrawAt = now;
        if (room != null) {
            room.handleDraw(playerName, drawMessage);
        }
    }

    private void handleClear() {
        if (room != null) {
            room.handleClear(playerName);
        }
    }

    private void handleUndo() {
        if (room != null) {
            room.handleUndo(playerName);
        }
    }

    private void handleStrokeHistoryRequest() {
        if (room != null) {
            room.handleStrokeHistoryRequest(this);
        }
    }

    private void handleChat(String text) {
        long now = System.currentTimeMillis();
        if (now - lastChatAt < Constants.CHAT_RATE_LIMIT_MS) {
            send(new ErrorMessage("CHAT_RATE", "You are sending messages too quickly."));
            return;
        }
        lastChatAt = now;
        if (room != null) {
            room.handleChat(playerName, text);
        }
    }

    private void handleGuess(String text) {
        long now = System.currentTimeMillis();
        if (now - lastGuessAt < Constants.GUESS_RATE_LIMIT_MS) {
            send(new ErrorMessage("GUESS_RATE", "Guessing too fast."));
            return;
        }
        lastGuessAt = now;
        handleChat(text);
    }

    private void leaveRoom() {
        if (room != null && playerName != null) {
            room.removePlayer(playerName);
            server.removeRoomIfEmpty(room.getName());
            logger.info("Player " + playerName + " left room " + room.getName());
            room = null;
        }
    }

    public void disconnect(String reason) {
        if (!running.getAndSet(false)) {
            return;
        }
        leaveRoom();
        server.unregisterClient(this);
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        logger.info("Client disconnected: " + reason);
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean isConnected() {
        return running.get() && !socket.isClosed();
    }
}
