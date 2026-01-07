package com.scribble.game;

import com.scribble.network.messages.*;
import com.scribble.server.ClientHandler;
import com.scribble.server.Server;
import com.scribble.server.monitor.RoomNetworkStats;
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
    private final Map<String, Boolean> playAgainVotes;

    private String currentDrawer;
    private String lastDrawer;
    private String currentWord;
    private List<String> currentChoices;
    private int timeLeft;
    private int roundNumber;
    private GamePhase phase;
    private ScheduledFuture<?> tickTask;
    private ScheduledFuture<?> chooseTask;
    private boolean playAgainOpen;

    public Room(String name, Server server, RoomSettings settings) {
        this.name = name;
        this.server = server;
        this.settings = settings == null
                ? new RoomSettings(Constants.MAX_PLAYERS, Constants.ROUND_DURATION_SECONDS,
                        Constants.DEFAULT_TOTAL_ROUNDS,
                        false, null, true, Constants.CHOOSE_WORD_SECONDS, HintMode.NORMAL, WordDifficulty.MEDIUM)
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
        this.playAgainVotes = new HashMap<>();
        this.phase = GamePhase.WAITING;
        this.roundNumber = 0;
        this.timeLeft = settings != null ? settings.getRoundTimeSeconds() : Constants.ROUND_DURATION_SECONDS;
        this.playAgainOpen = false;
    }

    /**
     * Gets the room's network stats tracker (if monitoring is enabled).
     */
    private RoomNetworkStats getRoomStats() {
        return server.getRoomStats(name);
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

    public String addPlayer(String playerName, ClientHandler handler, boolean spectatorRequested) {
        synchronized (lock) {
            if (players.containsKey(playerName)) {
                return "Name taken";
            }
            boolean forceSpectator = getActivePlayersCount() >= settings.getMaxPlayers()
                    || phase == GamePhase.RUNNING
                    || phase == GamePhase.CHOOSING_WORD;
            boolean spectator = spectatorRequested || forceSpectator;
            if (spectator && !settings.isAllowSpectators()) {
                return "Spectators are disabled";
            }
            players.put(playerName, handler);
            scores.putIfAbsent(playerName, 0);
            if (spectator) {
                spectators.add(playerName);
            }

            // Record player join in room stats
            RoomNetworkStats stats = getRoomStats();
            if (stats != null) {
                stats.recordPlayerJoin(handler.getClientId());
                stats.updatePlayerCounts(players.size(), spectators.size());
            }

            broadcastSystem(playerName + " joined the room" + (spectator ? " as spectator" : ""));
            broadcastPlayerListLocked();
            sendStrokeHistory(handler);
            startIfReadyLocked();
            return null;
        }
    }

    public void removePlayer(String playerName) {
        synchronized (lock) {
            ClientHandler handler = players.get(playerName);

            players.remove(playerName);
            scores.remove(playerName);
            spectators.remove(playerName);
            playAgainVotes.remove(playerName);
            playersWhoGuessed.remove(playerName);

            // Record player leave in room stats
            if (handler != null) {
                RoomNetworkStats stats = getRoomStats();
                if (stats != null) {
                    stats.recordPlayerLeave(handler.getClientId());
                    stats.updatePlayerCounts(players.size(), spectators.size());
                }
            }
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
            if (phase == GamePhase.FINISHED && getActivePlayersCount() < Constants.MIN_PLAYERS) {
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

            // Record draw event in room stats
            RoomNetworkStats stats = getRoomStats();
            if (stats != null) {
                stats.recordDrawEvent();
            }

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
        String sanitized = sanitizeText(text);
        ValidationUtils.ValidationResult result = ValidationUtils.validateChatMessage(sanitized);
        if (!result.isValid()) {
            return;
        }
        broadcast(new ChatMessage(sender, sanitized, ChatType.CHAT));
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

    public void handlePlayAgainRequest(String sender, boolean playAgain) {
        if (sender == null) {
            return;
        }
        synchronized (lock) {
            if (phase != GamePhase.FINISHED || !playAgainOpen || spectators.contains(sender)) {
                return;
            }
            if (playAgain) {
                playAgainVotes.put(sender, true);
            } else {
                playAgainVotes.remove(sender);
            }
            broadcastPlayAgainStatusLocked();
            int requiredVotes = getRequiredVotesLocked();
            if (countPlayAgainVotesLocked() >= requiredVotes) {
                startNewMatchLocked();
            }
        }
    }

    public void sendFullState(ClientHandler handler, String playerName) {
        if (handler == null || playerName == null) {
            return;
        }
        synchronized (lock) {
            handler.send(buildPlayerListMessageLocked());
            handler.send(buildGameStateMessageLocked(playerName));
            if (phase == GamePhase.CHOOSING_WORD && playerName.equals(currentDrawer) && currentChoices != null) {
                handler.send(new WordChoicesMessage(currentChoices, timeLeft));
            }
            if (phase == GamePhase.FINISHED && playAgainOpen) {
                handler.send(buildPlayAgainStatusMessageLocked());
            }
            sendStrokeHistory(handler);
        }
    }

    public void handleGuess(String sender, String text) {
        if (sender == null || text == null) {
            return;
        }
        String sanitized = sanitizeText(text);
        ValidationUtils.ValidationResult result = ValidationUtils.validateChatMessage(sanitized);
        if (!result.isValid()) {
            return;
        }
        synchronized (lock) {
            if (spectators.contains(sender) || sender.equals(currentDrawer)) {
                broadcast(new ChatMessage(sender, sanitized, ChatType.CHAT));
                return;
            }
            if (phase == GamePhase.RUNNING && currentWord != null
                    && !playersWhoGuessed.contains(sender)
                    && normalize(sanitized).equals(normalize(currentWord))) {
                playersWhoGuessed.add(sender);
                int guessOrder = roundGuessOrder.size();
                int points = Scoring.guessPoints(timeLeft, settings.getRoundTimeSeconds(), guessOrder);
                scores.put(sender, scores.getOrDefault(sender, 0) + points);
                roundGuessPoints.put(sender, points);
                roundGuessOrder.add(sender);
                broadcast(new ChatMessage(sender, "guessed the word!", ChatType.CORRECT));
                broadcastPlayerListLocked();

                // Record correct guess in room stats
                RoomNetworkStats stats = getRoomStats();
                if (stats != null) {
                    stats.recordGuessAttempt(true);
                }

                if (allNonDrawerGuessed()) {
                    startIntermissionLocked();
                } else {
                    broadcastGameStateLocked();
                }
                return;
            }
        }
        if (phase == GamePhase.RUNNING && currentWord != null
                && !playersWhoGuessed.contains(sender)
                && !spectators.contains(sender)
                && !sender.equals(currentDrawer)
                && isCloseGuess(sanitized, currentWord)) {
            ClientHandler handler = players.get(sender);
            if (handler != null) {
                handler.send(new GuessHintMessage("Close!"));
            }
        }

        // Record incorrect guess attempt in room stats (only if in game and not already
        // guessed)
        if (phase == GamePhase.RUNNING && currentWord != null
                && !playersWhoGuessed.contains(sender)
                && !spectators.contains(sender)
                && !sender.equals(currentDrawer)) {
            RoomNetworkStats stats = getRoomStats();
            if (stats != null) {
                stats.recordGuessAttempt(false);
            }
        }

        broadcast(new ChatMessage(sender, sanitized, ChatType.CHAT));
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
        timeLeft = settings.getWordChoiceSeconds();
        logger.info("Round " + roundNumber + " choosing word in room " + name + ", drawer=" + currentDrawer);

        // Record round start in room stats
        RoomNetworkStats stats = getRoomStats();
        if (stats != null) {
            stats.recordRoundStart();
        }

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
        playAgainOpen = true;
        playAgainVotes.clear();
        broadcastSystem("Match finished! Vote to play again.");
        broadcastGameStateLocked();
        broadcastPlayerListLocked();
        broadcastPlayAgainStatusLocked();
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
        if (settings.getHintMode() == HintMode.OFF) {
            return;
        }
        int total = settings.getRoundTimeSeconds();
        if (total <= 0 || currentWord == null) {
            return;
        }
        int firstReveal = total * Constants.HINT_FIRST_REVEAL_PERCENT / 100;
        int secondReveal = total * Constants.HINT_SECOND_REVEAL_PERCENT / 100;
        if (settings.getHintMode() == HintMode.LIGHT) {
            if (timeLeft == firstReveal) {
                revealRandomLetter();
            }
            return;
        }
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

    private void startNewMatchLocked() {
        playAgainOpen = false;
        playAgainVotes.clear();
        scores.replaceAll((key, value) -> 0);
        roundNumber = 0;
        resetToWaitingLocked();
        startIfReadyLocked();
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
        int recipientCount = players.size();
        for (ClientHandler handler : players.values()) {
            handler.send(message);
        }

        // Record broadcast in room stats
        RoomNetworkStats stats = getRoomStats();
        if (stats != null) {
            int estimatedSize = estimateMessageSize(message);
            stats.recordBroadcast(recipientCount, recipientCount * estimatedSize);
        }

        var serverStats = server.getNetworkStats();
        if (serverStats != null && recipientCount > 0) {
            int estimatedSize = estimateMessageSize(message);
            serverStats.recordBroadcastPacket(name, message.getClass().getSimpleName(),
                    recipientCount, recipientCount * estimatedSize);
        }
    }

    private void broadcastToOthers(String sender, Message message) {
        int recipientCount = 0;
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            if (!entry.getKey().equals(sender)) {
                entry.getValue().send(message);
                recipientCount++;
            }
        }

        // Record broadcast in room stats
        RoomNetworkStats stats = getRoomStats();
        if (stats != null && recipientCount > 0) {
            int estimatedSize = estimateMessageSize(message);
            stats.recordBroadcast(recipientCount, recipientCount * estimatedSize);
        }

        var serverStats = server.getNetworkStats();
        if (serverStats != null && recipientCount > 0) {
            int estimatedSize = estimateMessageSize(message);
            serverStats.recordBroadcastPacket(name, message.getClass().getSimpleName(),
                    recipientCount, recipientCount * estimatedSize);
        }
    }

    private void broadcastSystem(String text) {
        broadcast(new ChatMessage("System", text, ChatType.SYSTEM));
    }

    private void broadcastPlayerListLocked() {
        broadcast(buildPlayerListMessageLocked());
    }

    private void broadcastGameStateLocked() {
        for (String player : players.keySet()) {
            ClientHandler handler = players.get(player);
            if (handler != null) {
                handler.send(buildGameStateMessageLocked(player));
            }
        }
    }

    private void sendWordChoicesLocked() {
        if (currentDrawer == null) {
            return;
        }
        ClientHandler handler = players.get(currentDrawer);
        if (handler != null) {
            handler.send(new WordChoicesMessage(currentChoices, settings.getWordChoiceSeconds()));
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

    private void broadcastPlayAgainStatusLocked() {
        broadcast(buildPlayAgainStatusMessageLocked());
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

    private int estimateMessageSize(Message message) {
        if (message == null) {
            return 0;
        }
        int base = 100;
        if (message instanceof DrawMessage) {
            return base + 200;
        } else if (message instanceof ChatMessage || message instanceof GuessMessage) {
            return base + 100;
        } else if (message instanceof GameStateMessage) {
            return base + 500;
        } else if (message instanceof StrokeHistoryMessage) {
            return base + 2000;
        } else if (message instanceof WordChoicesMessage || message instanceof WordChosenMessage) {
            return base + 150;
        }
        return base + 50;
    }

    private int countPlayAgainVotesLocked() {
        int count = 0;
        for (Boolean vote : playAgainVotes.values()) {
            if (Boolean.TRUE.equals(vote)) {
                count++;
            }
        }
        return count;
    }

    private int getRequiredVotesLocked() {
        int eligible = getActivePlayersCount();
        return (eligible / 2) + 1;
    }

    private PlayerListMessage buildPlayerListMessageLocked() {
        List<PlayerInfo> list = new ArrayList<>();
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            String name = entry.getKey();
            boolean guessed = playersWhoGuessed.contains(name);
            boolean spectator = spectators.contains(name);
            list.add(
                    new PlayerInfo(name, scores.getOrDefault(name, 0), name.equals(currentDrawer), guessed, spectator));
        }
        list.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        return new PlayerListMessage(list);
    }

    private GameStateMessage buildGameStateMessageLocked(String player) {
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
        return new GameStateMessage(currentDrawer, word, timeLeft, roundNumber, phase, isDrawer);
    }

    private PlayAgainStatusMessage buildPlayAgainStatusMessageLocked() {
        int eligible = getActivePlayersCount();
        int required = getRequiredVotesLocked();
        int yesVotes = countPlayAgainVotesLocked();
        return new PlayAgainStatusMessage(yesVotes, eligible, required);
    }

    private String sanitizeText(String text) {
        String cleaned = text.replaceAll("\\p{Cntrl}", "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private boolean isCloseGuess(String guess, String answer) {
        String a = normalize(guess);
        String b = normalize(answer);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        int maxDistance = Math.max(1, b.length() / 5);
        return editDistance(a, b) <= maxDistance;
    }

    private int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
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
