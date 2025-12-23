package com.scribble.game;

import com.scribble.network.messages.*;
import com.scribble.server.ClientHandler;
import com.scribble.server.Server;
import com.scribble.utils.Constants;
import com.scribble.utils.Logger;
import com.scribble.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Room {
    private static final Logger logger = Logger.getLogger(Room.class);

    private final String name;
    private final Server server;
    private final Map<String, ClientHandler> players;
    private final Map<String, Integer> scores;
    private final Set<String> playersWhoGuessed;
    private final Random random;
    private final WordProvider wordProvider;
    private final ScheduledExecutorService scheduler;
    private final Object lock = new Object();

    private String currentDrawer;
    private String lastDrawer;
    private String currentWord;
    private int timeLeft;
    private int roundNumber;
    private GamePhase phase;
    private ScheduledFuture<?> tickTask;
    private final Map<String, Integer> roundGuessPoints;
    private final List<String> roundGuessOrder;

    public Room(String name, Server server) {
        this.name = name;
        this.server = server;
        this.players = new ConcurrentHashMap<>();
        this.scores = new ConcurrentHashMap<>();
        this.playersWhoGuessed = Collections.synchronizedSet(new HashSet<>());
        this.random = new Random();
        this.wordProvider = new WordProvider();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.phase = GamePhase.WAITING;
        this.roundNumber = 0;
        this.roundGuessPoints = new HashMap<>();
        this.roundGuessOrder = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public RoomSummary toSummary() {
        return new RoomSummary(name, players.size(), phase == GamePhase.RUNNING, roundNumber);
    }

    public boolean addPlayer(String playerName, ClientHandler handler) {
        synchronized (lock) {
            if (players.size() >= Constants.MAX_PLAYERS) {
                return false;
            }
            if (players.containsKey(playerName)) {
                return false;
            }
            players.put(playerName, handler);
            scores.putIfAbsent(playerName, 0);
            broadcastSystem(playerName + " joined the room");
            broadcastPlayerList();
            startIfReadyLocked();
            return true;
        }
    }

    public void removePlayer(String playerName) {
        synchronized (lock) {
            players.remove(playerName);
            scores.remove(playerName);
            playersWhoGuessed.remove(playerName);
            broadcastSystem(playerName + " left the room");
            broadcastPlayerList();

            if (playerName != null && playerName.equals(currentDrawer)) {
                currentDrawer = null;
                logger.info("Drawer left room " + name + ", selecting new drawer");
                if (players.size() >= Constants.MIN_PLAYERS) {
                    startRoundLocked();
                } else {
                    stopRoundLocked();
                    phase = GamePhase.WAITING;
                    currentDrawer = null;
                    currentWord = null;
                    timeLeft = Constants.ROUND_DURATION_SECONDS;
                    broadcastGameStateLocked();
                }
            }

            if (players.size() < Constants.MIN_PLAYERS && phase != GamePhase.WAITING) {
                stopRoundLocked();
                phase = GamePhase.WAITING;
                currentDrawer = null;
                currentWord = null;
                timeLeft = Constants.ROUND_DURATION_SECONDS;
                broadcastGameStateLocked();
            }
        }
        server.broadcastRoomList();
    }

    public void handleDraw(String sender, DrawMessage message) {
        synchronized (lock) {
            if (phase != GamePhase.RUNNING || sender == null || !sender.equals(currentDrawer)) {
                return;
            }
            for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
                if (!entry.getKey().equals(sender)) {
                    entry.getValue().send(message);
                }
            }
        }
    }

    public void handleClear(String sender) {
        synchronized (lock) {
            if (phase != GamePhase.RUNNING || sender == null || !sender.equals(currentDrawer)) {
                return;
            }
            broadcast(new ClearMessage());
        }
    }

    public void handleChat(String sender, String text) {
        if (sender == null || text == null) {
            return;
        }
        ValidationUtils.ValidationResult result = ValidationUtils.validateChatMessage(text);
        if (!result.isValid()) {
            return;
        }
        String normalized = normalize(text);
        synchronized (lock) {
            if (phase == GamePhase.RUNNING && !sender.equals(currentDrawer) && currentWord != null
                    && !playersWhoGuessed.contains(sender)
                    && normalized.equals(normalize(currentWord))) {
                playersWhoGuessed.add(sender);
                int points = Scoring.guessPoints(timeLeft);
                scores.put(sender, scores.getOrDefault(sender, 0) + points);
                roundGuessPoints.put(sender, points);
                roundGuessOrder.add(sender);
                if (currentDrawer != null) {
                    scores.put(currentDrawer, scores.getOrDefault(currentDrawer, 0) + Constants.DRAWER_BONUS_PER_GUESS);
                }
                broadcast(new ChatMessage(sender, "guessed the word!", ChatType.CORRECT));
                broadcastPlayerList();

                if (players.size() > 1 && playersWhoGuessed.size() >= players.size() - 1) {
                    startIntermissionLocked();
                } else {
                    broadcastGameStateLocked();
                }
                return;
            }
        }
        broadcast(new ChatMessage(sender, text, ChatType.CHAT));
    }

    private void startIfReadyLocked() {
        if (players.size() >= Constants.MIN_PLAYERS && phase == GamePhase.WAITING) {
            startRoundLocked();
        } else {
            broadcastGameStateLocked();
        }
        server.broadcastRoomList();
    }

    private void startRoundLocked() {
        stopRoundLocked();
        if (players.size() < Constants.MIN_PLAYERS) {
            phase = GamePhase.WAITING;
            currentDrawer = null;
            currentWord = null;
            timeLeft = Constants.ROUND_DURATION_SECONDS;
            broadcastGameStateLocked();
            return;
        }
        phase = GamePhase.RUNNING;
        roundNumber++;
        playersWhoGuessed.clear();
        roundGuessPoints.clear();
        roundGuessOrder.clear();
        currentDrawer = selectDrawer();
        currentWord = wordProvider.randomWord();
        timeLeft = Constants.ROUND_DURATION_SECONDS;
        logger.info("Round " + roundNumber + " started in room " + name + ", drawer=" + currentDrawer);
        broadcastSystem("Round " + roundNumber + " started. " + currentDrawer + " is drawing.");
        broadcastPlayerList();
        broadcastGameStateLocked();
        tickTask = scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        server.broadcastRoomList();
    }

    private void startIntermissionLocked() {
        stopRoundLocked();
        phase = GamePhase.INTERMISSION;
        logger.info("Round " + roundNumber + " ended in room " + name + ", word=" + currentWord);
        broadcast(new ChatMessage("System", buildRoundSummary(), ChatType.SYSTEM));
        broadcastGameStateLocked();
        scheduler.schedule(() -> {
            synchronized (lock) {
                startRoundLocked();
            }
        }, Constants.INTERMISSION_SECONDS, TimeUnit.SECONDS);
        server.broadcastRoomList();
    }

    private void tick() {
        synchronized (lock) {
            if (phase != GamePhase.RUNNING) {
                return;
            }
            timeLeft--;
            if (timeLeft <= 0) {
                startIntermissionLocked();
                return;
            }
            broadcastGameStateLocked();
        }
    }

    private void stopRoundLocked() {
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
    }

    private void broadcast(Message message) {
        for (ClientHandler handler : players.values()) {
            handler.send(message);
        }
    }

    private void broadcastSystem(String text) {
        broadcast(new ChatMessage("System", text, ChatType.SYSTEM));
    }

    private void broadcastPlayerList() {
        List<PlayerInfo> list = new ArrayList<>();
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            String name = entry.getKey();
            boolean guessed = playersWhoGuessed.contains(name);
            list.add(new PlayerInfo(name, scores.getOrDefault(name, 0), name.equals(currentDrawer), guessed));
        }
        list.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        broadcast(new PlayerListMessage(list));
    }

    private void broadcastGameStateLocked() {
        for (String player : players.keySet()) {
            boolean isDrawer = player.equals(currentDrawer);
            String word;
            if (phase == GamePhase.WAITING) {
                word = "Waiting for players";
            } else if (phase == GamePhase.INTERMISSION) {
                word = currentWord;
            } else {
                word = isDrawer ? currentWord : maskedWord();
            }
            ClientHandler handler = players.get(player);
            if (handler != null) {
                handler.send(new GameStateMessage(currentDrawer, word, timeLeft, roundNumber, phase, isDrawer));
            }
        }
    }

    private String selectDrawer() {
        List<String> names = new ArrayList<>(players.keySet());
        if (names.isEmpty()) {
            return null;
        }
        if (names.size() == 1) {
            lastDrawer = names.get(0);
            return lastDrawer;
        }
        if (lastDrawer != null && !names.contains(lastDrawer)) {
            lastDrawer = null;
        }
        String selected;
        do {
            selected = names.get(random.nextInt(names.size()));
        } while (lastDrawer != null && selected.equals(lastDrawer));
        lastDrawer = selected;
        return selected;
    }

    private String maskedWord() {
        if (currentWord == null) {
            return "";
        }
        int revealCount = 0;
        if (timeLeft <= Constants.REVEAL_START_SECONDS) {
            int elapsed = Constants.REVEAL_START_SECONDS - timeLeft;
            revealCount = elapsed / Constants.REVEAL_EVERY_SECONDS + 1;
        }
        StringBuilder builder = new StringBuilder();
        int revealed = 0;
        for (int i = 0; i < currentWord.length(); i++) {
            char c = currentWord.charAt(i);
            if (c == ' ') {
                builder.append("  ");
                continue;
            }
            if (revealed < revealCount) {
                builder.append(c).append(' ');
                revealed++;
            } else {
                builder.append("_ ");
            }
        }
        return builder.toString().trim();
    }

    private String normalize(String text) {
        return text.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String buildRoundSummary() {
        if (currentWord == null) {
            return "Round complete.";
        }
        if (roundGuessOrder.isEmpty()) {
            return "Round complete. The word was: " + currentWord + ". No one guessed it.";
        }
        StringBuilder builder = new StringBuilder("Round complete. The word was: ");
        builder.append(currentWord).append(". Guessers: ");
        for (int i = 0; i < roundGuessOrder.size(); i++) {
            String name = roundGuessOrder.get(i);
            int points = roundGuessPoints.getOrDefault(name, 0);
            builder.append(name).append(" (+").append(points).append(")");
            if (i < roundGuessOrder.size() - 1) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }

    public void shutdown() {
        synchronized (lock) {
            stopRoundLocked();
            scheduler.shutdownNow();
        }
    }
}
