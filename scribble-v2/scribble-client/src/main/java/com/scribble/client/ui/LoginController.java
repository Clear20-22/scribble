package com.scribble.client.ui;

import com.scribble.client.Client;
import com.scribble.client.ClientEventListener;
import com.scribble.client.ConnectionStatus;
import com.scribble.client.SceneManager;
import com.scribble.network.messages.ErrorMessage;
import com.scribble.network.messages.JoinResultMessage;
import com.scribble.utils.Constants;
import com.scribble.utils.ValidationUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class LoginController implements ClientEventListener {
    @FXML
    private TextField usernameField;
    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private Button connectButton;
    @FXML
    private Label statusLabel;

    private SceneManager sceneManager;
    private Client client;

    public void setSceneManager(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setMessage(String message) {
        if (message != null && !message.isEmpty()) {
            statusLabel.setText(message);
            statusLabel.getStyleClass().add("error-text");
        }
    }

    @FXML
    private void initialize() {
        hostField.setText(Constants.DEFAULT_HOST);
        portField.setText(String.valueOf(Constants.DEFAULT_PORT));
    }

    @FXML
    private void onConnect() {
        String username = usernameField.getText();
        ValidationUtils.ValidationResult nameValidation = ValidationUtils.validatePlayerName(username);
        if (!nameValidation.isValid()) {
            showError(nameValidation.getErrorMessage());
            return;
        }
        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            showError("Host cannot be empty");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            showError("Invalid port");
            return;
        }

        statusLabel.setText("Connecting...");
        if (client.connect(host, port)) {
            sceneManager.setPlayerName(username.trim());
            sceneManager.showLobby();
        } else {
            showError("Connection failed");
        }
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().add("error-text");
    }

    @Override
    public void onDisconnected(String reason) {
        showError(reason);
    }

    @Override
    public void onJoinResult(JoinResultMessage message) {
        // no-op
    }

    @Override
    public void onError(ErrorMessage message) {
        showError(message.getMessage());
    }

    @Override
    public void onConnectionStatus(ConnectionStatus status) {
        statusLabel.setText(status.name());
    }
}
