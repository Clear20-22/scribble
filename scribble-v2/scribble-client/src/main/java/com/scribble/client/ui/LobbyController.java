package com.scribble.client.ui;

import com.scribble.client.Client;
import com.scribble.client.ClientEventListener;
import com.scribble.client.ConnectionStatus;
import com.scribble.client.SceneManager;
import com.scribble.network.messages.*;
import com.scribble.utils.ValidationUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;

public class LobbyController implements ClientEventListener {
    @FXML
    private TableView<RoomSummary> roomTable;
    @FXML
    private TableColumn<RoomSummary, String> nameColumn;
    @FXML
    private TableColumn<RoomSummary, String> playersColumn;
    @FXML
    private TableColumn<RoomSummary, String> statusColumn;
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

    private SceneManager sceneManager;
    private Client client;
    private String playerName;
    private Timeline refreshTimeline;

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
            return new ReadOnlyStringWrapper(summary.getPlayerCount() + "/" + com.scribble.utils.Constants.MAX_PLAYERS);
        });
        statusColumn.setCellValueFactory(cell -> {
            RoomSummary summary = cell.getValue();
            String status = summary.isInGame() ? "PLAYING" : "WAITING";
            return new ReadOnlyStringWrapper(status + "  R" + summary.getRoundNumber());
        });
        joinColumn.setCellFactory(col -> new TableCell<>() {
            private final Button joinButton = new Button("Join");

            {
                joinButton.getStyleClass().add("secondary-button");
                joinButton.setOnAction(e -> {
                    RoomSummary summary = getTableView().getItems().get(getIndex());
                    if (summary != null) {
                        joinRoom(summary);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : joinButton);
            }
        });
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
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Room");
        dialog.setHeaderText("Create a new room");
        dialog.setContentText("Room name:");
        dialog.getEditor().setPromptText("Room name");
        dialog.getEditor().setText("");

        dialog.showAndWait().ifPresent(name -> {
            ValidationUtils.ValidationResult result = ValidationUtils.validateRoomName(name);
            if (!result.isValid()) {
                showError(result.getErrorMessage());
                return;
            }
            joinRoom(new RoomSummary(name.trim(), 0, false, 0));
        });
    }

    private void joinRoom(RoomSummary summary) {
        sceneManager.setRoomName(summary.getName());
        client.send(new JoinMessage(summary.getName(), playerName));
        setBusy(true);
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

    @Override
    public void onRoomList(RoomListMessage message) {
        roomTable.getItems().setAll(message.getRooms());
        statusLabel.setText("");
        setBusy(false);
    }

    @Override
    public void onJoinResult(JoinResultMessage message) {
        setBusy(false);
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
}
