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
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RoomController implements ClientEventListener {
    @FXML
    private Canvas drawCanvas;
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
    private ChoiceDialog<String> wordChoiceDialog;

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
        client.send(new RequestStrokeHistoryMessage());
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
                clearCanvas();
            }
        });

        undoButton.setOnAction(e -> handleUndo());

        leaveButton.setOnAction(e -> {
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
                    getStyleClass().removeAll("drawer-player", "guessed-player");
                } else {
                    String guessed = item.hasGuessed() ? " ✓" : "";
                    String spect = item.isSpectator() ? " (S)" : "";
                    setText(item.getName() + spect + "  " + item.getScore() + guessed);
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
        client.send(new DrawMessage(strokeId, pendingPoints, currentColor, brushSize, eraser));
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
        client.send(new GuessMessage(filtered));
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
        updateDrawerState(message.isDrawer() && !spectator);
    }

    @Override
    public void onPlayerList(PlayerListMessage message) {
        playerList.getItems().setAll(message.getPlayers());
    }

    @Override
    public void onDraw(DrawMessage message) {
        List<StrokePoint> points = message.getPoints();
        if (points.size() < 2) {
            return;
        }
        for (int i = 1; i < points.size(); i++) {
            StrokePoint a = points.get(i - 1);
            StrokePoint b = points.get(i);
            drawLine(a.getX(), a.getY(), b.getX(), b.getY(),
                    message.getColor(), message.getBrushSize(), message.isEraser());
        }
    }

    @Override
    public void onClear(ClearMessage message) {
        clearCanvas();
    }

    @Override
    public void onStrokeHistory(StrokeHistoryMessage message) {
        clearCanvas();
        for (DrawMessage draw : message.getStrokes()) {
            onDraw(draw);
        }
    }

    @Override
    public void onWordChoices(WordChoicesMessage message) {
        if (spectator) {
            return;
        }
        if (wordChoiceDialog != null) {
            wordChoiceDialog.close();
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(message.getChoices().get(0), message.getChoices());
        dialog.setTitle("Choose a word");
        dialog.setHeaderText("Pick a word (" + message.getSecondsToChoose() + "s)");
        dialog.setContentText("Word:");
        dialog.resultProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                client.send(new ChooseWordMessage(newVal));
            }
        });
        wordChoiceDialog = dialog;
        dialog.show();
    }

    @Override
    public void onWordChosen(WordChosenMessage message) {
        wordLabel.setText("Word: " + message.getWord());
        clearCanvas();
        if (wordChoiceDialog != null) {
            wordChoiceDialog.close();
            wordChoiceDialog = null;
        }
    }

    @Override
    public void onJoinResult(JoinResultMessage message) {
        if (!message.isSuccess()) {
            statusLabel.setText(message.getReason());
            return;
        }
        spectator = message.isSpectator();
        updateRoleLabel();
        client.send(new RequestStrokeHistoryMessage());
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
        chatList.getItems().add(new ChatEntry(message.getMessage(), ChatType.SYSTEM));
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
}
