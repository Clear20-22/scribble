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
    private long lastPongAt;
    private long reconnectDelayMs = Constants.RECONNECT_INITIAL_DELAY_MS;
    private volatile boolean autoReconnect;
    private final AtomicBoolean heartbeatStarted = new AtomicBoolean(false);

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
            autoReconnect = true;
            setStatus(ConnectionStatus.CONNECTED);
            startReader();
            startHeartbeat();
            return true;
        } catch (IOException e) {
            logger.error("Failed to connect", e);
            disconnect("Connection failed");
            return false;
        }
    }

    public void setListener(ClientEventListener listener) {
        this.listener = listener;
    }

    public void setIdentity(String playerName, String roomName) {
        this.playerName = playerName;
        this.roomName = roomName;
    }

    public void send(Message message) {
        if (!connected.get()) {
            return;
        }
        try {
            synchronized (out) {
                out.writeObject(message);
                out.flush();
            }
        } catch (IOException e) {
            logger.error("Send failed", e);
            disconnect("Send failed");
        }
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
        if (message instanceof PingMessage ping) {
            send(new PongMessage(ping.getTimestamp()));
            return;
        }
        if (message instanceof PongMessage) {
            lastPongAt = System.currentTimeMillis();
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
            } else if (message instanceof DrawMessage draw) {
                listener.onDraw(draw);
            } else if (message instanceof ClearMessage clear) {
                listener.onClear(clear);
            } else if (message instanceof ChatMessage chat) {
                listener.onChat(chat);
            } else if (message instanceof ErrorMessage error) {
                listener.onError(error);
            }
        });
    }

    public void disconnect(String reason) {
        if (!connected.getAndSet(false)) {
            return;
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        setStatus(ConnectionStatus.DISCONNECTED);
        if (autoReconnect) {
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
            reconnecting.set(false);
            if (playerName != null && roomName != null) {
                send(new JoinMessage(roomName, playerName));
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
}
