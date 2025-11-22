package com.scribble;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a game room with players, game state, and broadcasting.
 */
public class Room {
    private String name;
    private ConcurrentHashMap<String, ClientHandler> players;
    private String currentDrawer;
    private String currentWord;
    private int timeLeft;
    private Map<String, Integer> scores;
    private Timer timer;
    private List<String> wordList;
    private Random random;

    public Room(String name) {
        this.name = name;
        players = new ConcurrentHashMap<>();
        scores = new ConcurrentHashMap<>();
        wordList = Arrays.asList("Apple", "House", "Car", "Tree", "Sun", "Moon", "Cat", "Dog", "Book", "Chair");
        random = new Random();
        startNewRound();
    }

    public void addPlayer(String playerName, ClientHandler handler) {
        players.put(playerName, handler);
        scores.putIfAbsent(playerName, 0);
        broadcastPlayerList();
        if (players.size() == 1) {
            startNewRound();
        }
    }

    public void removePlayer(String playerName) {
        players.remove(playerName);
        if (currentDrawer != null && currentDrawer.equals(playerName)) {
            startNewRound();
        }
    }

    public void broadcast(Message message) {
        for (ClientHandler handler : players.values()) {
            handler.sendMessage(message);
        }
    }

    public void broadcastExcept(String excludePlayer, Message message) {
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            if (!entry.getKey().equals(excludePlayer)) {
                entry.getValue().sendMessage(message);
            }
        }
    }

    public void handleDraw(DrawMessage draw, String sender) {
        if (sender.equals(currentDrawer)) {
            broadcastExcept(sender, draw);
        }
    }

    public void handleChat(ChatMessage chat) {
        broadcast(chat);
        if (!chat.getSender().equals(currentDrawer) && chat.getText().equalsIgnoreCase(currentWord)) {
            scores.put(chat.getSender(), scores.get(chat.getSender()) + 10);
            broadcast(new ChatMessage("System", chat.getSender() + " guessed correctly!"));
            startNewRound();
        }
    }

    public void handleClear(ClearMessage clear, String sender) {
        if (sender.equals(currentDrawer)) {
            broadcastExcept(sender, clear);
        }
    }

    private void startNewRound() {
        if (players.isEmpty())
            return;
        List<String> playerNames = new ArrayList<>(players.keySet());
        currentDrawer = playerNames.get(random.nextInt(playerNames.size()));
        currentWord = wordList.get(random.nextInt(wordList.size()));
        timeLeft = 60; // 60 seconds
        if (timer != null)
            timer.cancel();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                timeLeft--;
                if (timeLeft <= 0) {
                    broadcast(new ChatMessage("System", "Time's up! The word was: " + currentWord));
                    startNewRound();
                } else {
                    broadcastGameState();
                }
            }
        }, 1000, 1000);
        broadcastGameState();
    }

    private void broadcastGameState() {
        Map<String, Integer> scoresCopy = new HashMap<>(scores);
        for (String player : players.keySet()) {
            if (!scoresCopy.containsKey(player)) {
                scoresCopy.put(player, 0);
            }
        }
        GameStateMessage state = new GameStateMessage(currentDrawer, currentDrawer.equals("me") ? currentWord : "?????",
                timeLeft, scoresCopy);
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            String player = entry.getKey();
            String word = player.equals(currentDrawer) ? currentWord : "?????";
            GameStateMessage personalState = new GameStateMessage(currentDrawer, word, timeLeft, scoresCopy);
            entry.getValue().sendMessage(personalState);
        }
    }

    private void broadcastPlayerList() {
        List<String> playerList = new ArrayList<>(players.keySet());
        broadcast(new PlayerListMessage(playerList));
    }

    public String getName() {
        return name;
    }

    public int getPlayerCount() {
        return players.size();
    }
}