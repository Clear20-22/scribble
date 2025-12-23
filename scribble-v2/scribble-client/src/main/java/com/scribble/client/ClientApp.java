package com.scribble.client;

import javafx.application.Application;
import javafx.stage.Stage;

public class ClientApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        Client client = new Client();
        SceneManager sceneManager = new SceneManager(primaryStage, client);
        primaryStage.setTitle("Scribble");
        sceneManager.showLogin(null);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
