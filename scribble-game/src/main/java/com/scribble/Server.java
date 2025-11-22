package com.scribble;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main server class that listens for client connections and manages rooms.
 */
public class Server {
    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private ConcurrentHashMap<String, Room> rooms;

    public Server() {
        rooms = new ConcurrentHashMap<>();
        executor = Executors.newCachedThreadPool();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                executor.execute(handler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Room getOrCreateRoom(String roomName) {
        return rooms.computeIfAbsent(roomName, k -> new Room(roomName));
    }

    public java.util.List<String> getRoomNames() {
        return new java.util.ArrayList<>(rooms.keySet());
    }

    public void removeRoomIfEmpty(String roomName) {
        Room room = rooms.get(roomName);
        if (room != null && room.getPlayerCount() == 0) {
            rooms.remove(roomName);
            System.out.println("Removed empty room: " + roomName);
        }
    }

    public static void main(String[] args) {
        new Server().start();
    }
}