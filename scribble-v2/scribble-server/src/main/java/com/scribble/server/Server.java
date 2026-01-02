package com.scribble.server;

import com.scribble.game.Room;
import com.scribble.network.messages.RoomListMessage;
import com.scribble.network.messages.RoomSummary;
import com.scribble.server.monitor.ClientNetworkStats;
import com.scribble.server.monitor.NetworkMonitorBroadcaster;
import com.scribble.server.monitor.RoomNetworkStats;
import com.scribble.server.monitor.ServerNetworkStats;
import com.scribble.utils.Constants;
import com.scribble.utils.Logger;
import com.scribble.utils.ThreadUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class);

    private final int port;
    private final ExecutorService clientExecutor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ConcurrentHashMap<String, Room> rooms;
    private final Set<ClientHandler> clients;
    private volatile boolean running;
    private ServerSocket serverSocket;

    // Network monitoring
    private final ServerNetworkStats networkStats;
    private final NetworkMonitorBroadcaster monitorBroadcaster;
    private final boolean monitoringEnabled;

    public Server(int port) {
        this(port, true); // Monitoring enabled by default
    }

    public Server(int port, boolean enableMonitoring) {
        this.port = port;
        this.clientExecutor = Executors.newCachedThreadPool();
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        this.rooms = new ConcurrentHashMap<>();
        this.clients = ConcurrentHashMap.newKeySet();
        this.running = false;

        // Initialize network monitoring
        this.monitoringEnabled = enableMonitoring;
        if (enableMonitoring) {
            this.networkStats = ServerNetworkStats.initialize(port);
            this.monitorBroadcaster = new NetworkMonitorBroadcaster(networkStats);
            logger.info("Network monitoring enabled");
        } else {
            this.networkStats = null;
            this.monitorBroadcaster = null;
            logger.info("Network monitoring disabled");
        }
    }

    public void start() {
        if (running) {
            logger.warn("Server already running");
            return;
        }
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("Server started on port " + port);

            heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats,
                    Constants.PING_INTERVAL_SECONDS,
                    Constants.PING_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    String clientId = UUID.randomUUID().toString();

                    // Create client network stats if monitoring enabled
                    ClientNetworkStats clientStats = null;
                    if (monitoringEnabled && networkStats != null) {
                        InetSocketAddress remoteAddr = (InetSocketAddress) socket.getRemoteSocketAddress();
                        clientStats = new ClientNetworkStats(clientId, remoteAddr);
                        networkStats.recordConnectionAccepted(clientId, clientStats);
                    }

                    ClientHandler handler = new ClientHandler(socket, this, clientId, clientStats);
                    registerClient(handler);
                    clientExecutor.execute(handler);
                } catch (SocketException e) {
                    if (running) {
                        logger.error("Socket error", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to start server", e);
            shutdown();
        }
    }

    public void registerClient(ClientHandler handler) {
        clients.add(handler);
        sendRoomList(handler);
    }

    public void unregisterClient(ClientHandler handler) {
        clients.remove(handler);

        // Record disconnect in monitoring
        if (monitoringEnabled && networkStats != null) {
            networkStats.recordDisconnect(handler.getClientId());
            networkStats.removeClientStats(handler.getClientId());
        }
    }

    public Room getOrCreateRoom(String name, com.scribble.network.messages.RoomSettings settings) {
        return rooms.computeIfAbsent(name, roomName -> {
            Room room = new Room(roomName, this, settings);

            // Create room stats if monitoring enabled
            if (monitoringEnabled && networkStats != null) {
                networkStats.getOrCreateRoomStats(roomName);
            }

            broadcastRoomList();
            return room;
        });
    }

    public void removeRoomIfEmpty(String name) {
        Room room = rooms.get(name);
        if (room != null && room.getPlayerCount() == 0) {
            rooms.remove(name);
            room.shutdown();

            // Clean up room stats
            if (monitoringEnabled && networkStats != null) {
                networkStats.removeRoomStats(name);
            }

            broadcastRoomList();
        }
    }

    public void broadcastRoomList() {
        RoomListMessage message = new RoomListMessage(getRoomSummaries());
        for (ClientHandler client : clients) {
            client.send(message);
        }
    }

    public void sendRoomList(ClientHandler client) {
        client.send(new RoomListMessage(getRoomSummaries()));
    }

    private List<RoomSummary> getRoomSummaries() {
        List<RoomSummary> summaries = new ArrayList<>();
        for (Room room : rooms.values()) {
            summaries.add(room.toSummary());
        }
        summaries.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return summaries;
    }

    private void sendHeartbeats() {
        for (ClientHandler client : clients) {
            client.sendPing();
            if (client.isTimedOut()) {
                client.disconnect("Ping timeout");
            }
        }
    }

    public void shutdown() {
        if (!running) {
            return;
        }
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }
        for (Room room : rooms.values()) {
            room.shutdown();
        }
        rooms.clear();

        // Shutdown monitoring
        if (monitoringEnabled) {
            if (monitorBroadcaster != null) {
                monitorBroadcaster.shutdown();
            }
            if (networkStats != null) {
                networkStats.shutdown();
            }
        }

        ThreadUtils.shutdownExecutor(clientExecutor);
        ThreadUtils.shutdownExecutor(heartbeatExecutor);
        logger.info("Server shut down");
    }

    // --- Monitoring accessors ---

    public ServerNetworkStats getNetworkStats() {
        return networkStats;
    }

    public NetworkMonitorBroadcaster getMonitorBroadcaster() {
        return monitorBroadcaster;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public RoomNetworkStats getRoomStats(String roomName) {
        if (networkStats != null) {
            return networkStats.getOrCreateRoomStats(roomName);
        }
        return null;
    }
}
