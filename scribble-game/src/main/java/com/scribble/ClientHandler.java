package com.scribble;

import java.io.*;
import java.net.Socket;

/**
 * Handles communication with a single client.
 */
public class ClientHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Server server;
    private String playerName;
    private Room room;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                Message message = (Message) in.readObject();
                handleMessage(message);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Client disconnected: " + playerName);
            if (room != null) {
                room.removePlayer(playerName);
                server.removeRoomIfEmpty(room.getName());
            }
        }
    }

    private void handleMessage(Message message) {
        switch (message.getType()) {
            case "LIST_ROOMS":
                sendMessage(new RoomListMessage(server.getRoomNames()));
                break;
            case "JOIN":
                JoinMessage join = (JoinMessage) message;
                playerName = join.getPlayerName();
                room = server.getOrCreateRoom(join.getRoomName());
                room.addPlayer(playerName, this);
                break;
            case "DRAW":
                DrawMessage draw = (DrawMessage) message;
                room.handleDraw(draw, playerName);
                break;
            case "CHAT":
                ChatMessage chat = (ChatMessage) message;
                room.handleChat(chat);
                break;
            case "CLEAR":
                ClearMessage clear = (ClearMessage) message;
                room.handleClear(clear, playerName);
                break;
            case "GUESS":
                GuessMessage guess = (GuessMessage) message;
                room.handleChat(new ChatMessage(playerName, guess.getGuess()));
                break;
        }
    }

    public void sendMessage(Message message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}