package com.scribble.client;

import com.scribble.network.messages.*;
import com.scribble.utils.Constants;
import com.scribble.utils.Logger;
import javafx.application.Platform;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class);

    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Thread readerThread;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ConnectionStatus> status = new AtomicReference<>(ConnectionStatus.DISCONNECTED);
    private ClientEventListener listener;

    private String lastHost;
    private int lastPort;
    private String playerName;
    private String roomName;
    private boolean spectator;
    private long lastPongAt;
    private long reconnectDelayMs = Constants.RECONNECT_INITIAL_DELAY_MS;
    private volatile boolean autoReconnect;
    private final AtomicBoolean heartbeatStarted = new AtomicBoolean(false);
    private volatile long lastLatencyMs;

    // Network statistics for visualization
    private final NetworkStats networkStats = new NetworkStats();

    public boolean connect(String host, int port) {
        if (connected.get()) {
            return true;
        }
        try {
            lastHost = host;
            lastPort = port;
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            connected.set(true);
            reconnectDelayMs = Constants.RECONNECT_INITIAL_DELAY_MS;
            lastPongAt = System.currentTimeMillis();
            // Only enable auto-reconnect if not already in a reconnection cycle
            if (!reconnecting.get()) {
                autoReconnect = true;
            }
            networkStats.reset();
            networkStats.onConnectionStarted();
            setStatus(ConnectionStatus.CONNECTED);
            startReader();
            startHeartbeat();
            return true;
        } catch (IOException e) {
            logger.error("Failed to connect", e);
            // Don't trigger disconnect/reconnect loop on initial connection failure
            closeSocketQuietly();
            return false;
        }
    }

    private void closeSocketQuietly() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;
        in = null;
        out = null;
    }

    public void setListener(ClientEventListener listener) {
        this.listener = listener;
    }

    public void setIdentity(String playerName, String roomName, boolean spectator) {
        this.playerName = playerName;
        this.roomName = roomName;
        this.spectator = spectator;
    }

    public void send(Message message) {
        if (!connected.get()) {
            return;
        }
        try {
            synchronized (out) {
                out.writeObject(message);
                out.flush();
                // Track network statistics
                networkStats.onMessageSent(message.getClass().getSimpleName(), estimateMessageSize(message));
            }
        } catch (IOException e) {
            logger.error("Send failed", e);
            disconnect("Send failed");
        }
    }

    private int estimateMessageSize(Message message) {
        // Rough estimation of serialized message size
        if (message instanceof DrawMessage)
            return 150;
        if (message instanceof PingMessage || message instanceof PongMessage)
            return 24;
        if (message instanceof ChatMessage || message instanceof GuessMessage)
            return 100;
        if (message instanceof StrokeHistoryMessage)
            return 2000;
        return 50; // Default estimate
    }

    private void startReader() {
        readerThread = new Thread(() -> {
            try {
                while (connected.get()) {
                    Message message = (Message) in.readObject();
                    handleMessage(message);
                }
            } catch (SocketException | EOFException e) {
                disconnect("Disconnected");
            } catch (Exception e) {
                logger.error("Read failed", e);
                disconnect("Read failed");
            }
        }, "ClientReader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void handleMessage(Message message) {
        if (message == null) {
            return;
        }
        // Track received message statistics
        networkStats.onMessageReceived(message.getClass().getSimpleName(), estimateMessageSize(message));

        if (message instanceof PingMessage ping) {
            send(new PongMessage(ping.getTimestamp()));
            return;
        }
        if (message instanceof PongMessage) {
            lastPongAt = System.currentTimeMillis();
            lastLatencyMs = lastPongAt - ((PongMessage) message).getTimestamp();
            networkStats.onLatencyMeasured(lastLatencyMs);
            Platform.runLater(() -> {
                if (listener != null) {
                    listener.onLatency(lastLatencyMs);
                    listener.onNetworkStats(networkStats);
                }
            });
            return;
        }
        if (listener == null) {
            return;
        }
        Platform.runLater(() -> {
            if (message instanceof RoomListMessage roomList) {
                listener.onRoomList(roomList);
            } else if (message instanceof JoinResultMessage joinResult) {
                listener.onJoinResult(joinResult);
            } else if (message instanceof PlayerListMessage playerList) {
                listener.onPlayerList(playerList);
            } else if (message instanceof GameStateMessage state) {
                listener.onGameState(state);
            } else if (message instanceof WordChoicesMessage choices) {
                listener.onWordChoices(choices);
            } else if (message instanceof WordChosenMessage chosen) {
                listener.onWordChosen(chosen);
            } else if (message instanceof StrokeHistoryMessage history) {
                listener.onStrokeHistory(history);
            } else if (message instanceof DrawMessage draw) {
                listener.onDraw(draw);
            } else if (message instanceof ClearMessage clear) {
                listener.onClear(clear);
            } else if (message instanceof ChatMessage chat) {
                listener.onChat(chat);
            } else if (message instanceof ErrorMessage error) {
                listener.onError(error);
            } else if (message instanceof GuessHintMessage hint) {
                listener.onGuessHint(hint);
            } else if (message instanceof PlayAgainStatusMessage status) {
                listener.onPlayAgainStatus(status);
            } else if (message instanceof ServerMetricsMessage metrics) {
                listener.onServerMetrics(metrics);
            }
        });
    }

    public void disconnect(String reason) {
        if (!connected.getAndSet(false)) {
            return;
        }
        closeSocketQuietly();
        setStatus(ConnectionStatus.DISCONNECTED);
        if (autoReconnect && !reconnecting.get()) {
            attemptReconnect();
        }
        Platform.runLater(() -> {
            if (listener != null) {
                listener.onDisconnected(reason);
            }
        });
    }

    private void startHeartbeat() {
        if (heartbeatStarted.getAndSet(true)) {
            return;
        }
        scheduler.scheduleAtFixedRate(() -> {
            if (!connected.get()) {
                return;
            }
            // Don't timeout during reconnection
            if (reconnecting.get()) {
                return;
            }
            send(new PingMessage(System.currentTimeMillis()));
            long now = System.currentTimeMillis();
            if (now - lastPongAt > Constants.PING_TIMEOUT_SECONDS * 1000L) {
                disconnect("Server timeout");
            }
        }, Constants.PING_INTERVAL_SECONDS, Constants.PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void attemptReconnect() {
        if (lastHost == null || reconnecting.getAndSet(true)) {
            return;
        }
        setStatus(ConnectionStatus.RECONNECTING);
        scheduler.schedule(this::doReconnect, reconnectDelayMs, TimeUnit.MILLISECONDS);
    }

    private void doReconnect() {
        if (connected.get()) {
            reconnecting.set(false);
            return;
        }
        boolean success = connect(lastHost, lastPort);
        if (success) {
            // Reset pong timestamp to avoid immediate timeout
            lastPongAt = System.currentTimeMillis();
            reconnecting.set(false);
            networkStats.onReconnect();
            // Only auto-rejoin if we were in a room
            if (playerName != null && roomName != null) {
                send(new JoinMessage(roomName, playerName, spectator));
            }
            return;
        }
        reconnectDelayMs = Math.min(reconnectDelayMs * 2, Constants.RECONNECT_MAX_DELAY_MS);
        scheduler.schedule(this::doReconnect, reconnectDelayMs, TimeUnit.MILLISECONDS);
    }

    private void setStatus(ConnectionStatus connectionStatus) {
        status.set(connectionStatus);
        Platform.runLater(() -> {
            if (listener != null) {
                listener.onConnectionStatus(connectionStatus);
            }
        });
    }

    public boolean isConnected() {
        return connected.get();
    }

    public NetworkStats getNetworkStats() {
        return networkStats;
    }

    public String getServerAddress() {
        return lastHost + ":" + lastPort;
    }

    /**
     * Subscribe or unsubscribe to server metrics (admin feature).
     * When subscribed, the client will receive periodic ServerMetricsMessage
     * updates.
     * 
     * @param subscribe true to subscribe, false to unsubscribe
     */
    public void subscribeToServerMetrics(boolean subscribe) {
        send(new SubscribeMetricsMessage(subscribe, null));
    }

    /**
     * Subscribe to server metrics with admin token (for authenticated access).
     * 
     * @param subscribe  true to subscribe, false to unsubscribe
     * @param adminToken optional admin token for authentication
     */
    public void subscribeToServerMetrics(boolean subscribe, String adminToken) {
        send(new SubscribeMetricsMessage(subscribe, adminToken));
    }
}
