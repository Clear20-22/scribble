package com.scribble;

import java.io.*;
import java.net.Socket;

/**
 * Client class that connects to the server and handles communication.
 */
public class Client {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private ClientGUI gui;

    public Client(ClientGUI gui) {
        this.gui = gui;
    }

    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            new Thread(this::listenForMessages).start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
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

    private void listenForMessages() {
        try {
            while (true) {
                Message message = (Message) in.readObject();
                handleMessage(message);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Disconnected from server");
        }
    }

    private void handleMessage(Message message) {
        switch (message.getType()) {
            case "DRAW":
                DrawMessage draw = (DrawMessage) message;
                gui.drawLine(draw.getStartX(), draw.getStartY(), draw.getEndX(), draw.getEndY(), draw.getColor(),
                        draw.getBrushSize());
                break;
            case "CHAT":
                ChatMessage chat = (ChatMessage) message;
                gui.addChatMessage(chat.getSender() + ": " + chat.getText());
                break;
            case "GAME_STATE":
                GameStateMessage state = (GameStateMessage) message;
                gui.updateGameState(state.getCurrentDrawer(), state.getWord(), state.getTimeLeft(), state.getScores());
                break;
            case "PLAYER_LIST":
                PlayerListMessage players = (PlayerListMessage) message;
                gui.updatePlayerList(players.getPlayers());
                break;
            case "CLEAR":
                gui.clearCanvas();
                break;
        }
    }
}