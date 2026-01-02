package com.scribble.server.admin;

import com.scribble.server.Server;
import com.scribble.server.monitor.ClientNetworkStats;
import com.scribble.server.monitor.RoomNetworkStats;
import com.scribble.server.monitor.ServerNetworkStats;
import com.scribble.utils.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Admin Monitoring Control Panel - Central dashboard for server monitoring.
 * Launches alongside the server to provide real-time visibility into all
 * network activity.
 */
public class AdminMonitorApp extends Application {
    private static final Logger logger = Logger.getLogger(AdminMonitorApp.class);

    private static Server server;
    private static int serverPort = 5555;

    private ServerNetworkStats stats;
    private ScheduledExecutorService updateScheduler;

    // UI Components
    private Label serverStatusLabel;
    private Label uptimeLabel;
    private Label connectedClientsLabel;
    private Label totalConnectionsLabel;
    private Label totalDisconnectsLabel;
    private Label messagesSentLabel;
    private Label messagesReceivedLabel;
    private Label bytesSentLabel;
    private Label bytesReceivedLabel;
    private Label throughputInLabel;
    private Label throughputOutLabel;
    private Label msgsPerSecLabel;
    private Label roomCountLabel;
    private Label cpuLoadLabel;
    private Label heapUsedLabel;
    private Label threadCountLabel;
    private Label errorsLabel;

    // Graphs - Overview tab (small)
    private Canvas throughputGraph;
    private Canvas latencyGraph;
    private Canvas connectionsGraph;

    // Graphs - Graphs tab (large)
    private Canvas largeThroughputGraph;
    private Canvas largeMemoryGraph;
    private Canvas largeCpuGraph;
    private Canvas largeMsgsGraph;

    // Tables
    private TableView<ClientInfoRow> clientsTable;
    private TableView<RoomInfoRow> roomsTable;
    private ListView<String> packetLogList;

    public static void launchWithServer(Server serverInstance, int port) {
        server = serverInstance;
        serverPort = port;
        new Thread(() -> Application.launch(AdminMonitorApp.class)).start();
    }

