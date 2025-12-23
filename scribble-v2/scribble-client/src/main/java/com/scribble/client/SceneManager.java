package com.scribble.client;

import com.scribble.client.ui.LobbyController;
import com.scribble.client.ui.LoginController;
import com.scribble.client.ui.RoomController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SceneManager {
    private final Stage stage;
    private final Client client;
    private String playerName;
    private String roomName;

    public SceneManager(Stage stage, Client client) {
        this.stage = stage;
        this.client = client;
    }

    public void showLogin(String message) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Parent root = loader.load();
            LoginController controller = loader.getController();
            controller.setSceneManager(this);
            controller.setClient(client);
            controller.setMessage(message);
            setScene(root);
            client.setListener(controller);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Login.fxml", e);
        }
    }

    public void showLobby() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Lobby.fxml"));
            Parent root = loader.load();
            LobbyController controller = loader.getController();
            controller.setSceneManager(this);
            controller.setClient(client);
            controller.setPlayerName(playerName);
            setScene(root);
            client.setListener(controller);
            controller.onShown();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lobby.fxml", e);
        }
    }

    public void showRoom() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Room.fxml"));
            Parent root = loader.load();
            RoomController controller = loader.getController();
            controller.setSceneManager(this);
            controller.setClient(client);
            controller.setPlayerName(playerName);
            controller.setRoomName(roomName);
            setScene(root);
            client.setListener(controller);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Room.fxml", e);
        }
    }

    private void setScene(Parent root) {
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
        client.setIdentity(playerName, roomName);
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
        client.setIdentity(playerName, roomName);
    }

    public void clearRoom() {
        this.roomName = null;
        client.setIdentity(playerName, null);
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getRoomName() {
        return roomName;
    }
}
