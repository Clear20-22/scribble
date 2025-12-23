package com.scribble.client.ui;

import com.scribble.client.Client;
import com.scribble.client.ClientEventListener;
import com.scribble.client.ConnectionStatus;
import com.scribble.client.SceneManager;
import com.scribble.network.messages.*;
import com.scribble.utils.Constants;
import com.scribble.utils.ValidationUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
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

    private SceneManager sceneManager;
    private Client client;
    private String playerName;
    private Timeline refreshTimeline;
    private final ObservableList<RoomSummary> roomData = FXCollections.observableArrayList();
    private final FilteredList<RoomSummary> filteredRooms = new FilteredList<>(roomData);

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
            int maxPlayers = summary.getSettings() != null ? summary.getSettings().getMaxPlayers() : Constants.MAX_PLAYERS;
            return new ReadOnlyStringWrapper(summary.getPlayerCount() + "/" + maxPlayers);
        });
        phaseColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPhase().name()));
        roundColumn.setCellValueFactory(new PropertyValueFactory<>("roundNumber"));
        settingsColumn.setCellValueFactory(cell -> {
            RoomSettings settings = cell.getValue().getSettings();
            if (settings == null) {
                return new ReadOnlyStringWrapper("-");
            }
            return new ReadOnlyStringWrapper(settings.getRoundTimeSeconds() + "s / " + settings.getTotalRounds() + "r");
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
        Dialog<RoomSettings> dialog = new Dialog<>();
        dialog.setTitle("Create Room");
        dialog.setHeaderText("Room settings");

        TextField nameField = new TextField();
        nameField.setPromptText("Room name");

        Spinner<Integer> maxPlayers = new Spinner<>(2, Constants.MAX_PLAYERS, Constants.MAX_PLAYERS);
        Spinner<Integer> roundTime = new Spinner<>(30, 120, Constants.ROUND_DURATION_SECONDS);
        Spinner<Integer> totalRounds = new Spinner<>(1, 10, Constants.DEFAULT_TOTAL_ROUNDS);
        CheckBox privateRoom = new CheckBox("Private room");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Name"), nameField);
        grid.addRow(1, new Label("Max players"), maxPlayers);
        grid.addRow(2, new Label("Round time (s)"), roundTime);
        grid.addRow(3, new Label("Total rounds"), totalRounds);
        grid.addRow(4, new Label(""), privateRoom);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String name = nameField.getText();
                ValidationUtils.ValidationResult result = ValidationUtils.validateRoomName(name);
                if (!result.isValid()) {
                    showError(result.getErrorMessage());
                    return null;
                }
                return new RoomSettings(maxPlayers.getValue(), roundTime.getValue(), totalRounds.getValue(), privateRoom.isSelected());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(settings -> {
            String name = nameField.getText().trim();
            client.send(new CreateRoomMessage(name, settings));
            joinRoom(new RoomSummary(name, 0, GamePhase.WAITING, 0, settings), false);
        });
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
        setBusy(false);
    }

    @Override
    public void onJoinResult(JoinResultMessage message) {
        setBusy(false);
        sceneManager.setSpectator(message.isSpectator());
        if (message.isSuccess()) {
            sceneManager.showRoom();
        } else {
            showError(message.getReason());
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
