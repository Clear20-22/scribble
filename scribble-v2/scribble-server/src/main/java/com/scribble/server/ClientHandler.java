package com.scribble.server;

import com.scribble.game.Room;
import com.scribble.network.messages.*;
import com.scribble.server.monitor.ClientNetworkStats;
import com.scribble.server.monitor.ServerNetworkStats;
import com.scribble.utils.Constants;
import com.scribble.utils.Logger;
import com.scribble.utils.ValidationUtils;

import java.io.*;
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
    private CountingOutputStream countingOut;

    // Unique client identifier
    private final String clientId;

    // Network monitoring
    private final ClientNetworkStats networkStats;

    private String playerName;
    private Room room;
    private long lastDrawAt;
    private long lastChatAt;
    private long lastGuessAt;
    private volatile long lastPongAt;
    private int violations;
    private volatile long mutedUntil;

    public ClientHandler(Socket socket, Server server) {
        this(socket, server, java.util.UUID.randomUUID().toString(), null);
    }

    public ClientHandler(Socket socket, Server server, String clientId, ClientNetworkStats networkStats) {
        this.socket = socket;
        this.server = server;
        this.clientId = clientId;
        this.networkStats = networkStats;
        this.outgoing = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean(true);
        this.lastPongAt = System.currentTimeMillis();
        initializeStreams();
        startWriter();
    }

    private void initializeStreams() {
        try {
            countingOut = new CountingOutputStream(socket.getOutputStream());
            out = new ObjectOutputStream(countingOut);
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
                        long beforeBytes = countingOut != null ? countingOut.getCount() : 0;
                        out.writeObject(message);
                        out.flush();
                        messagesSinceReset++;
                        if (messagesSinceReset >= 200) {
                            out.reset();
                            messagesSinceReset = 0;
                        }

                        // Record TX stats
                        int bytesWritten = 0;
                        if (countingOut != null) {
                            bytesWritten = (int) Math.max(0, countingOut.getCount() - beforeBytes);
                        } else {
                            bytesWritten = estimateMessageSize(message);
                        }
                        recordTx(message, bytesWritten);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    disconnect("Write failed");
                }
            }
        }, "ClientWriter-" + (clientId.length() > 8 ? clientId.substring(0, 8) : clientId));
        writerThread.setDaemon(true);
        writerThread.start();
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                Message message = (Message) in.readObject();

                // Record RX stats
                int estimatedSize = estimateMessageSize(message);
                recordRx(message, estimatedSize);

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
        long now = System.currentTimeMillis();
        ServerNetworkStats serverStats = server.getNetworkStats();
        if (serverStats != null) {
            serverStats.recordPingSent(clientId);
        }
        send(new PingMessage(now));
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
            ServerNetworkStats serverStats = server.getNetworkStats();
            if (serverStats != null) {
                serverStats.recordPongReceived(clientId);
            }
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
        if (message instanceof RequestStateMessage) {
            handleStateRequest();
            return;
        }
        if (message instanceof PlayAgainRequestMessage playAgainRequest) {
            handlePlayAgainRequest(playAgainRequest);
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

        // Handle metrics subscription (admin feature)
        if (message instanceof SubscribeMetricsMessage subscribeMsg) {
            handleMetricsSubscription(subscribeMsg);
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
            send(new JoinResultMessage(false, nameValidation.getErrorMessage(), message.getRoomName(),
                    message.getPlayerName(), false));
            return;
        }
        ValidationUtils.ValidationResult roomValidation = ValidationUtils.validateRoomName(message.getRoomName());
        if (!roomValidation.isValid()) {
            send(new JoinResultMessage(false, roomValidation.getErrorMessage(), message.getRoomName(),
                    message.getPlayerName(), false));
            return;
        }
        Room targetRoom = server.getOrCreateRoom(message.getRoomName().trim(), null);
        String trimmedName = message.getPlayerName().trim();
        String joinError = targetRoom.addPlayer(trimmedName, this, message.isSpectator());
        if (joinError != null) {
            send(new JoinResultMessage(false, joinError, targetRoom.getName(), trimmedName, false));
            return;
        }
        this.playerName = trimmedName;
        this.room = targetRoom;
        if (networkStats != null) {
            networkStats.setPlayerName(playerName);
            networkStats.setCurrentRoomId(room.getName());
        }
        ServerNetworkStats serverStats = server.getNetworkStats();
        if (serverStats != null) {
            serverStats.updateClientPlayerName(clientId, playerName);
            serverStats.updateClientRoom(clientId, room.getName());
        }
        boolean spectator = targetRoom.isSpectator(playerName);
        send(new JoinResultMessage(true, spectator ? "Joined as spectator" : "Joined", room.getName(), playerName,
                spectator));
        logger.info("Player " + playerName + " joined room " + room.getName());
        server.broadcastRoomList();
    }

    private RoomSettings sanitizeSettings(RoomSettings settings) {
        if (settings == null) {
            return new RoomSettings(Constants.MAX_PLAYERS, Constants.ROUND_DURATION_SECONDS,
                    Constants.DEFAULT_TOTAL_ROUNDS,
                    false, null, true, Constants.CHOOSE_WORD_SECONDS, HintMode.NORMAL, WordDifficulty.MEDIUM);
        }
        int maxPlayers = Math.max(Constants.MIN_PLAYERS, Math.min(Constants.MAX_PLAYERS, settings.getMaxPlayers()));
        int roundTime = Math.max(Constants.MIN_ROUND_SECONDS,
                Math.min(Constants.MAX_ROUND_SECONDS, settings.getRoundTimeSeconds()));
        int totalRounds = Math.max(1, Math.min(10, settings.getTotalRounds()));
        int wordChoiceSeconds = Math.max(Constants.MIN_WORD_CHOICE_SECONDS,
                Math.min(Constants.MAX_WORD_CHOICE_SECONDS, settings.getWordChoiceSeconds()));
        HintMode hintMode = settings.getHintMode() == null ? HintMode.NORMAL : settings.getHintMode();
        WordDifficulty difficulty = settings.getWordDifficulty() == null ? WordDifficulty.MEDIUM
                : settings.getWordDifficulty();
        String roomCode = settings.getRoomCode();
        if (roomCode != null && roomCode.isBlank()) {
            roomCode = null;
        }
        return new RoomSettings(maxPlayers, roundTime, totalRounds, settings.isPrivateRoom(),
                roomCode, settings.isAllowSpectators(), wordChoiceSeconds, hintMode, difficulty);
    }

    private void handleChooseWord(ChooseWordMessage message) {
        if (room != null) {
            room.handleChooseWord(playerName, message.getWord());
        }
    }

    private void handleDraw(DrawMessage drawMessage) {
        if (isMuted()) {
            return;
        }
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

    private void handlePlayAgainRequest(PlayAgainRequestMessage message) {
        if (room != null) {
            room.handlePlayAgainRequest(playerName, message.isPlayAgain());
        }
    }

    private void handleChat(String text) {
        if (isMuted()) {
            send(new ErrorMessage("MUTED", "You are temporarily muted."));
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastChatAt < Constants.CHAT_RATE_LIMIT_MS) {
            send(new ErrorMessage("CHAT_RATE", "You are sending messages too quickly."));
            registerViolation("CHAT_RATE");
            return;
        }
        lastChatAt = now;
        if (room != null) {
            room.handleChat(playerName, text);
        }
    }

    private void handleGuess(String text) {
        if (isMuted()) {
            send(new ErrorMessage("MUTED", "You are temporarily muted."));
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastGuessAt < Constants.GUESS_RATE_LIMIT_MS) {
            send(new ErrorMessage("GUESS_RATE", "Guessing too fast."));
            registerViolation("GUESS_RATE");
            return;
        }
        lastGuessAt = now;
        if (room != null) {
            room.handleGuess(playerName, text);
        }
    }

    private void handleStateRequest() {
        if (room != null) {
            room.sendFullState(this, playerName);
        }
    }

    private void handleMetricsSubscription(SubscribeMetricsMessage message) {
        if (!server.isMonitoringEnabled()) {
            send(new ErrorMessage("MONITORING_DISABLED", "Server monitoring is not enabled"));
            return;
        }

        if (!isAdminSubscriberAllowed(message)) {
            send(new ErrorMessage("MONITORING_DENIED", "Admin metrics access denied"));
            return;
        }

        var broadcaster = server.getMonitorBroadcaster();
        if (broadcaster == null) {
            return;
        }

        // TODO: Add admin token validation here if needed
        // For now, allow any client to subscribe (can be restricted later)

        if (message.isSubscribe()) {
            broadcaster.addSubscriber(this);
            logger.info("Client " + clientId + " subscribed to metrics");
        } else {
            broadcaster.removeSubscriber(this);
            logger.info("Client " + clientId + " unsubscribed from metrics");
        }
    }

    private void leaveRoom() {
        if (room != null && playerName != null) {
            room.removePlayer(playerName);
            server.removeRoomIfEmpty(room.getName());
            logger.info("Player " + playerName + " left room " + room.getName());
            if (networkStats != null) {
                networkStats.setCurrentRoomId(null);
            }
            ServerNetworkStats serverStats = server.getNetworkStats();
            if (serverStats != null) {
                serverStats.updateClientRoom(clientId, null);
            }
            room = null;
        }
    }

    public void disconnect(String reason) {
        if (!running.getAndSet(false)) {
            return;
        }

        // Unsubscribe from metrics if subscribed
        if (server.isMonitoringEnabled() && server.getMonitorBroadcaster() != null) {
            server.getMonitorBroadcaster().removeSubscriber(this);
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

    public String getClientId() {
        return clientId;
    }

    public ClientNetworkStats getNetworkStats() {
        return networkStats;
    }

    public String getCurrentRoomName() {
        return room != null ? room.getName() : null;
    }

    public boolean isConnected() {
        return running.get() && !socket.isClosed();
    }

    private boolean isMuted() {
        return System.currentTimeMillis() < mutedUntil;
    }

    private void registerViolation(String code) {
        violations++;
        if (violations >= Constants.KICK_VIOLATIONS) {
            disconnect("Abuse detected");
            return;
        }
        if (violations >= Constants.MUTE_VIOLATIONS) {
            mutedUntil = System.currentTimeMillis() + Constants.MUTE_SECONDS * 1000L;
            send(new ErrorMessage("MUTED", "You are temporarily muted."));
            return;
        }
        if (violations >= Constants.WARN_VIOLATIONS) {
            send(new ErrorMessage("WARN", "Please slow down (" + code + ")."));
        }
    }

    // --- Network Monitoring Helpers ---

    private void recordTx(Message message, int sizeBytes) {
        String msgType = message.getClass().getSimpleName();
        String roomName = getCurrentRoomName();
        if (networkStats != null && roomName != null) {
            networkStats.setCurrentRoomId(roomName);
        }

        ServerNetworkStats serverStats = server.getNetworkStats();
        if (serverStats != null) {
            serverStats.recordMessageSent(clientId, msgType, sizeBytes, roomName);
        }
    }

    private void recordRx(Message message, int sizeBytes) {
        String msgType = message.getClass().getSimpleName();
        String roomName = getCurrentRoomName();
        if (networkStats != null && roomName != null) {
            networkStats.setCurrentRoomId(roomName);
        }

        ServerNetworkStats serverStats = server.getNetworkStats();
        if (serverStats != null) {
            serverStats.recordMessageReceived(clientId, msgType, sizeBytes, roomName);
        }
    }

    /**
     * Estimates the serialized size of a message.
     * This is an approximation - for exact size, we'd need to serialize to a buffer
     * first.
     */
    private int estimateMessageSize(Message message) {
        if (message == null) {
            return 0;
        }

        // Use a ByteArrayOutputStream to get actual serialized size
        // This adds some overhead but gives accurate measurements
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(message);
            oos.flush();
            return baos.size();
        } catch (IOException e) {
            // Fallback: estimate based on message type
            return estimateMessageSizeFallback(message);
        }
    }

    private int estimateMessageSizeFallback(Message message) {
        // Base overhead for Java serialization
        int base = 100;

        if (message instanceof DrawMessage) {
            return base + 200; // Draw messages have coordinates, color, etc.
        } else if (message instanceof ChatMessage || message instanceof GuessMessage) {
            return base + 100; // Text messages
        } else if (message instanceof GameStateMessage) {
            return base + 500; // State messages are larger
        } else if (message instanceof StrokeHistoryMessage) {
            return base + 2000; // History can be large
        } else if (message instanceof ServerMetricsMessage) {
            return base + 5000; // Metrics messages are large
        } else {
            return base + 50; // Default small message
        }
    }

    private boolean isAdminSubscriberAllowed(SubscribeMetricsMessage message) {
        if (socket.getInetAddress() != null && socket.getInetAddress().isLoopbackAddress()) {
            return true;
        }
        String token = getAdminToken();
        if (token == null || token.isBlank()) {
            return false;
        }
        String provided = message != null ? message.getAdminToken() : null;
        return token.equals(provided);
    }

    private String getAdminToken() {
        String token = System.getProperty("scribble.monitor.adminToken");
        if (token == null || token.isBlank()) {
            token = System.getenv("SCRIBBLE_MONITOR_TOKEN");
        }
        return token;
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count;

        private CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
            count += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        public long getCount() {
            return count;
        }
    }
}
