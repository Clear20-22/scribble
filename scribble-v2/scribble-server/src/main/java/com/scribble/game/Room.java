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
    private final RoomSettings settings;
    private final Map<String, ClientHandler> players;
    private final Set<String> spectators;
    private final Map<String, Integer> scores;
    private final Set<String> playersWhoGuessed;
    private final Map<String, Integer> roundGuessPoints;
    private final List<String> roundGuessOrder;
    private final Map<String, List<DrawMessage>> strokesById;
    private final List<String> strokeOrder;
    private final Set<Integer> revealedIndices;
    private final Random random;
    private final WordProvider wordProvider;
    private final ScheduledExecutorService scheduler;
    private final Object lock = new Object();

    private String currentDrawer;
    private String lastDrawer;
    private String currentWord;
    private List<String> currentChoices;
    private int timeLeft;
    private int roundNumber;
    private GamePhase phase;
    private ScheduledFuture<?> tickTask;
    private ScheduledFuture<?> chooseTask;

    public Room(String name, Server server, RoomSettings settings) {
        this.name = name;
        this.server = server;
        this.settings = settings == null
                ? new RoomSettings(Constants.MAX_PLAYERS, Constants.ROUND_DURATION_SECONDS, Constants.DEFAULT_TOTAL_ROUNDS, false)
                : settings;
        this.players = new ConcurrentHashMap<>();
        this.spectators = new HashSet<>();
        this.scores = new ConcurrentHashMap<>();
        this.playersWhoGuessed = Collections.synchronizedSet(new HashSet<>());
        this.roundGuessPoints = new HashMap<>();
        this.roundGuessOrder = new ArrayList<>();
        this.strokesById = new HashMap<>();
        this.strokeOrder = new ArrayList<>();
        this.revealedIndices = new HashSet<>();
        this.random = new Random();
        this.wordProvider = new WordProvider();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.phase = GamePhase.WAITING;
        this.roundNumber = 0;
        this.timeLeft = settings != null ? settings.getRoundTimeSeconds() : Constants.ROUND_DURATION_SECONDS;
    }

    public String getName() {
        return name;
    }

    public RoomSettings getSettings() {
        return settings;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getActivePlayersCount() {
        return players.size() - spectators.size();
    }

    public RoomSummary toSummary() {
        return new RoomSummary(name, getActivePlayersCount(), phase, roundNumber, settings);
    }

    public boolean isSpectator(String playerName) {
        return spectators.contains(playerName);
    }

    public boolean addPlayer(String playerName, ClientHandler handler, boolean spectatorRequested) {
        synchronized (lock) {
            if (players.containsKey(playerName)) {
                return false;
            }
            boolean forceSpectator = getActivePlayersCount() >= settings.getMaxPlayers()
                    || phase == GamePhase.RUNNING
                    || phase == GamePhase.CHOOSING_WORD;
            boolean spectator = spectatorRequested || forceSpectator;
            players.put(playerName, handler);
            scores.putIfAbsent(playerName, 0);
            if (spectator) {
                spectators.add(playerName);
            }
            broadcastSystem(playerName + " joined the room" + (spectator ? " as spectator" : ""));
            broadcastPlayerListLocked();
            sendStrokeHistory(handler);
            startIfReadyLocked();
            return true;
        }
    }

    public void removePlayer(String playerName) {
        synchronized (lock) {
            players.remove(playerName);
            scores.remove(playerName);
            spectators.remove(playerName);
            playersWhoGuessed.remove(playerName);
            broadcastSystem(playerName + " left the room");
            broadcastPlayerListLocked();

            if (playerName != null && playerName.equals(currentDrawer)) {
                currentDrawer = null;
                logger.info("Drawer left room " + name + ", selecting new drawer");
                if (getActivePlayersCount() >= Constants.MIN_PLAYERS) {
                    startRoundLocked();
                } else {
                    resetToWaitingLocked();
                }
            }

            if (getActivePlayersCount() < Constants.MIN_PLAYERS && phase != GamePhase.WAITING) {
                resetToWaitingLocked();
            }
        }
        server.broadcastRoomList();
    }

    public void handleDraw(String sender, DrawMessage message) {
        synchronized (lock) {
            if (phase != GamePhase.RUNNING || sender == null || !sender.equals(currentDrawer)) {
                return;
            }
            if (message.getPoints().size() > Constants.DRAW_MAX_POINTS) {
                return;
            }
            if (message.getStrokeId() == null || message.getStrokeId().isBlank()) {
                return;
            }
            strokesById.computeIfAbsent(message.getStrokeId(), key -> {
                strokeOrder.add(key);
                return new ArrayList<>();
            }).add(message);
            broadcastToOthers(sender, message);
        }
    }

    public void handleUndo(String sender) {
        synchronized (lock) {
            if (phase != GamePhase.RUNNING || sender == null || !sender.equals(currentDrawer)) {
                return;
            }
            if (strokeOrder.isEmpty()) {
                return;
            }
            String lastStrokeId = strokeOrder.remove(strokeOrder.size() - 1);
            strokesById.remove(lastStrokeId);
            broadcastStrokeHistoryLocked();
        }
    }

    public void handleClear(String sender) {
        synchronized (lock) {
            if (phase != GamePhase.RUNNING || sender == null || !sender.equals(currentDrawer)) {
                return;
            }
            strokesById.clear();
            strokeOrder.clear();
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
        synchronized (lock) {
            if (spectators.contains(sender)) {
                broadcast(new ChatMessage(sender, text, ChatType.CHAT));
                return;
            }
            if (phase == GamePhase.RUNNING && !sender.equals(currentDrawer) && currentWord != null
                    && !playersWhoGuessed.contains(sender)
                    && normalize(text).equals(normalize(currentWord))) {
                playersWhoGuessed.add(sender);
                int guessOrder = roundGuessOrder.size();
                int points = Scoring.guessPoints(timeLeft, settings.getRoundTimeSeconds(), guessOrder);
                scores.put(sender, scores.getOrDefault(sender, 0) + points);
                roundGuessPoints.put(sender, points);
                roundGuessOrder.add(sender);
                broadcast(new ChatMessage(sender, "guessed the word!", ChatType.CORRECT));
                broadcastPlayerListLocked();

                if (allNonDrawerGuessed()) {
                    startIntermissionLocked();
                } else {
                    broadcastGameStateLocked();
                }
                return;
            }
        }
        broadcast(new ChatMessage(sender, text, ChatType.CHAT));
    }

    public void handleChooseWord(String sender, String word) {
        synchronized (lock) {
            if (phase != GamePhase.CHOOSING_WORD || sender == null || !sender.equals(currentDrawer)) {
                return;
            }
            if (currentChoices == null || !currentChoices.contains(word)) {
                return;
            }
            selectWordLocked(word);
        }
    }

    public void sendStrokeHistory(ClientHandler handler) {
        if (handler == null) {
            return;
        }
        List<DrawMessage> strokes = flattenStrokes();
        if (!strokes.isEmpty()) {
            handler.send(new StrokeHistoryMessage(strokes));
        }
    }

    public void handleStrokeHistoryRequest(ClientHandler handler) {
        sendStrokeHistory(handler);
    }

    private void startIfReadyLocked() {
        if (getActivePlayersCount() >= Constants.MIN_PLAYERS && phase == GamePhase.WAITING) {
            startRoundLocked();
        } else {
            broadcastGameStateLocked();
        }
        server.broadcastRoomList();
    }

    private void startRoundLocked() {
        stopTimersLocked();
        if (getActivePlayersCount() < Constants.MIN_PLAYERS) {
            resetToWaitingLocked();
            return;
        }
        phase = GamePhase.CHOOSING_WORD;
        roundNumber++;
        resetRoundState();
        currentDrawer = selectDrawer();
        currentChoices = wordProvider.randomChoices(3);
        timeLeft = Constants.CHOOSE_WORD_SECONDS;
        logger.info("Round " + roundNumber + " choosing word in room " + name + ", drawer=" + currentDrawer);
        sendWordChoicesLocked();
        broadcastPlayerListLocked();
        broadcastGameStateLocked();
        startChooseCountdownLocked();
        server.broadcastRoomList();
    }

    private void startChooseCountdownLocked() {
        chooseTask = scheduler.scheduleAtFixedRate(() -> {
            synchronized (lock) {
                if (phase != GamePhase.CHOOSING_WORD) {
                    return;
                }
                timeLeft--;
                if (timeLeft <= 0) {
                    String fallback = currentChoices.get(random.nextInt(currentChoices.size()));
                    selectWordLocked(fallback);
                    return;
                }
                broadcastGameStateLocked();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void selectWordLocked(String word) {
        stopChooseTaskLocked();
        currentWord = word;
        phase = GamePhase.RUNNING;
        revealedIndices.clear();
        timeLeft = settings.getRoundTimeSeconds();
        broadcastWordChosenLocked();
        broadcastGameStateLocked();
        tickTask = scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    private void startIntermissionLocked() {
        stopTimersLocked();
        phase = GamePhase.INTERMISSION;
        awardDrawerBonusLocked();
        logger.info("Round " + roundNumber + " ended in room " + name + ", word=" + currentWord);
        broadcast(new ChatMessage("System", buildRoundSummary(), ChatType.SYSTEM));
        broadcastGameStateLocked();
        scheduler.schedule(() -> {
            synchronized (lock) {
                if (roundNumber >= settings.getTotalRounds()) {
                    finishMatchLocked();
                } else {
                    startRoundLocked();
                }
            }
        }, Constants.INTERMISSION_SECONDS, TimeUnit.SECONDS);
        server.broadcastRoomList();
    }

    private void finishMatchLocked() {
        phase = GamePhase.FINISHED;
        broadcastSystem("Match finished! Resetting scores.");
        broadcastGameStateLocked();
        scores.replaceAll((key, value) -> 0);
        roundNumber = 0;
        scheduler.schedule(() -> {
            synchronized (lock) {
                resetToWaitingLocked();
                startIfReadyLocked();
            }
        }, Constants.INTERMISSION_SECONDS, TimeUnit.SECONDS);
    }

    private void tick() {
        synchronized (lock) {
            if (phase != GamePhase.RUNNING) {
                return;
            }
            timeLeft--;
            maybeRevealHints();
            if (timeLeft <= 0) {
                startIntermissionLocked();
                return;
            }
            broadcastGameStateLocked();
        }
    }

    private void maybeRevealHints() {
        int total = settings.getRoundTimeSeconds();
        if (total <= 0 || currentWord == null) {
            return;
        }
        int firstReveal = total * Constants.HINT_FIRST_REVEAL_PERCENT / 100;
        int secondReveal = total * Constants.HINT_SECOND_REVEAL_PERCENT / 100;
        if (timeLeft == firstReveal || timeLeft == secondReveal) {
            revealRandomLetter();
        }
    }

    private void revealRandomLetter() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < currentWord.length(); i++) {
            char c = currentWord.charAt(i);
            if (c == ' ') {
                continue;
            }
            if (!revealedIndices.contains(i)) {
                indices.add(i);
            }
        }
        if (indices.size() <= 1) {
            return;
        }
        int pick = indices.get(random.nextInt(indices.size()));
        revealedIndices.add(pick);
    }

    private void stopTimersLocked() {
        stopRoundTaskLocked();
        stopChooseTaskLocked();
    }

    private void stopRoundTaskLocked() {
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
    }

    private void stopChooseTaskLocked() {
        if (chooseTask != null) {
            chooseTask.cancel(false);
            chooseTask = null;
        }
    }

    private void resetRoundState() {
        playersWhoGuessed.clear();
        roundGuessPoints.clear();
        roundGuessOrder.clear();
        strokesById.clear();
        strokeOrder.clear();
        revealedIndices.clear();
    }

    private void resetToWaitingLocked() {
        stopTimersLocked();
        phase = GamePhase.WAITING;
        currentDrawer = null;
        currentWord = null;
        currentChoices = null;
        timeLeft = settings.getRoundTimeSeconds();
        broadcastGameStateLocked();
    }

    private void broadcast(Message message) {
        for (ClientHandler handler : players.values()) {
            handler.send(message);
        }
    }

    private void broadcastToOthers(String sender, Message message) {
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            if (!entry.getKey().equals(sender)) {
                entry.getValue().send(message);
            }
        }
    }

    private void broadcastSystem(String text) {
        broadcast(new ChatMessage("System", text, ChatType.SYSTEM));
    }

    private void broadcastPlayerListLocked() {
        List<PlayerInfo> list = new ArrayList<>();
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            String name = entry.getKey();
            boolean guessed = playersWhoGuessed.contains(name);
            boolean spectator = spectators.contains(name);
            list.add(new PlayerInfo(name, scores.getOrDefault(name, 0), name.equals(currentDrawer), guessed, spectator));
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
            } else if (phase == GamePhase.CHOOSING_WORD) {
                word = isDrawer ? "Pick a word" : "Choosing a word...";
            } else if (phase == GamePhase.INTERMISSION || phase == GamePhase.FINISHED) {
                word = currentWord == null ? "" : currentWord;
            } else {
                word = isDrawer ? currentWord : maskedWord();
            }
            ClientHandler handler = players.get(player);
            if (handler != null) {
                handler.send(new GameStateMessage(currentDrawer, word, timeLeft, roundNumber, phase, isDrawer));
            }
        }
    }

    private void sendWordChoicesLocked() {
        if (currentDrawer == null) {
            return;
        }
        ClientHandler handler = players.get(currentDrawer);
        if (handler != null) {
            handler.send(new WordChoicesMessage(currentChoices, Constants.CHOOSE_WORD_SECONDS));
        }
    }

    private void broadcastWordChosenLocked() {
        for (String player : players.keySet()) {
            boolean isDrawer = player.equals(currentDrawer);
            String word = isDrawer ? currentWord : maskedWord();
            players.get(player).send(new WordChosenMessage(word, isDrawer));
        }
    }

    private void broadcastStrokeHistoryLocked() {
        List<DrawMessage> strokes = flattenStrokes();
        broadcast(new StrokeHistoryMessage(strokes));
    }

    private List<DrawMessage> flattenStrokes() {
        List<DrawMessage> result = new ArrayList<>();
        for (String strokeId : strokeOrder) {
            List<DrawMessage> chunks = strokesById.get(strokeId);
            if (chunks != null) {
                result.addAll(chunks);
            }
        }
        return result;
    }

    private String selectDrawer() {
        List<String> names = new ArrayList<>(players.keySet());
        names.removeIf(spectators::contains);
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

    private boolean allNonDrawerGuessed() {
        int activePlayers = getActivePlayersCount();
        if (activePlayers <= 1) {
            return false;
        }
        int required = activePlayers - 1;
        return playersWhoGuessed.size() >= required;
    }

    private String maskedWord() {
        if (currentWord == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < currentWord.length(); i++) {
            char c = currentWord.charAt(i);
            if (c == ' ') {
                builder.append("  ");
                continue;
            }
            if (revealedIndices.contains(i)) {
                builder.append(c).append(' ');
            } else {
                builder.append("_ ");
            }
        }
        return builder.toString().trim();
    }

    private String normalize(String text) {
        return text.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private void awardDrawerBonusLocked() {
        if (currentDrawer == null || roundGuessOrder.isEmpty()) {
            return;
        }
        int bonus = roundGuessOrder.size() * Constants.DRAWER_BONUS_PER_GUESS;
        scores.put(currentDrawer, scores.getOrDefault(currentDrawer, 0) + bonus);
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
            stopTimersLocked();
            scheduler.shutdownNow();
        }
    }
}
