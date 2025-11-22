package com.scribble;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

/**
 * JavaFX GUI for the client.
 */
public class ClientGUI extends Application {
    private Canvas canvas;
    private GraphicsContext gc;
    private Color currentColor = Color.BLACK;
    private double brushSize = 2.0;
    private double lastX, lastY;
    private Client client;
    private TextArea chatArea;
    private ListView<String> playerList;
    private Label wordLabel;
    private Label timerLabel;
    private Label drawerLabel;
    private Button selectedColorButton;
    private String playerName;
    private String roomName;

    @Override
    public void start(Stage primaryStage) {
        // Initial dialog for name and room
        TextInputDialog nameDialog = new TextInputDialog("Player");
        nameDialog.setTitle("Join Game");
        nameDialog.setHeaderText("Enter your name and room");
        nameDialog.setContentText("Name:");
        nameDialog.showAndWait().ifPresent(name -> playerName = name);

        TextInputDialog roomDialog = new TextInputDialog("Room1");
        roomDialog.setTitle("Join Game");
        roomDialog.setHeaderText("Enter room name");
        roomDialog.setContentText("Room:");
        roomDialog.showAndWait().ifPresent(room -> roomName = room);

        // Connect to server
        client = new Client(this);
        if (!client.connect("localhost", 12345)) {
            // Show error and exit
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to connect to server. Make sure the server is running.");
            alert.showAndWait();
            System.exit(1);
        }
        client.sendMessage(new JoinMessage(playerName, roomName));

        // GUI setup
        canvas = new Canvas(800, 600);
        gc = canvas.getGraphicsContext2D();
        gc.setStroke(currentColor);
        gc.setLineWidth(brushSize);

        canvas.setOnMousePressed(e -> {
            lastX = e.getX();
            lastY = e.getY();
        });

        canvas.setOnMouseDragged(e -> {
            double x = e.getX();
            double y = e.getY();
            gc.strokeLine(lastX, lastY, x, y);
            client.sendMessage(new DrawMessage(lastX, lastY, x, y, toHex(currentColor), brushSize));
            lastX = x;
            lastY = y;
        });

        // Color buttons
        Button blackButton = createColorButton("Black", Color.BLACK);
        Button redButton = createColorButton("Red", Color.RED);
        Button blueButton = createColorButton("Blue", Color.BLUE);
        Button greenButton = createColorButton("Green", Color.GREEN);
        Button yellowButton = createColorButton("Yellow", Color.YELLOW);
        Button purpleButton = createColorButton("Purple", Color.PURPLE);
        selectColorButton(blackButton, Color.BLACK);

        HBox colorBox = new HBox(10, blackButton, redButton, blueButton, greenButton, yellowButton, purpleButton);
        colorBox.setAlignment(Pos.CENTER);
        colorBox.setPadding(new Insets(10));

        // Brush size slider
        Label sizeLabel = new Label("Brush Size:");
        Slider sizeSlider = new Slider(1, 20, brushSize);
        sizeSlider.setMajorTickUnit(5);
        sizeSlider.setMinorTickCount(1);
        sizeSlider.setShowTickLabels(true);
        sizeSlider.setShowTickMarks(true);
        sizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            brushSize = newVal.doubleValue();
            gc.setLineWidth(brushSize);
        });

        HBox sizeBox = new HBox(10, sizeLabel, sizeSlider);
        sizeBox.setAlignment(Pos.CENTER);
        sizeBox.setPadding(new Insets(10));

        // Clear button
        Button clearButton = new Button("Clear Canvas");
        clearButton.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white; -fx-font-size: 14px;");
        clearButton.setOnAction(e -> {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            client.sendMessage(new ClearMessage());
        });

        // Chat
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setPrefHeight(200);

        TextField chatInput = new TextField();
        chatInput.setPromptText("Type your guess or message...");
        chatInput.setOnAction(e -> {
            String text = chatInput.getText();
            if (!text.isEmpty()) {
                client.sendMessage(new ChatMessage(playerName, text));
                chatInput.clear();
            }
        });

        VBox chatBox = new VBox(10, new Label("Chat"), chatArea, chatInput);
        chatBox.setPadding(new Insets(10));

        // Player list
        playerList = new ListView<>();
        VBox playerBox = new VBox(10, new Label("Players"), playerList);
        playerBox.setPadding(new Insets(10));
        playerBox.setPrefWidth(150);

        // Game info
        wordLabel = new Label("Word: ?????");
        timerLabel = new Label("Time: 60");
        drawerLabel = new Label("Drawer: None");
        VBox infoBox = new VBox(10, wordLabel, timerLabel, drawerLabel);
        infoBox.setPadding(new Insets(10));

        // Layout
        HBox controlBox = new HBox(20, colorBox, sizeBox, clearButton);
        controlBox.setAlignment(Pos.CENTER);
        controlBox.setPadding(new Insets(10));

        BorderPane centerPane = new BorderPane();
        centerPane.setCenter(canvas);
        centerPane.setBottom(controlBox);

        BorderPane rightPane = new BorderPane();
        rightPane.setTop(infoBox);
        rightPane.setCenter(chatBox);
        rightPane.setBottom(playerBox);

        BorderPane root = new BorderPane();
        root.setCenter(centerPane);
        root.setRight(rightPane);

        Scene scene = new Scene(root, 1100, 700);

        primaryStage.setTitle("Scribble Game - " + playerName);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private Button createColorButton(String name, Color color) {
        Button button = new Button(name);
        button.setStyle("-fx-background-color: " + toHex(color) + "; -fx-text-fill: " +
                (color.getBrightness() > 0.5 ? "black" : "white") + "; -fx-font-size: 12px; -fx-min-width: 60px;");
        button.setOnAction(e -> selectColorButton(button, color));
        return button;
    }

    private void selectColorButton(Button button, Color color) {
        if (selectedColorButton != null) {
            Color prevColor = getColorFromButton(selectedColorButton);
            selectedColorButton.setStyle("-fx-background-color: " + toHex(prevColor) + "; -fx-text-fill: " +
                    (prevColor.getBrightness() > 0.5 ? "black" : "white")
                    + "; -fx-font-size: 12px; -fx-min-width: 60px;");
        }
        selectedColorButton = button;
        currentColor = color;
        gc.setStroke(currentColor);
        button.setStyle("-fx-background-color: " + toHex(color) + "; -fx-text-fill: " +
                (color.getBrightness() > 0.5 ? "black" : "white")
                + "; -fx-font-size: 12px; -fx-min-width: 60px; -fx-border-color: #333; -fx-border-width: 2px;");
    }

    private Color getColorFromButton(Button button) {
        String text = button.getText();
        switch (text) {
            case "Black":
                return Color.BLACK;
            case "Red":
                return Color.RED;
            case "Blue":
                return Color.BLUE;
            case "Green":
                return Color.GREEN;
            case "Yellow":
                return Color.YELLOW;
            case "Purple":
                return Color.PURPLE;
            default:
                return Color.BLACK;
        }
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    // Methods called from Client
    public void drawLine(double startX, double startY, double endX, double endY, String color, double brushSize) {
        Platform.runLater(() -> {
            gc.setStroke(Color.web(color));
            gc.setLineWidth(brushSize);
            gc.strokeLine(startX, startY, endX, endY);
            gc.setStroke(currentColor);
            gc.setLineWidth(this.brushSize);
        });
    }

    public void addChatMessage(String message) {
        Platform.runLater(() -> chatArea.appendText(message + "\n"));
    }

    public void updateGameState(String currentDrawer, String word, int timeLeft, Map<String, Integer> scores) {
        Platform.runLater(() -> {
            drawerLabel.setText("Drawer: " + currentDrawer);
            wordLabel.setText("Word: " + word);
            timerLabel.setText("Time: " + timeLeft);
            // Update scores in player list or separate
        });
    }

    public void updatePlayerList(List<String> players) {
        Platform.runLater(() -> {
            playerList.getItems().clear();
            playerList.getItems().addAll(players);
        });
    }

    public void clearCanvas() {
        Platform.runLater(() -> gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight()));
    }

    public static void main(String[] args) {
        launch(args);
    }
}