    @Override
    public void start(Stage primaryStage) {
        stats = ServerNetworkStats.getInstance();

        primaryStage.setTitle("Scribble Server - Admin Monitor");
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(800);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

        // Header
        root.setTop(createHeader());

        // Main content with tabs
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #1a1a2e;");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab overviewTab = new Tab("Overview", createOverviewPane());
        Tab clientsTab = new Tab("Clients", createClientsPane());
        Tab roomsTab = new Tab("Rooms", createRoomsPane());
        Tab packetLogTab = new Tab("Packet Log", createPacketLogPane());
        Tab graphsTab = new Tab("Graphs", createGraphsPane());

        tabPane.getTabs().addAll(overviewTab, clientsTab, roomsTab, packetLogTab, graphsTab);
        root.setCenter(tabPane);

        // Footer status bar
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/css/admin-style.css") != null
                ? getClass().getResource("/css/admin-style.css").toExternalForm()
                : "");
        applyDarkTheme(scene);

        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            shutdown();
            Platform.exit();
        });
        primaryStage.show();

        startUpdateLoop();
        logger.info("Admin Monitor UI started");
    }

    private HBox createHeader() {
        HBox header = new HBox(20);
        header.setPadding(new Insets(15, 20, 15, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #16213e;");

        Label title = new Label("🖥 SCRIBBLE SERVER MONITOR");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        serverStatusLabel = new Label("● RUNNING");
        serverStatusLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        serverStatusLabel.setTextFill(Color.LIGHTGREEN);

        Label portLabel = new Label("Port: " + serverPort);
        portLabel.setFont(Font.font("System", 14));
        portLabel.setTextFill(Color.LIGHTGRAY);

        header.getChildren().addAll(title, spacer, serverStatusLabel, portLabel);
        return header;
    }

    private VBox createOverviewPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(20));
        pane.setStyle("-fx-background-color: #1a1a2e;");

        // Stats cards grid
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(15);
        statsGrid.setVgap(15);

        // Row 1 - Connection stats
        statsGrid.add(createStatCard("⏱ Uptime", uptimeLabel = createValueLabel("0s")), 0, 0);
        statsGrid.add(createStatCard("👥 Connected", connectedClientsLabel = createValueLabel("0")), 1, 0);
        statsGrid.add(createStatCard("✅ Total Connections", totalConnectionsLabel = createValueLabel("0")), 2, 0);
        statsGrid.add(createStatCard("❌ Disconnects", totalDisconnectsLabel = createValueLabel("0")), 3, 0);

        // Row 2 - Message stats
        statsGrid.add(createStatCard("📤 Messages Sent", messagesSentLabel = createValueLabel("0")), 0, 1);
        statsGrid.add(createStatCard("📥 Messages Received", messagesReceivedLabel = createValueLabel("0")), 1, 1);
        statsGrid.add(createStatCard("📊 Bytes Sent", bytesSentLabel = createValueLabel("0 B")), 2, 1);
        statsGrid.add(createStatCard("📊 Bytes Received", bytesReceivedLabel = createValueLabel("0 B")), 3, 1);

        // Row 3 - Throughput
        statsGrid.add(createStatCard("⬆ TX Rate", throughputOutLabel = createValueLabel("0 B/s")), 0, 2);
        statsGrid.add(createStatCard("⬇ RX Rate", throughputInLabel = createValueLabel("0 B/s")), 1, 2);
        statsGrid.add(createStatCard("💬 Msgs/sec", msgsPerSecLabel = createValueLabel("0")), 2, 2);
        statsGrid.add(createStatCard("🏠 Rooms", roomCountLabel = createValueLabel("0")), 3, 2);

        // Row 4 - System stats
        statsGrid.add(createStatCard("🔥 CPU Load", cpuLoadLabel = createValueLabel("0%")), 0, 3);
        statsGrid.add(createStatCard("💾 Heap Used", heapUsedLabel = createValueLabel("0 MB")), 1, 3);
        statsGrid.add(createStatCard("🧵 Threads", threadCountLabel = createValueLabel("0")), 2, 3);
        statsGrid.add(createStatCard("⚠ Errors", errorsLabel = createValueLabel("0")), 3, 3);

        pane.getChildren().add(statsGrid);

        // Mini graphs section
        HBox graphsRow = new HBox(15);
        graphsRow.setPadding(new Insets(10, 0, 0, 0));

        throughputGraph = new Canvas(350, 150);
        latencyGraph = new Canvas(350, 150);
        connectionsGraph = new Canvas(350, 150);

        graphsRow.getChildren().addAll(
                createGraphCard("Throughput (B/s)", throughputGraph),
                createGraphCard("Connections", connectionsGraph),
                createGraphCard("CPU & Memory", latencyGraph));

        pane.getChildren().add(graphsRow);

        return pane;
    }

    private VBox createStatCard(String title, Label valueLabel) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(15));
        card.setMinWidth(200);
        card.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", 12));
        titleLabel.setTextFill(Color.LIGHTGRAY);

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private Label createValueLabel(String initial) {
        Label label = new Label(initial);
        label.setFont(Font.font("System", FontWeight.BOLD, 20));
        label.setTextFill(Color.WHITE);
        return label;
    }

    private VBox createGraphCard(String title, Canvas canvas) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.LIGHTGRAY);

        card.getChildren().addAll(titleLabel, canvas);
        return card;
    }

    @SuppressWarnings("unchecked")
    private VBox createClientsPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(20));
        pane.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Connected Clients");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        clientsTable = new TableView<>();
        clientsTable.setStyle("-fx-background-color: #16213e;");
        clientsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ClientInfoRow, String> idCol = new TableColumn<>("Client ID");
        idCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().clientId));
        idCol.setCellFactory(col -> createCenteredCell());

        TableColumn<ClientInfoRow, String> ipCol = new TableColumn<>("IP Address");
        ipCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().ipAddress));
        ipCol.setCellFactory(col -> createCenteredCell());

        TableColumn<ClientInfoRow, String> playerCol = new TableColumn<>("Player");
        playerCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().playerName));
        playerCol.setCellFactory(col -> createCenteredCell());

        TableColumn<ClientInfoRow, String> roomCol = new TableColumn<>("Room");
        roomCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().roomName));
        roomCol.setCellFactory(col -> createCenteredCell());

        TableColumn<ClientInfoRow, String> sentCol = new TableColumn<>("Sent");
        sentCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().messagesSent));
        sentCol.setCellFactory(col -> createCenteredCell());

        TableColumn<ClientInfoRow, String> recvCol = new TableColumn<>("Received");
        recvCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().messagesReceived));
        recvCol.setCellFactory(col -> createCenteredCell());

        TableColumn<ClientInfoRow, String> rttCol = new TableColumn<>("RTT");
        rttCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().rtt));
        rttCol.setCellFactory(col -> createCenteredCell());

        TableColumn<ClientInfoRow, String> durationCol = new TableColumn<>("Duration");
        durationCol
                .setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().duration));
        durationCol.setCellFactory(col -> createCenteredCell());

        clientsTable.getColumns().addAll(idCol, ipCol, playerCol, roomCol, sentCol, recvCol, rttCol, durationCol);

        VBox.setVgrow(clientsTable, Priority.ALWAYS);
        pane.getChildren().addAll(title, clientsTable);
        return pane;
    }

    @SuppressWarnings("unchecked")
    private VBox createRoomsPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(20));
        pane.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Active Rooms");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        roomsTable = new TableView<>();
        roomsTable.setStyle("-fx-background-color: #16213e;");
        roomsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<RoomInfoRow, String> nameCol = new TableColumn<>("Room Name");
        nameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().roomName));
        nameCol.setCellFactory(col -> createCenteredCell());

        TableColumn<RoomInfoRow, String> playersCol = new TableColumn<>("Players");
        playersCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().playerCount));
        playersCol.setCellFactory(col -> createCenteredCell());

        TableColumn<RoomInfoRow, String> roundsCol = new TableColumn<>("Rounds");
        roundsCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().roundsPlayed));
        roundsCol.setCellFactory(col -> createCenteredCell());

        TableColumn<RoomInfoRow, String> drawsCol = new TableColumn<>("Draw Events");
        drawsCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().drawEvents));
        drawsCol.setCellFactory(col -> createCenteredCell());

        TableColumn<RoomInfoRow, String> guessesCol = new TableColumn<>("Guesses");
        guessesCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().guesses));
        guessesCol.setCellFactory(col -> createCenteredCell());

        TableColumn<RoomInfoRow, String> broadcastCol = new TableColumn<>("Broadcasts");
        broadcastCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().broadcasts));
        broadcastCol.setCellFactory(col -> createCenteredCell());

        TableColumn<RoomInfoRow, String> uptimeCol = new TableColumn<>("Uptime");
        uptimeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().uptime));
        uptimeCol.setCellFactory(col -> createCenteredCell());

        roomsTable.getColumns().addAll(nameCol, playersCol, roundsCol, drawsCol, guessesCol, broadcastCol, uptimeCol);

        VBox.setVgrow(roomsTable, Priority.ALWAYS);
        pane.getChildren().addAll(title, roomsTable);
        return pane;
    }

    private VBox createPacketLogPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(20));
        pane.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Recent Packets");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        packetLogList = new ListView<>();
        packetLogList.setStyle("-fx-background-color: #16213e; -fx-control-inner-background: #16213e;");

        VBox.setVgrow(packetLogList, Priority.ALWAYS);
        pane.getChildren().addAll(title, packetLogList);
        return pane;
    }

    private VBox createGraphsPane() {
        VBox pane = new VBox(20);
        pane.setPadding(new Insets(20));
        pane.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Live Graphs");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        // Create large canvases for live graphs
        largeThroughputGraph = new Canvas(1100, 220);
        largeMemoryGraph = new Canvas(540, 180);
        largeCpuGraph = new Canvas(540, 180);
        largeMsgsGraph = new Canvas(1100, 180);

        // Row with CPU and Memory side by side
        HBox systemRow = new HBox(15);
        systemRow.getChildren().addAll(
                createGraphCard("CPU Load (%)", largeCpuGraph),
                createGraphCard("JVM Heap Memory (MB)", largeMemoryGraph));

        pane.getChildren().addAll(title,
                createGraphCard("Network Throughput (bytes/sec) - Cyan=IN, Magenta=OUT", largeThroughputGraph),
                createGraphCard("Messages per Second", largeMsgsGraph),
                systemRow);
        return pane;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(20);
        statusBar.setPadding(new Insets(8, 15, 8, 15));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #0f0f23;");

        Label versionLabel = new Label("Scribble Server v2.0");
        versionLabel.setTextFill(Color.GRAY);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label refreshLabel = new Label("Refreshing every 1s");
        refreshLabel.setTextFill(Color.GRAY);

        statusBar.getChildren().addAll(versionLabel, spacer, refreshLabel);
        return statusBar;
    }

    private void startUpdateLoop() {
        updateScheduler = Executors.newSingleThreadScheduledExecutor();
        updateScheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(this::updateUI);
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void updateUI() {
        if (stats == null)
            return;

        ServerNetworkStats.ServerStatsSnapshot snapshot = stats.toSnapshot();

        // Update overview stats
        uptimeLabel.setText(formatDuration(snapshot.serverUptime));
        connectedClientsLabel.setText(String.valueOf(snapshot.currentConnectedClients));
        totalConnectionsLabel.setText(String.valueOf(snapshot.totalConnectionsAccepted));
        totalDisconnectsLabel.setText(String.valueOf(snapshot.totalDisconnects));
        messagesSentLabel.setText(formatNumber(snapshot.totalMessagesSent));
        messagesReceivedLabel.setText(formatNumber(snapshot.totalMessagesReceived));
        bytesSentLabel.setText(formatBytes(snapshot.totalBytesSent));
        bytesReceivedLabel.setText(formatBytes(snapshot.totalBytesReceived));
        throughputOutLabel.setText(formatBytes((long) snapshot.bytesPerSecOut) + "/s");
        throughputInLabel.setText(formatBytes((long) snapshot.bytesPerSecIn) + "/s");
        msgsPerSecLabel.setText(String.format("%.1f", snapshot.msgsPerSec));
        roomCountLabel.setText(String.valueOf(snapshot.roomCount));

        // System stats
        cpuLoadLabel.setText(String.format("%.1f%%",
                snapshot.gcStats.totalTimeMs > 0 ? (snapshot.gcStats.totalTimeMs / (double) snapshot.serverUptime * 100)
                        : 0));
        heapUsedLabel.setText(String.format("%.0f MB", snapshot.heapUsedMb));
        threadCountLabel.setText(String.valueOf(snapshot.threadCount));
        errorsLabel.setText(String.valueOf(snapshot.parseErrors + snapshot.invalidPayloads));

        // Update graphs - Overview tab
        drawThroughputGraph(snapshot);
        drawConnectionsGraph(snapshot);
        drawSystemGraph(snapshot);

        // Update graphs - Graphs tab (large versions)
        drawLargeThroughputGraph(snapshot);
        drawLargeMemoryGraph(snapshot);
        drawLargeCpuGraph(snapshot);
        drawLargeMsgsGraph(snapshot);

        // Update clients table
        updateClientsTable(snapshot.clientStats);

        // Update rooms table
        updateRoomsTable(snapshot.roomStats);

        // Update packet log
        updatePacketLog();
    }

    private void drawThroughputGraph(ServerNetworkStats.ServerStatsSnapshot snapshot) {
        GraphicsContext gc = throughputGraph.getGraphicsContext2D();
        double w = throughputGraph.getWidth();
        double h = throughputGraph.getHeight();

        gc.setFill(Color.rgb(22, 33, 62));
        gc.fillRect(0, 0, w, h);

        double[] inData = snapshot.bytesPerSecInHistory;
        double[] outData = snapshot.bytesPerSecOutHistory;

        if (inData.length < 2)
            return;

        double maxVal = 1;
        for (double v : inData)
            maxVal = Math.max(maxVal, v);
        for (double v : outData)
            maxVal = Math.max(maxVal, v);

        // Draw IN line (cyan)
        gc.setStroke(Color.CYAN);
        gc.setLineWidth(2);
        gc.beginPath();
        for (int i = 0; i < inData.length; i++) {
            double x = (i / (double) (inData.length - 1)) * w;
            double y = h - (inData[i] / maxVal) * (h - 10);
            if (i == 0)
                gc.moveTo(x, y);
            else
                gc.lineTo(x, y);
        }
        gc.stroke();

        // Draw OUT line (magenta)
        gc.setStroke(Color.MAGENTA);
        gc.beginPath();
        for (int i = 0; i < outData.length; i++) {
            double x = (i / (double) (outData.length - 1)) * w;
            double y = h - (outData[i] / maxVal) * (h - 10);
            if (i == 0)
                gc.moveTo(x, y);
            else
                gc.lineTo(x, y);
        }
        gc.stroke();
    }

    private void drawConnectionsGraph(ServerNetworkStats.ServerStatsSnapshot snapshot) {
        GraphicsContext gc = connectionsGraph.getGraphicsContext2D();
        double w = connectionsGraph.getWidth();
        double h = connectionsGraph.getHeight();

        gc.setFill(Color.rgb(22, 33, 62));
        gc.fillRect(0, 0, w, h);

        double[] data = snapshot.connectedClientsHistory;
        if (data.length < 2)
            return;

        double maxVal = 1;
        for (double v : data)
            maxVal = Math.max(maxVal, v);

        gc.setStroke(Color.LIGHTGREEN);
        gc.setLineWidth(2);
        gc.beginPath();
        for (int i = 0; i < data.length; i++) {
            double x = (i / (double) (data.length - 1)) * w;
            double y = h - (data[i] / maxVal) * (h - 10);
            if (i == 0)
                gc.moveTo(x, y);
            else
                gc.lineTo(x, y);
        }
        gc.stroke();
    }

    private void drawSystemGraph(ServerNetworkStats.ServerStatsSnapshot snapshot) {
        GraphicsContext gc = latencyGraph.getGraphicsContext2D();
        double w = latencyGraph.getWidth();
        double h = latencyGraph.getHeight();

        gc.setFill(Color.rgb(22, 33, 62));
        gc.fillRect(0, 0, w, h);

        double[] heapData = snapshot.heapUsedMbHistory;
        if (heapData.length < 2)
            return;

        double maxVal = snapshot.heapMaxMb > 0 ? snapshot.heapMaxMb : 1;

        gc.setStroke(Color.ORANGE);
        gc.setLineWidth(2);
        gc.beginPath();
        for (int i = 0; i < heapData.length; i++) {
            double x = (i / (double) (heapData.length - 1)) * w;
            double y = h - (heapData[i] / maxVal) * (h - 10);
            if (i == 0)
                gc.moveTo(x, y);
            else
                gc.lineTo(x, y);
        }
        gc.stroke();
    }

    // ===== LARGE GRAPHS FOR GRAPHS TAB =====

    private void drawLargeThroughputGraph(ServerNetworkStats.ServerStatsSnapshot snapshot) {
        if (largeThroughputGraph == null)
            return;
        GraphicsContext gc = largeThroughputGraph.getGraphicsContext2D();
        double w = largeThroughputGraph.getWidth();
        double h = largeThroughputGraph.getHeight();

        gc.setFill(Color.rgb(22, 33, 62));
        gc.fillRect(0, 0, w, h);

        double[] inData = snapshot.bytesPerSecInHistory;
        double[] outData = snapshot.bytesPerSecOutHistory;

        if (inData == null || inData.length < 2) {
            gc.setFill(Color.GRAY);
            gc.fillText("Waiting for data...", w / 2 - 50, h / 2);
            return;
        }

        // Find max for scaling
        double maxVal = 1;
        for (double v : inData)
            maxVal = Math.max(maxVal, v);
        for (double v : outData)
            maxVal = Math.max(maxVal, v);

        // Draw grid lines
        gc.setStroke(Color.rgb(50, 60, 80));
        gc.setLineWidth(0.5);
        for (int i = 1; i < 5; i++) {
            double y = h * i / 5;
            gc.strokeLine(0, y, w, y);
        }

        // Draw IN line (cyan)
        gc.setStroke(Color.CYAN);
        gc.setLineWidth(2);
        gc.beginPath();
        for (int i = 0; i < inData.length; i++) {
            double x = (i / (double) (inData.length - 1)) * w;
            double y = h - (inData[i] / maxVal) * (h - 20) - 10;
            if (i == 0)
                gc.moveTo(x, y);
            else
                gc.lineTo(x, y);
        }
        gc.stroke();

        // Draw OUT line (magenta)
        gc.setStroke(Color.MAGENTA);
        gc.beginPath();
        for (int i = 0; i < outData.length; i++) {
            double x = (i / (double) (outData.length - 1)) * w;
            double y = h - (outData[i] / maxVal) * (h - 20) - 10;
            if (i == 0)
                gc.moveTo(x, y);
            else
                gc.lineTo(x, y);
        }
        gc.stroke();

        // Draw legend
        gc.setFill(Color.CYAN);
        gc.fillRect(w - 150, 10, 12, 12);
        gc.setFill(Color.WHITE);
        gc.fillText("IN", w - 132, 20);
        gc.setFill(Color.MAGENTA);
        gc.fillRect(w - 100, 10, 12, 12);
        gc.setFill(Color.WHITE);
        gc.fillText("OUT", w - 82, 20);

        // Draw scale
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText(formatBytes((long) maxVal) + "/s", 5, 15);
        gc.fillText("0", 5, h - 5);
    }

    private void drawLargeMemoryGraph(ServerNetworkStats.ServerStatsSnapshot snapshot) {
        if (largeMemoryGraph == null)
            return;
        GraphicsContext gc = largeMemoryGraph.getGraphicsContext2D();
        double w = largeMemoryGraph.getWidth();
        double h = largeMemoryGraph.getHeight();

        gc.setFill(Color.rgb(22, 33, 62));
        gc.fillRect(0, 0, w, h);

        double[] heapData = snapshot.heapUsedMbHistory;
        if (heapData == null || heapData.length < 2) {
            gc.setFill(Color.GRAY);
            gc.fillText("Waiting for data...", w / 2 - 50, h / 2);
            return;
        }

        double maxVal = snapshot.heapMaxMb > 0 ? snapshot.heapMaxMb : 512;

        // Draw max line
        gc.setStroke(Color.rgb(100, 60, 60));
        gc.setLineWidth(1);
        gc.setLineDashes(5);
        gc.strokeLine(0, 10, w, 10);
        gc.setLineDashes(null);

        // Draw grid
        gc.setStroke(Color.rgb(50, 60, 80));
        gc.setLineWidth(0.5);
        for (int i = 1; i < 4; i++) {
            double y = h * i / 4;
            gc.strokeLine(0, y, w, y);
        }

        // Draw heap line
        gc.setStroke(Color.ORANGE);
        gc.setLineWidth(2);
        gc.beginPath();
        for (int i = 0; i < heapData.length; i++) {
            double x = (i / (double) (heapData.length - 1)) * w;
            double y = h - (heapData[i] / maxVal) * (h - 20) - 10;
            if (i == 0)
                gc.moveTo(x, y);
            else
                gc.lineTo(x, y);
        }
        gc.stroke();

        // Scale labels
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText(String.format("%.0f MB (max)", maxVal), 5, 15);
        gc.fillText("0 MB", 5, h - 5);
        gc.setFill(Color.ORANGE);
        gc.fillText(String.format("Current: %.0f MB", heapData[heapData.length - 1]), w - 130, 15);
    }

    private void drawLargeCpuGraph(ServerNetworkStats.ServerStatsSnapshot snapshot) {
        if (largeCpuGraph == null)
            return;
        GraphicsContext gc = largeCpuGraph.getGraphicsContext2D();
        double w = largeCpuGraph.getWidth();
        double h = largeCpuGraph.getHeight();

        gc.setFill(Color.rgb(22, 33, 62));
        gc.fillRect(0, 0, w, h);

        double[] cpuData = snapshot.cpuLoadHistory;
        if (cpuData == null || cpuData.length < 2) {
            gc.setFill(Color.GRAY);
            gc.fillText("Waiting for data...", w / 2 - 50, h / 2);
            return;
        }

        double maxVal = 100; // CPU percentage

        // Draw grid
        gc.setStroke(Color.rgb(50, 60, 80));
        gc.setLineWidth(0.5);
        for (int i = 1; i < 4; i++) {
            double y = h * i / 4;
            gc.strokeLine(0, y, w, y);
        }

        // Draw CPU line
        gc.setStroke(Color.LIGHTGREEN);
        gc.setLineWidth(2);
        gc.beginPath();
        for (int i = 0; i < cpuData.length; i++) {
            double x = (i / (double) (cpuData.length - 1)) * w;
            double y = h - (cpuData[i] / maxVal) * (h - 20) - 10;
            if (i == 0)
                gc.moveTo(x, y);
            else
                gc.lineTo(x, y);
        }
        gc.stroke();

        // Scale labels
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText("100%", 5, 15);
        gc.fillText("0%", 5, h - 5);
        gc.setFill(Color.LIGHTGREEN);
        gc.fillText(String.format("Current: %.1f%%", cpuData[cpuData.length - 1]), w - 120, 15);
    }

    private void drawLargeMsgsGraph(ServerNetworkStats.ServerStatsSnapshot snapshot) {
        if (largeMsgsGraph == null)
            return;
        GraphicsContext gc = largeMsgsGraph.getGraphicsContext2D();
        double w = largeMsgsGraph.getWidth();
        double h = largeMsgsGraph.getHeight();

        gc.setFill(Color.rgb(22, 33, 62));
        gc.fillRect(0, 0, w, h);

        double[] msgsData = snapshot.msgsPerSecHistory;
        if (msgsData == null || msgsData.length < 2) {
            gc.setFill(Color.GRAY);
            gc.fillText("Waiting for data...", w / 2 - 50, h / 2);
            return;
        }

        double maxVal = 1;
        for (double v : msgsData)
            maxVal = Math.max(maxVal, v);

        // Draw grid
        gc.setStroke(Color.rgb(50, 60, 80));
        gc.setLineWidth(0.5);
        for (int i = 1; i < 4; i++) {
            double y = h * i / 4;
            gc.strokeLine(0, y, w, y);
        }

        // Draw msgs line
        gc.setStroke(Color.YELLOW);
        gc.setLineWidth(2);
        gc.beginPath();
        for (int i = 0; i < msgsData.length; i++) {
            double x = (i / (double) (msgsData.length - 1)) * w;
            double y = h - (msgsData[i] / maxVal) * (h - 20) - 10;
            if (i == 0)
                gc.moveTo(x, y);
            else
                gc.lineTo(x, y);
        }
        gc.stroke();

        // Scale labels
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText(String.format("%.0f msg/s", maxVal), 5, 15);
        gc.fillText("0", 5, h - 5);
        gc.setFill(Color.YELLOW);
        gc.fillText(String.format("Current: %.1f msg/s", msgsData[msgsData.length - 1]), w - 150, 15);
    }

    private void drawPlaceholderGraph(GraphicsContext gc, String label, Color color) {
        double w = gc.getCanvas().getWidth();
        double h = gc.getCanvas().getHeight();
        gc.setFill(Color.rgb(22, 33, 62));
        gc.fillRect(0, 0, w, h);
        gc.setFill(color.deriveColor(0, 1, 1, 0.3));
        gc.fillText(label, 10, 20);
    }

    private void updateClientsTable(Map<String, ClientNetworkStats.ClientStatsSnapshot> clientStats) {
        clientsTable.getItems().clear();
        for (var entry : clientStats.entrySet()) {
            var cs = entry.getValue();
            if (cs.connected) {
                String shortId = cs.clientId.length() > 8 ? cs.clientId.substring(0, 8) + "..." : cs.clientId;
                clientsTable.getItems().add(new ClientInfoRow(
                        shortId,
                        cs.remoteAddress + ":" + cs.remotePort,
                        cs.playerName != null ? cs.playerName : "-",
                        cs.currentRoomId != null ? cs.currentRoomId : "-",
                        formatNumber(cs.messagesSent),
                        formatNumber(cs.messagesReceived),
                        cs.lastRtt + " ms",
                        formatDuration(cs.sessionDurationMs)));
            }
        }
    }

    private void updateRoomsTable(Map<String, RoomNetworkStats.RoomStatsSnapshot> roomStats) {
        roomsTable.getItems().clear();
        for (var entry : roomStats.entrySet()) {
            var rs = entry.getValue();
            roomsTable.getItems().add(new RoomInfoRow(
                    rs.roomId,
                    String.valueOf(rs.currentPlayerCount),
                    String.valueOf(rs.roundsPlayed),
                    formatNumber(rs.drawMessages),
                    rs.correctGuesses + "/" + rs.guessAttempts,
                    formatNumber(rs.messagesBroadcast),
                    formatDuration(rs.uptime)));
        }
    }

    private void updatePacketLog() {
        var packets = stats.getGlobalPacketLog();
        packetLogList.getItems().clear();
        int count = 0;
        for (var packet : packets) {
            if (count++ >= 100)
                break;

            // Get client info
            String clientIdShort = "?";
            String playerName = "?";
            if (packet.getClientId() != null) {
                String id = packet.getClientId();
                clientIdShort = id.length() > 8 ? id.substring(0, 8) : id;

                // Get player name from client stats
                var clientStats = stats.getClientStats(packet.getClientId());
                if (clientStats != null && clientStats.getPlayerName() != null) {
                    playerName = clientStats.getPlayerName();
                }
            }

            // Get room info
            String roomInfo = packet.getRoomId() != null ? packet.getRoomId() : "null";

            // Direction indicator and label
            boolean isTx = packet.getDirection() == com.scribble.server.monitor.PacketLogEntry.Direction.TX;
            String dirSymbol = isTx ? "→ OUT" : "← IN ";

            // Format: [TIME] DIRECTION MessageType (size) | Player: name | Client: id |
            // Room: room
            String entry = String.format("[%s] %s  %-25s (%4d B) │ Player: %-10s │ Client: %s │ Room: %s",
                    new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(packet.getTimestamp()),
                    dirSymbol,
                    packet.getMessageType(),
                    packet.getSizeBytes(),
                    playerName,
                    clientIdShort,
                    roomInfo);
            packetLogList.getItems().add(entry);
        }
    }

    private <T> TableCell<T, String> createCenteredCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER);
                }
            }
        };
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60)
            return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60)
            return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatNumber(long n) {
        if (n < 1000)
            return String.valueOf(n);
        if (n < 1000000)
            return String.format("%.1fK", n / 1000.0);
        return String.format("%.1fM", n / 1000000.0);
    }

    private void applyDarkTheme(Scene scene) {
        scene.getRoot().setStyle(
                "-fx-base: #1a1a2e;" +
                        "-fx-background: #1a1a2e;" +
                        "-fx-control-inner-background: #16213e;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-family: 'System';");
    }

    private void shutdown() {
        if (updateScheduler != null) {
            updateScheduler.shutdownNow();
        }
        logger.info("Admin Monitor UI shutdown");
    }

    // Data classes for tables
    public static class ClientInfoRow {
        public final String clientId, ipAddress, playerName, roomName;
        public final String messagesSent, messagesReceived, rtt, duration;

        public ClientInfoRow(String clientId, String ipAddress, String playerName, String roomName,
                String messagesSent, String messagesReceived, String rtt, String duration) {
            this.clientId = clientId;
            this.ipAddress = ipAddress;
            this.playerName = playerName;
            this.roomName = roomName;
            this.messagesSent = messagesSent;
            this.messagesReceived = messagesReceived;
            this.rtt = rtt;
            this.duration = duration;
        }
    }

    public static class RoomInfoRow {
        public final String roomName, playerCount, roundsPlayed, drawEvents, guesses, broadcasts, uptime;

        public RoomInfoRow(String roomName, String playerCount, String roundsPlayed,
                String drawEvents, String guesses, String broadcasts, String uptime) {
            this.roomName = roomName;
            this.playerCount = playerCount;
            this.roundsPlayed = roundsPlayed;
            this.drawEvents = drawEvents;
            this.guesses = guesses;
            this.broadcasts = broadcasts;
            this.uptime = uptime;
        }
    }
}
