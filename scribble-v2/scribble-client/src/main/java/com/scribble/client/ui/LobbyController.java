package com.scribble.client.ui;

import com.scribble.client.Client;
import com.scribble.client.ClientEventListener;
import com.scribble.client.ConnectionStatus;
import com.scribble.client.SceneManager;
import com.scribble.network.messages.*;
import com.scribble.utils.Constants;
import com.scribble.utils.ValidationUtils;
import javafx.animation.KeyFrame;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class LobbyController implements ClientEventListener {
    @FXML
    private TableView<RoomSummary> roomTable;
    @FXML
    private TableColumn<RoomSummary, String> nameColumn;
    @FXML
    private TableColumn<RoomSummary, String> playersColumn;
    @FXML
    private TableColumn<RoomSummary, String> phaseColumn;
    @FXML
    private TableColumn<RoomSummary, Integer> roundColumn;
    @FXML
    private TableColumn<RoomSummary, String> settingsColumn;
    @FXML
    private TableColumn<RoomSummary, Void> joinColumn;
    @FXML
    private Button createButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label connectionStatusLabel;
    @FXML
    private Label pingLabel;
    @FXML
    private TextField searchField;
    @FXML
    private CheckBox hideFullCheck;
    @FXML
    private VBox createOverlay;
    @FXML
    private VBox createCard;
    @FXML
    private TextField roomNameField;
    @FXML
    private Label roomNameError;
    @FXML
    private Spinner<Integer> maxPlayersSpinner;
    @FXML
    private Spinner<Integer> roundCountSpinner;
    @FXML
    private Spinner<Integer> roundTimeSpinner;
    @FXML
    private Spinner<Integer> wordChoiceSpinner;
    @FXML
    private ComboBox<WordDifficulty> difficultyBox;
    @FXML
    private HBox hintToggleBox;
    @FXML
    private HBox privacyToggleBox;
    @FXML
    private TextField roomCodeField;
    @FXML
    private CheckBox allowSpectatorsCheck;
    @FXML
    private Label createErrorLabel;
    @FXML
    private Button createConfirmButton;
    @FXML
    private Button createCancelButton;

    private SceneManager sceneManager;
    private Client client;
    private String playerName;
    private Timeline refreshTimeline;
    private FadeTransition createFade;
    private ScaleTransition createScale;
    private ToggleGroup hintGroup;
    private ToggleGroup privacyGroup;
    private final ObservableList<RoomSummary> roomData = FXCollections.observableArrayList();
    private final FilteredList<RoomSummary> filteredRooms = new FilteredList<>(roomData);

    // Track pending room join after creation
    private String pendingJoinRoom = null;
    private boolean pendingJoinSpectator = false;

    public void setSceneManager(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    @FXML
    private void initialize() {
        roomTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        playersColumn.setCellValueFactory(cell -> {
            RoomSummary summary = cell.getValue();
            int maxPlayers = summary.getSettings() != null ? summary.getSettings().getMaxPlayers()
                    : Constants.MAX_PLAYERS;
            return new ReadOnlyStringWrapper(summary.getPlayerCount() + "/" + maxPlayers);
        });
        phaseColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPhase().name()));
        roundColumn.setCellValueFactory(new PropertyValueFactory<>("roundNumber"));
        settingsColumn.setCellValueFactory(cell -> {
            RoomSettings settings = cell.getValue().getSettings();
            if (settings == null) {
                return new ReadOnlyStringWrapper("-");
            }
            return new ReadOnlyStringWrapper(settings.getRoundTimeSeconds() + "s / "
                    + settings.getTotalRounds() + "r / "
                    + settings.getWordChoiceSeconds() + "s pick");
        });
        joinColumn.setCellFactory(col -> new TableCell<>() {
            private final Button joinButton = new Button("Join");

            {
                joinButton.getStyleClass().add("secondary-button");
                joinButton.setOnAction(e -> {
                    RoomSummary summary = getTableView().getItems().get(getIndex());
                    if (summary != null) {
                        joinRoom(summary, shouldSpectate(summary));
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                RoomSummary summary = getTableView().getItems().get(getIndex());
                if (summary != null) {
                    joinButton.setText(shouldSpectate(summary) ? "Spectate" : "Join");
                }
                setGraphic(joinButton);
            }
        });

        roomTable.setItems(filteredRooms);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        hideFullCheck.selectedProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        setupCreateForm();
    }

    public void onShown() {
        refreshRooms();
        onConnectionStatus(ConnectionStatus.CONNECTED);
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(4), e -> refreshRooms()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    @FXML
    private void onRefresh() {
        refreshRooms();
    }

    @FXML
    private void onCreateRoom() {
        openCreateOverlay();
    }

    private void joinRoom(RoomSummary summary, boolean spectator) {
        sceneManager.setRoomName(summary.getName());
        sceneManager.setSpectator(spectator);
        client.send(new JoinMessage(summary.getName(), playerName, spectator));
        setBusy(true);
    }

    private boolean shouldSpectate(RoomSummary summary) {
        if (summary == null) {
            return false;
        }
        RoomSettings settings = summary.getSettings();
        boolean full = settings != null && summary.getPlayerCount() >= settings.getMaxPlayers();
        boolean active = summary.getPhase() == GamePhase.RUNNING || summary.getPhase() == GamePhase.CHOOSING_WORD;
        return full || active;
    }

    private void refreshRooms() {
        client.send(new ListRoomsMessage());
    }

    private void setBusy(boolean busy) {
        createButton.setDisable(busy);
        refreshButton.setDisable(busy);
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().add("error-text");
    }

    private void setupCreateForm() {
        maxPlayersSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                Constants.MIN_PLAYERS, Constants.MAX_PLAYERS, Constants.MAX_PLAYERS));
        roundCountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, 10, Constants.DEFAULT_TOTAL_ROUNDS));
        roundTimeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                Constants.MIN_ROUND_SECONDS, Constants.MAX_ROUND_SECONDS, Constants.ROUND_DURATION_SECONDS, 5));
        wordChoiceSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                Constants.MIN_WORD_CHOICE_SECONDS, Constants.MAX_WORD_CHOICE_SECONDS, Constants.CHOOSE_WORD_SECONDS));
        difficultyBox.setItems(FXCollections.observableArrayList(WordDifficulty.values()));
        difficultyBox.getSelectionModel().select(WordDifficulty.MEDIUM);

        hintGroup = new ToggleGroup();
        RadioButton hintOff = new RadioButton("Off");
        RadioButton hintLight = new RadioButton("Light");
        RadioButton hintNormal = new RadioButton("Normal");
        hintOff.setToggleGroup(hintGroup);
        hintLight.setToggleGroup(hintGroup);
        hintNormal.setToggleGroup(hintGroup);
        hintNormal.setSelected(true);
        hintToggleBox.getChildren().setAll(hintOff, hintLight, hintNormal);

        privacyGroup = new ToggleGroup();
        RadioButton publicBtn = new RadioButton("Public");
        RadioButton privateBtn = new RadioButton("Private");
        publicBtn.setToggleGroup(privacyGroup);
        privateBtn.setToggleGroup(privacyGroup);
        publicBtn.setSelected(true);
        privacyToggleBox.getChildren().setAll(publicBtn, privateBtn);

        allowSpectatorsCheck.setSelected(true);
        roomCodeField.setDisable(true);

        privacyGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isPrivate = newVal == privateBtn;
            roomCodeField.setDisable(!isPrivate);
        });

        roomNameField.textProperty().addListener((obs, oldVal, newVal) -> validateCreateForm());
        createConfirmButton.setOnAction(e -> submitCreateRoom());
        createCancelButton.setOnAction(e -> closeCreateOverlay());

        validateCreateForm();
    }

    private void validateCreateForm() {
        ValidationUtils.ValidationResult nameResult = ValidationUtils.validateRoomName(roomNameField.getText());
        if (!nameResult.isValid()) {
            roomNameError.setText(nameResult.getErrorMessage());
        } else {
            roomNameError.setText("");
        }
        createConfirmButton.setDisable(!nameResult.isValid());
    }

    private void submitCreateRoom() {
        ValidationUtils.ValidationResult nameResult = ValidationUtils.validateRoomName(roomNameField.getText());
        if (!nameResult.isValid()) {
            roomNameError.setText(nameResult.getErrorMessage());
            return;
        }
        boolean isPrivate = ((RadioButton) privacyGroup.getSelectedToggle()).getText().equals("Private");
        String roomCode = roomCodeField.isDisabled() ? null : roomCodeField.getText().trim();
        HintMode hintMode = HintMode.NORMAL;
        Toggle selectedHint = hintGroup.getSelectedToggle();
        if (selectedHint != null) {
            String label = ((RadioButton) selectedHint).getText().toUpperCase();
            hintMode = HintMode.valueOf(label);
        }

        RoomSettings settings = new RoomSettings(
                maxPlayersSpinner.getValue(),
                roundTimeSpinner.getValue(),
                roundCountSpinner.getValue(),
                isPrivate,
                roomCode == null || roomCode.isBlank() ? null : roomCode,
                allowSpectatorsCheck.isSelected(),
                wordChoiceSpinner.getValue(),
                hintMode,
                difficultyBox.getValue() == null ? WordDifficulty.MEDIUM : difficultyBox.getValue());

        String name = roomNameField.getText().trim();
        // Set pending join - will join once room appears in room list
        pendingJoinRoom = name;
        pendingJoinSpectator = false;
        client.send(new CreateRoomMessage(name, settings));
        closeCreateOverlay();
        setBusy(true);
        statusLabel.setText("Creating room...");
    }

    private void openCreateOverlay() {
        createErrorLabel.setText("");
        createOverlay.setVisible(true);
        createOverlay.setManaged(true);
        createOverlay.setOpacity(0);
        createOverlay.setScaleX(0.96);
        createOverlay.setScaleY(0.96);
        if (createFade != null) {
            createFade.stop();
        }
        if (createScale != null) {
            createScale.stop();
        }
        createFade = new FadeTransition(Duration.millis(180), createOverlay);
        createFade.setFromValue(0);
        createFade.setToValue(1);
        createScale = new ScaleTransition(Duration.millis(180), createOverlay);
        createScale.setFromX(0.96);
        createScale.setFromY(0.96);
        createScale.setToX(1);
        createScale.setToY(1);
        createFade.play();
        createScale.play();
        roomNameField.requestFocus();
    }

    private void closeCreateOverlay() {
        if (!createOverlay.isVisible()) {
            return;
        }
        if (createFade != null) {
            createFade.stop();
        }
        createFade = new FadeTransition(Duration.millis(140), createOverlay);
        createFade.setFromValue(createOverlay.getOpacity());
        createFade.setToValue(0);
        createFade.setOnFinished(event -> {
            createOverlay.setVisible(false);
            createOverlay.setManaged(false);
            createOverlay.setOpacity(1);
        });
        createFade.play();
    }

    private void applyFilters() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        boolean hideFull = hideFullCheck.isSelected();
        filteredRooms.setPredicate(summary -> {
            if (summary == null) {
                return false;
            }
            if (!query.isEmpty() && !summary.getName().toLowerCase().contains(query)) {
                return false;
            }
            if (hideFull && summary.getSettings() != null) {
                return summary.getPlayerCount() < summary.getSettings().getMaxPlayers();
            }
            return true;
        });
    }

    @Override
    public void onRoomList(RoomListMessage message) {
        roomData.setAll(message.getRooms());
        applyFilters();
        statusLabel.setText("");

        // Check if we have a pending room join after creation
        if (pendingJoinRoom != null) {
            String roomToJoin = pendingJoinRoom;
            boolean spectator = pendingJoinSpectator;
            pendingJoinRoom = null;
            pendingJoinSpectator = false;

            // Find the room we just created
            RoomSummary targetRoom = null;
            for (RoomSummary room : message.getRooms()) {
                if (room.getName().equals(roomToJoin)) {
                    targetRoom = room;
                    break;
                }
            }

            if (targetRoom != null) {
                joinRoom(targetRoom, spectator);
            } else {
                // Room not found - maybe it failed to create
                setBusy(false);
                showError("Failed to create room");
            }
        } else {
            setBusy(false);
        }
    }

    @Override
    public void onJoinResult(JoinResultMessage message) {
        setBusy(false);
        sceneManager.setSpectator(message.isSpectator());
        if (message.isSuccess()) {
            sceneManager.showRoom();
        } else {
            if (createOverlay.isVisible()) {
                createErrorLabel.setText(message.getReason());
            } else {
                showError(message.getReason());
            }
        }
    }

    @Override
    public void onDisconnected(String reason) {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        showError(reason);
    }

    @Override
    public void onError(ErrorMessage message) {
        showError(message.getMessage());
    }

    @Override
    public void onConnectionStatus(ConnectionStatus status) {
        connectionStatusLabel.setText(status.name());
        connectionStatusLabel.getStyleClass().removeAll("status-connected", "status-reconnecting",
                "status-disconnected");
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
