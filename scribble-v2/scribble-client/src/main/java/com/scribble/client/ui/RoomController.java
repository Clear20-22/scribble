package com.scribble.client.ui;

import com.scribble.client.ChatEntry;
import com.scribble.client.Client;
import com.scribble.client.ClientEventListener;
import com.scribble.client.ConnectionStatus;
import com.scribble.client.SceneManager;
import com.scribble.network.messages.*;
import com.scribble.utils.ColorPalette;
import com.scribble.utils.Constants;
import com.scribble.utils.PaletteColor;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RoomController implements ClientEventListener {
    @FXML
    private Canvas drawCanvas;
    @FXML
    private StackPane canvasFrame;
    @FXML
    private HBox paletteBox;
    @FXML
    private Slider brushSlider;
    @FXML
    private ToggleButton eraserToggle;
    @FXML
    private Button clearButton;
    @FXML
    private Button leaveButton;
    @FXML
    private Label wordLabel;
    @FXML
    private Label timerLabel;
    @FXML
    private Label roundLabel;
    @FXML
    private Label drawerLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private javafx.scene.control.ProgressIndicator chooseSpinner;
    @FXML
    private Label connectionStatusLabel;
    @FXML
    private Label pingLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private ListView<PlayerInfo> playerList;
    @FXML
    private ListView<ChatEntry> chatList;
    @FXML
    private TextField chatInput;
    @FXML
    private Button undoButton;
    @FXML
    private VBox wordChoiceOverlay;
    @FXML
    private Label wordChoiceTitle;
    @FXML
    private Label wordChoiceTimer;
    @FXML
    private HBox wordChoiceButtons;
    @FXML
    private Label choosingHintLabel;
    @FXML
    private VBox leaderboardOverlay;
    @FXML
    private Label leaderboardTitle;
    @FXML
    private VBox leaderboardRows;
    @FXML
    private Label leaderboardVoteLabel;
    @FXML
    private Button playAgainButton;
    @FXML
    private Button returnLobbyButton;
    @FXML
    private Button leaveRoomButton;

    private SceneManager sceneManager;
    private Client client;
    private String playerName;
    private String roomName;
    private boolean spectator;

    private GraphicsContext gc;
    private String currentColor = "#1F1F1F";
    private double brushSize = 4.0;
    private boolean isDrawer;
    private boolean eraser;
    private double lastX;
    private double lastY;
    private long lastSentAt;
    private String strokeId;
    private List<StrokePoint> pendingPoints;
    private Timeline wordChoiceTimeline;
    private int wordChoiceSecondsLeft;
    private final List<DrawMessage> localStrokes = new ArrayList<>();
    private FadeTransition overlayFade;
    private ScaleTransition overlayScale;
    private FadeTransition leaderboardFade;
    private ScaleTransition leaderboardScale;
    private List<PlayerInfo> lastPlayers = new ArrayList<>();
    private PlayAgainStatusMessage lastPlayAgainStatus;

    public void setSceneManager(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }

    public void onShown() {
        client.send(new RequestStateMessage());
        updateRoleLabel();
    }

    @FXML
    private void initialize() {
        gc = drawCanvas.getGraphicsContext2D();
        clearCanvas();
        setupPalette();
        setupPlayerList();
        setupChatList();
        pendingPoints = new ArrayList<>();
        onConnectionStatus(ConnectionStatus.CONNECTED);

        brushSlider.valueProperty().addListener((obs, oldVal, newVal) -> brushSize = newVal.doubleValue());
        eraserToggle.selectedProperty().addListener((obs, oldVal, newVal) -> eraser = newVal);

        drawCanvas.setOnMousePressed(e -> {
            if (!isDrawer) {
                return;
            }
            lastX = e.getX();
            lastY = e.getY();
            strokeId = UUID.randomUUID().toString();
            pendingPoints.clear();
            pendingPoints.add(new StrokePoint(lastX, lastY));
        });

        drawCanvas.setOnMouseDragged(e -> {
            if (!isDrawer) {
                return;
            }
            double x = e.getX();
            double y = e.getY();
            drawLine(lastX, lastY, x, y, currentColor, brushSize, eraser);
            pendingPoints.add(new StrokePoint(x, y));
            flushStroke(false);
            lastX = x;
            lastY = y;
        });

        drawCanvas.setOnMouseReleased(e -> flushStroke(true));

        drawCanvas.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.C && isDrawer && !chatInput.isFocused()) {
                        handleClearShortcut();
                        event.consume();
                    }
                    if (event.isControlDown() && event.getCode() == KeyCode.Z && isDrawer) {
                        handleUndo();
                        event.consume();
                    }
                });
            }
        });

        clearButton.setOnAction(e -> {
            if (isDrawer) {
                client.send(new ClearMessage());
                localStrokes.clear();
                clearCanvas();
            }
        });

        undoButton.setOnAction(e -> handleUndo());

        leaveButton.setOnAction(e -> {
            client.send(new LeaveRoomMessage());
            sceneManager.clearRoom();
            sceneManager.showLobby();
        });

        playAgainButton.setOnAction(e -> client.send(new PlayAgainRequestMessage(true)));
        returnLobbyButton.setOnAction(e -> {
            client.send(new LeaveRoomMessage());
            sceneManager.clearRoom();
            sceneManager.showLobby();
        });
        leaveRoomButton.setOnAction(e -> {
            client.send(new LeaveRoomMessage());
            sceneManager.clearRoom();
            sceneManager.showLobby();
        });

        chatInput.setOnAction(e -> sendChat());

    }

    private void setupPalette() {
        for (PaletteColor color : ColorPalette.getColors()) {
            Button button = new Button();
            button.getStyleClass().add("color-swatch");
            button.setStyle("-fx-background-color: " + color.getHex() + ";");
            button.setOnAction(e -> currentColor = color.getHex());
            paletteBox.getChildren().add(button);
        }
    }

    private void setupPlayerList() {
        playerList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(PlayerInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("drawer-player", "guessed-player", "rank-1", "rank-2", "rank-3");
                } else {
                    String guessed = item.hasGuessed() ? " ✓" : "";
                    String spect = item.isSpectator() ? " (S)" : "";
                    int rank = getIndex() + 1;
                    setText(rank + ". " + item.getName() + spect + "  " + item.getScore() + guessed);
                    getStyleClass().removeAll("rank-1", "rank-2", "rank-3");
                    if (rank <= 3) {
                        getStyleClass().add("rank-" + rank);
                    }
                    if (item.isDrawer()) {
                        if (!getStyleClass().contains("drawer-player")) {
                            getStyleClass().add("drawer-player");
                        }
                        getStyleClass().removeAll("guessed-player");
                    } else {
                        getStyleClass().removeAll("drawer-player");
                        if (item.hasGuessed() && !getStyleClass().contains("guessed-player")) {
                            getStyleClass().add("guessed-player");
                        }
                    }
                }
            }
        });
    }

    private void setupChatList() {
        chatList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ChatEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("chat-system", "chat-correct");
                } else {
                    setText(item.getText());
                    getStyleClass().removeAll("chat-system", "chat-correct");
                    if (item.getType() == ChatType.SYSTEM) {
                        getStyleClass().add("chat-system");
                    } else if (item.getType() == ChatType.CORRECT) {
                        getStyleClass().add("chat-correct");
                    }
                }
            }
        });
    }

    private void flushStroke(boolean force) {
        long now = System.currentTimeMillis();
        if (pendingPoints.size() < 2) {
            return;
        }
        if (!force && now - lastSentAt < Constants.DRAW_BATCH_INTERVAL_MS
                && pendingPoints.size() < Constants.DRAW_BATCH_SIZE) {
            return;
        }
        lastSentAt = now;
        DrawMessage drawMessage = new DrawMessage(strokeId, pendingPoints, currentColor, brushSize, eraser);
        client.send(drawMessage);
        localStrokes.add(drawMessage);
        StrokePoint last = pendingPoints.get(pendingPoints.size() - 1);
        pendingPoints = new ArrayList<>();
        pendingPoints.add(last);
    }

    private void sendChat() {
        String text = chatInput.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String filtered = filterProfanity(text.trim());
        if (spectator || isDrawer) {
            client.send(new ChatMessage(filtered));
        } else {
            client.send(new GuessMessage(filtered));
        }
        chatInput.clear();
    }

    private String filterProfanity(String input) {
        String[] blocked = {"badword", "uglyword"};
        String output = input;
        for (String word : blocked) {
            output = output.replaceAll("(?i)" + word, "***");
        }
        return output;
    }

    private void drawLine(double startX, double startY, double endX, double endY, String color, double size, boolean eraser) {
        gc.setStroke(eraser ? Color.WHITE : Color.web(color));
        gc.setLineWidth(size);
        gc.strokeLine(startX, startY, endX, endY);
    }

    private void clearCanvas() {
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, drawCanvas.getWidth(), drawCanvas.getHeight());
    }

    private void updateDrawerState(boolean drawer) {
        isDrawer = drawer;
        clearButton.setDisable(!drawer);
        eraserToggle.setDisable(!drawer);
        paletteBox.setDisable(!drawer);
        brushSlider.setDisable(!drawer);
        undoButton.setDisable(!drawer);
    }

    private void handleClearShortcut() {
        if (!isDrawer) {
            return;
        }
        client.send(new ClearMessage());
        localStrokes.clear();
        clearCanvas();
    }

    private void handleUndo() {
        if (!isDrawer) {
            return;
        }
        client.send(new UndoMessage("LAST"));
    }

    private void updateRoleLabel() {
        roleLabel.setText(spectator ? "Spectator" : "Player");
    }

    @Override
    public void onGameState(GameStateMessage message) {
        wordLabel.setText("Word: " + message.getWord());
        timerLabel.setText("Time: " + message.getTimeLeft());
        roundLabel.setText("Round: " + message.getRoundNumber());
        drawerLabel.setText(message.isDrawer() ? "You are drawing" : "Drawer: " + message.getCurrentDrawer());
        String phaseText = switch (message.getPhase()) {
            case WAITING -> "WAITING";
            case CHOOSING_WORD -> "CHOOSING";
            case RUNNING -> "DRAWING";
            case INTERMISSION -> "ROUND END";
            case FINISHED -> "MATCH END";
        };
        statusLabel.setText(phaseText);
        boolean showSpinner = message.getPhase() == GamePhase.CHOOSING_WORD && !message.isDrawer();
        chooseSpinner.setVisible(showSpinner);
        chooseSpinner.setManaged(showSpinner);
        boolean showHint = message.getPhase() == GamePhase.CHOOSING_WORD && !message.isDrawer();
        choosingHintLabel.setVisible(showHint);
        choosingHintLabel.setManaged(showHint);
        updateDrawerState(message.isDrawer() && !spectator);
        if (message.getPhase() == GamePhase.CHOOSING_WORD && message.isDrawer()) {
            updateWordChoiceTimer(message.getTimeLeft());
        } else {
            hideWordChoiceOverlay();
        }
        if (message.getPhase() == GamePhase.FINISHED) {
            showLeaderboard();
        } else {
            hideLeaderboard();
        }
    }

    @Override
    public void onPlayerList(PlayerListMessage message) {
        playerList.getItems().setAll(message.getPlayers());
        lastPlayers = new ArrayList<>(message.getPlayers());
        updateLeaderboardRows();
    }

    @Override
    public void onPlayAgainStatus(PlayAgainStatusMessage message) {
        lastPlayAgainStatus = message;
        updateLeaderboardVoteLabel();
    }

    @Override
    public void onDraw(DrawMessage message) {
        List<StrokePoint> points = message.getPoints();
        if (points.size() < 2) {
            return;
        }
        localStrokes.add(message);
        for (int i = 1; i < points.size(); i++) {
            StrokePoint a = points.get(i - 1);
            StrokePoint b = points.get(i);
            drawLine(a.getX(), a.getY(), b.getX(), b.getY(),
                    message.getColor(), message.getBrushSize(), message.isEraser());
        }
    }

    @Override
    public void onClear(ClearMessage message) {
        localStrokes.clear();
        clearCanvas();
    }

    @Override
    public void onStrokeHistory(StrokeHistoryMessage message) {
        localStrokes.clear();
        localStrokes.addAll(message.getStrokes());
        redrawCanvas();
    }

    @Override
    public void onWordChoices(WordChoicesMessage message) {
        if (spectator) {
            return;
        }
        showWordChoiceOverlay(message.getChoices(), message.getSecondsToChoose());
    }

    @Override
    public void onWordChosen(WordChosenMessage message) {
        wordLabel.setText("Word: " + message.getWord());
        localStrokes.clear();
        clearCanvas();
        hideWordChoiceOverlay();
    }

    @Override
    public void onJoinResult(JoinResultMessage message) {
        if (!message.isSuccess()) {
            statusLabel.setText(message.getReason());
            return;
        }
        spectator = message.isSpectator();
        updateRoleLabel();
        client.send(new RequestStateMessage());
    }

    @Override
    public void onChat(ChatMessage message) {
        String text;
        if (message.getType() == ChatType.SYSTEM) {
            text = message.getText();
        } else if (message.getType() == ChatType.CORRECT) {
            text = message.getSender() + " " + message.getText();
        } else {
            text = message.getSender() + ": " + message.getText();
        }
        chatList.getItems().add(new ChatEntry(text, message.getType()));
        chatList.scrollTo(chatList.getItems().size() - 1);
    }

    @Override
    public void onDisconnected(String reason) {
        statusLabel.setText(reason);
    }

    @Override
    public void onError(ErrorMessage message) {
        String code = message.getCode();
        if ("DRAW_RATE".equals(code) || "WARN".equals(code)) {
            return;
        }
        if ("MUTED".equals(code)) {
            statusLabel.setText(message.getMessage());
            return;
        }
        chatList.getItems().add(new ChatEntry(message.getMessage(), ChatType.SYSTEM));
    }

    @Override
    public void onGuessHint(GuessHintMessage message) {
        chatList.getItems().add(new ChatEntry(message.getHint(), ChatType.SYSTEM));
        chatList.scrollTo(chatList.getItems().size() - 1);
    }

    @Override
    public void onConnectionStatus(ConnectionStatus status) {
        connectionStatusLabel.setText(status.name());
        connectionStatusLabel.getStyleClass().removeAll("status-connected", "status-reconnecting", "status-disconnected");
        switch (status) {
            case CONNECTED -> connectionStatusLabel.getStyleClass().add("status-connected");
            case RECONNECTING -> connectionStatusLabel.getStyleClass().add("status-reconnecting");
            case DISCONNECTED -> connectionStatusLabel.getStyleClass().add("status-disconnected");
        }
    }

    @Override
    public void onLatency(long latencyMs) {
        pingLabel.setText(latencyMs + " ms");
    }

    private void showWordChoiceOverlay(List<String> choices, int secondsToChoose) {
        wordChoiceButtons.getChildren().clear();
        for (String choice : choices) {
            Button button = new Button(choice);
            button.getStyleClass().add("choice-button");
            button.setOnAction(e -> client.send(new ChooseWordMessage(choice)));
            wordChoiceButtons.getChildren().add(button);
        }
        wordChoiceTitle.setText("Choose a word");
        wordChoiceSecondsLeft = secondsToChoose;
        updateWordChoiceTimer(wordChoiceSecondsLeft);
        wordChoiceOverlay.setVisible(true);
        wordChoiceOverlay.setManaged(true);
        playOverlayIn();
        startWordChoiceCountdown();
    }

    private void startWordChoiceCountdown() {
        stopWordChoiceCountdown();
        wordChoiceTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (wordChoiceSecondsLeft > 0) {
                wordChoiceSecondsLeft--;
                updateWordChoiceTimer(wordChoiceSecondsLeft);
            } else {
                disableWordChoiceButtons(true);
                stopWordChoiceCountdown();
            }
        }));
        wordChoiceTimeline.setCycleCount(Timeline.INDEFINITE);
        wordChoiceTimeline.play();
    }

    private void stopWordChoiceCountdown() {
        if (wordChoiceTimeline != null) {
            wordChoiceTimeline.stop();
            wordChoiceTimeline = null;
        }
    }

    private void updateWordChoiceTimer(int secondsLeft) {
        wordChoiceTimer.setText("Time left: " + secondsLeft + "s");
    }

    private void disableWordChoiceButtons(boolean disabled) {
        for (javafx.scene.Node node : wordChoiceButtons.getChildren()) {
            node.setDisable(disabled);
        }
    }

    private void hideWordChoiceOverlay() {
        stopWordChoiceCountdown();
        disableWordChoiceButtons(false);
        playOverlayOut();
    }

    private void playOverlayIn() {
        if (overlayFade != null) {
            overlayFade.stop();
        }
        if (overlayScale != null) {
            overlayScale.stop();
        }
        wordChoiceOverlay.setOpacity(0);
        wordChoiceOverlay.setScaleX(0.96);
        wordChoiceOverlay.setScaleY(0.96);
        overlayFade = new FadeTransition(Duration.millis(180), wordChoiceOverlay);
        overlayFade.setFromValue(0);
        overlayFade.setToValue(1);
        overlayScale = new ScaleTransition(Duration.millis(180), wordChoiceOverlay);
        overlayScale.setFromX(0.96);
        overlayScale.setFromY(0.96);
        overlayScale.setToX(1);
        overlayScale.setToY(1);
        overlayFade.play();
        overlayScale.play();
    }

    private void playOverlayOut() {
        if (!wordChoiceOverlay.isVisible()) {
            return;
        }
        if (overlayFade != null) {
            overlayFade.stop();
        }
        overlayFade = new FadeTransition(Duration.millis(140), wordChoiceOverlay);
        overlayFade.setFromValue(wordChoiceOverlay.getOpacity());
        overlayFade.setToValue(0);
        overlayFade.setOnFinished(event -> {
            wordChoiceOverlay.setVisible(false);
            wordChoiceOverlay.setManaged(false);
            wordChoiceOverlay.setOpacity(1);
        });
        overlayFade.play();
    }

    private void showLeaderboard() {
        updateLeaderboardRows();
        updateLeaderboardVoteLabel();
        leaderboardOverlay.setVisible(true);
        leaderboardOverlay.setManaged(true);
        playLeaderboardIn();
    }

    private void hideLeaderboard() {
        if (!leaderboardOverlay.isVisible()) {
            return;
        }
        if (leaderboardFade != null) {
            leaderboardFade.stop();
        }
        leaderboardFade = new FadeTransition(Duration.millis(140), leaderboardOverlay);
        leaderboardFade.setFromValue(leaderboardOverlay.getOpacity());
        leaderboardFade.setToValue(0);
        leaderboardFade.setOnFinished(event -> {
            leaderboardOverlay.setVisible(false);
            leaderboardOverlay.setManaged(false);
            leaderboardOverlay.setOpacity(1);
        });
        leaderboardFade.play();
    }

    private void playLeaderboardIn() {
        if (leaderboardFade != null) {
            leaderboardFade.stop();
        }
        if (leaderboardScale != null) {
            leaderboardScale.stop();
        }
        leaderboardOverlay.setOpacity(0);
        leaderboardOverlay.setScaleX(0.96);
        leaderboardOverlay.setScaleY(0.96);
        leaderboardFade = new FadeTransition(Duration.millis(200), leaderboardOverlay);
        leaderboardFade.setFromValue(0);
        leaderboardFade.setToValue(1);
        leaderboardScale = new ScaleTransition(Duration.millis(200), leaderboardOverlay);
        leaderboardScale.setFromX(0.96);
        leaderboardScale.setFromY(0.96);
        leaderboardScale.setToX(1);
        leaderboardScale.setToY(1);
        leaderboardFade.play();
        leaderboardScale.play();
        animateLeaderboardRows();
    }

    private void updateLeaderboardRows() {
        if (leaderboardRows == null) {
            return;
        }
        leaderboardRows.getChildren().clear();
        int count = Math.min(3, lastPlayers.size());
        for (int i = 0; i < count; i++) {
            PlayerInfo player = lastPlayers.get(i);
            int rank = i + 1;
            HBox row = new HBox();
            row.setSpacing(10);
            row.getStyleClass().add("leaderboard-row");
            row.getStyleClass().add("rank-" + rank);

            Region icon = new Region();
            icon.getStyleClass().add("leaderboard-icon");
            icon.getStyleClass().add("icon-rank-" + rank);
            Label rankLabel = new Label(rank + getRankSuffix(rank));
            rankLabel.getStyleClass().add("leaderboard-rank");
            Label nameLabel = new Label(player.getName());
            Label scoreLabel = new Label(player.getScore() + " pts");
            scoreLabel.getStyleClass().add("leaderboard-score");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row.getChildren().addAll(icon, rankLabel, nameLabel, spacer, scoreLabel);
            leaderboardRows.getChildren().add(row);
        }
    }

    private void animateLeaderboardRows() {
        int index = 0;
        for (javafx.scene.Node node : leaderboardRows.getChildren()) {
            node.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(180), node);
            fade.setDelay(Duration.millis(60L * index));
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
            index++;
        }
    }

    private String getRankSuffix(int rank) {
        return switch (rank) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private void updateLeaderboardVoteLabel() {
        if (leaderboardVoteLabel == null) {
            return;
        }
        if (lastPlayAgainStatus == null) {
            leaderboardVoteLabel.setText("Waiting for votes...");
            return;
        }
        leaderboardVoteLabel.setText("Play again votes: "
                + lastPlayAgainStatus.getYesVotes()
                + " / " + lastPlayAgainStatus.getRequiredVotes());
    }

    private void redrawCanvas() {
        clearCanvas();
        for (DrawMessage draw : localStrokes) {
            List<StrokePoint> points = draw.getPoints();
            for (int i = 1; i < points.size(); i++) {
                StrokePoint a = points.get(i - 1);
                StrokePoint b = points.get(i);
                drawLine(a.getX(), a.getY(), b.getX(), b.getY(),
                        draw.getColor(), draw.getBrushSize(), draw.isEraser());
            }
        }
    }

}
