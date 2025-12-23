package com.scribble.server;

import com.scribble.game.Room;
import com.scribble.network.messages.RoomListMessage;
import com.scribble.network.messages.RoomSummary;
import com.scribble.utils.Constants;
import com.scribble.utils.Logger;
import com.scribble.utils.ThreadUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    public Server(int port) {
        this.port = port;
        this.clientExecutor = Executors.newCachedThreadPool();
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        this.rooms = new ConcurrentHashMap<>();
        this.clients = ConcurrentHashMap.newKeySet();
        this.running = false;
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
                    ClientHandler handler = new ClientHandler(socket, this);
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
    }

    public Room getOrCreateRoom(String name) {
        return rooms.computeIfAbsent(name, roomName -> {
            Room room = new Room(roomName, this);
            broadcastRoomList();
            return room;
        });
    }

    public void removeRoomIfEmpty(String name) {
        Room room = rooms.get(name);
        if (room != null && room.getPlayerCount() == 0) {
            rooms.remove(name);
            room.shutdown();
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
        ThreadUtils.shutdownExecutor(clientExecutor);
        ThreadUtils.shutdownExecutor(heartbeatExecutor);
        logger.info("Server shut down");
    }
}
