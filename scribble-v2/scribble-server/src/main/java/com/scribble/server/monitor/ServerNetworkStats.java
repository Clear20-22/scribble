package com.scribble.server.monitor;

import com.scribble.utils.Logger;

import java.io.Serial;
import java.io.Serializable;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Global server network statistics tracker.
 * Singleton that aggregates all client, room, and system metrics.
 * Thread-safe using atomic operations and lock-free structures.
 */
public final class ServerNetworkStats {
    private static final Logger logger = Logger.getLogger(ServerNetworkStats.class);

    private static volatile ServerNetworkStats instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Configuration
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private volatile int updateIntervalMs = 1000;

    private static final int DEFAULT_RING_SIZE = getIntProperty("scribble.monitor.ringSize", 300);
    private static final int MAX_GLOBAL_PACKET_LOG = getIntProperty("scribble.monitor.logSize", 200);

    // Server info
    private final long serverStartTime;
    private final int serverPort;

    // Connection counters (lock-free)
    private final LongAdder totalConnectionsAccepted = new LongAdder();
    private final LongAdder totalDisconnects = new LongAdder();
    private final LongAdder totalReconnectAttempts = new LongAdder();
    private volatile int currentConnectedClients;

    // Message counters (global)
    private final LongAdder totalMessagesSent = new LongAdder();
    private final LongAdder totalMessagesReceived = new LongAdder();
    private final LongAdder totalBytesSent = new LongAdder();
    private final LongAdder totalBytesReceived = new LongAdder();

    // Per-message-type counters
    private final ConcurrentHashMap<String, LongAdder> messageTypeCountsTx = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> messageTypeCountsRx = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> messageTypeBytesRx = new ConcurrentHashMap<>();

    // Error counters
    private final LongAdder parseErrors = new LongAdder();
    private final LongAdder invalidPayloads = new LongAdder();
    private final LongAdder droppedMessages = new LongAdder();

    // Message size tracking
    private final LongAdder messageSizeSum = new LongAdder();
    private final LongAdder messageSizeCount = new LongAdder();
    private volatile int minMessageSize = Integer.MAX_VALUE;
    private volatile int maxMessageSize = 0;

    // Per-client stats
    private final ConcurrentHashMap<String, ClientNetworkStats> clientStats = new ConcurrentHashMap<>();

    // Per-room stats
    private final ConcurrentHashMap<String, RoomNetworkStats> roomStats = new ConcurrentHashMap<>();

    // Global packet log (last N entries)
    private final ConcurrentLinkedDeque<PacketLogEntry> globalPacketLog = new ConcurrentLinkedDeque<>();
    // Time-series data for graphs
    private final TimeSeriesRingBuffer bytesPerSecIn = new TimeSeriesRingBuffer(DEFAULT_RING_SIZE);
    private final TimeSeriesRingBuffer bytesPerSecOut = new TimeSeriesRingBuffer(DEFAULT_RING_SIZE);
    private final TimeSeriesRingBuffer msgsPerSec = new TimeSeriesRingBuffer(DEFAULT_RING_SIZE);
    private final TimeSeriesRingBuffer connectedClientsHistory = new TimeSeriesRingBuffer(DEFAULT_RING_SIZE);
    private final TimeSeriesRingBuffer cpuLoadHistory = new TimeSeriesRingBuffer(DEFAULT_RING_SIZE);
    private final TimeSeriesRingBuffer heapUsedHistory = new TimeSeriesRingBuffer(DEFAULT_RING_SIZE);
    private final TimeSeriesRingBuffer threadCountHistory = new TimeSeriesRingBuffer(DEFAULT_RING_SIZE);

    // Throughput calculation
    private volatile long lastThroughputCalcTime;
    private volatile long lastBytesSentSnapshot;
    private volatile long lastBytesReceivedSnapshot;
    private volatile long lastMsgsSentSnapshot;
    private volatile long lastMsgsReceivedSnapshot;
    private volatile double currentBytesPerSecIn;
    private volatile double currentBytesPerSecOut;
    private volatile double currentMsgsPerSec;

    // JVM metrics
    private final MemoryMXBean memoryBean;
    private final OperatingSystemMXBean osBean;
    private final ThreadMXBean threadBean;
    private final List<GarbageCollectorMXBean> gcBeans;

    // Background update scheduler
    private ScheduledExecutorService updateScheduler;

    // Admin subscribers
    private final ConcurrentHashMap<String, AdminSubscriber> adminSubscribers = new ConcurrentHashMap<>();

    private ServerNetworkStats(int serverPort) {
        this.serverStartTime = System.currentTimeMillis();
        this.serverPort = serverPort;
        this.lastThroughputCalcTime = serverStartTime;
        this.updateIntervalMs = getIntProperty("scribble.monitor.updateIntervalMs", 1000);

        // Initialize JVM metrics beans
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        startBackgroundUpdates();
        logger.info("ServerNetworkStats initialized on port " + serverPort);
    }

    private static int getIntProperty(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static ServerNetworkStats getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new ServerNetworkStats(5555);
                }
            }
        }
        return instance;
    }

    public static ServerNetworkStats initialize(int port) {
        synchronized (INSTANCE_LOCK) {
            if (instance == null) {
                instance = new ServerNetworkStats(port);
            }
            return instance;
        }
    }

    // --- Configuration ---

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setUpdateIntervalMs(int intervalMs) {
        this.updateIntervalMs = Math.max(100, intervalMs);
        restartBackgroundUpdates();
    }

    // --- Recording methods ---

    public void recordConnectionAccepted(String clientId, ClientNetworkStats stats) {
        if (!enabled.get())
            return;
        totalConnectionsAccepted.increment();
        clientStats.put(clientId, stats);
        updateCurrentClientCount();
    }

    public void recordDisconnect(String clientId) {
        if (!enabled.get())
            return;
        totalDisconnects.increment();
        ClientNetworkStats stats = clientStats.get(clientId);
        if (stats != null) {
            stats.setConnected(false);
        }
        // Keep stats for a while for historical viewing, then clean up
        // In production, you might want to move to a "recent disconnects" list
        updateCurrentClientCount();
    }

    public void removeClientStats(String clientId) {
        clientStats.remove(clientId);
        updateCurrentClientCount();
    }

    public void recordReconnectAttempt() {
        if (!enabled.get())
            return;
        totalReconnectAttempts.increment();
    }

    public void recordMessageSent(String clientId, String messageType, int bytes) {
        recordMessageSent(clientId, messageType, bytes, null);
    }

    public void recordMessageSent(String clientId, String messageType, int bytes, String roomId) {
        if (!enabled.get())
            return;
        totalMessagesSent.increment();
        totalBytesSent.add(bytes);
        messageTypeCountsTx.computeIfAbsent(messageType, k -> new LongAdder()).increment();
        recordMessageSize(bytes);

        ClientNetworkStats client = clientStats.get(clientId);
        if (client != null) {
            client.recordMessageSent(messageType, bytes);
        }

        String resolvedRoomId = roomId != null ? roomId : (client != null ? client.getCurrentRoomId() : null);
        logPacketGlobal(PacketLogEntry.Direction.TX, messageType, bytes, clientId, resolvedRoomId, null);
    }

    public void recordMessageReceived(String clientId, String messageType, int bytes, String roomId) {
        if (!enabled.get())
            return;
        totalMessagesReceived.increment();
        totalBytesReceived.add(bytes);
        messageTypeCountsRx.computeIfAbsent(messageType, k -> new LongAdder()).increment();
        messageTypeBytesRx.computeIfAbsent(messageType, k -> new LongAdder()).add(bytes);
        recordMessageSize(bytes);

        ClientNetworkStats client = clientStats.get(clientId);
        if (client != null) {
            client.recordMessageReceived(messageType, bytes);
        }

        if (roomId != null) {
            RoomNetworkStats room = roomStats.get(roomId);
            if (room != null) {
                room.recordMessageReceived(messageType, bytes);
            }
        }

        logPacketGlobal(PacketLogEntry.Direction.RX, messageType, bytes, clientId, roomId, null);
    }

    public void recordRoomBroadcast(String roomId, int recipientCount, int bytesPerRecipient) {
        if (!enabled.get())
            return;
        RoomNetworkStats room = roomStats.get(roomId);
        if (room != null) {
            room.recordBroadcast(recipientCount, recipientCount * bytesPerRecipient);
        }
    }

    public void recordBroadcastPacket(String roomId, String messageType, int recipientCount, int totalBytes) {
        if (!enabled.get())
            return;
        logPacketGlobal(PacketLogEntry.Direction.TX, messageType, totalBytes, null, roomId, recipientCount);
    }

    public void recordParseError(String clientId) {
        if (!enabled.get())
            return;
        parseErrors.increment();
        ClientNetworkStats client = clientStats.get(clientId);
        if (client != null) {
            client.recordParseError();
        }
    }

    public void recordInvalidPayload() {
        if (!enabled.get())
            return;
        invalidPayloads.increment();
    }

    public void recordDroppedMessage(String clientId) {
        if (!enabled.get())
            return;
        droppedMessages.increment();
        ClientNetworkStats client = clientStats.get(clientId);
        if (client != null) {
            client.recordDroppedMessage();
        }
    }

    public void recordPingSent(String clientId) {
        if (!enabled.get())
            return;
        ClientNetworkStats client = clientStats.get(clientId);
        if (client != null) {
            client.recordPingSent();
        }
    }

    public void recordPongReceived(String clientId) {
        if (!enabled.get())
            return;
        ClientNetworkStats client = clientStats.get(clientId);
        if (client != null) {
            client.recordPongReceived();
        }
    }

    /**
     * Convenience method: Record a sent message without client ID.
     * Used for global tracking from ClientHandler.
     */
    public void recordMessageSent(String messageType, int bytes) {
        if (!enabled.get())
            return;
        totalMessagesSent.increment();
        totalBytesSent.add(bytes);
        messageTypeCountsTx.computeIfAbsent(messageType, k -> new LongAdder()).increment();
        recordMessageSize(bytes);
    }

    /**
     * Convenience method: Record a received message without client ID.
     * Used for global tracking from ClientHandler.
     */
    public void recordMessageReceived(String messageType, int bytes) {
        if (!enabled.get())
            return;
        totalMessagesReceived.increment();
        totalBytesReceived.add(bytes);
        messageTypeCountsRx.computeIfAbsent(messageType, k -> new LongAdder()).increment();
        messageTypeBytesRx.computeIfAbsent(messageType, k -> new LongAdder()).add(bytes);
        recordMessageSize(bytes);
    }

    private void recordMessageSize(int bytes) {
        messageSizeSum.add(bytes);
        messageSizeCount.increment();
        if (bytes < minMessageSize)
            minMessageSize = bytes;
        if (bytes > maxMessageSize)
            maxMessageSize = bytes;
    }

    private void logPacketGlobal(PacketLogEntry.Direction direction, String messageType,
            int bytes, String clientId, String roomId, Integer recipientCount) {
        String src = resolveSrc(direction, clientId);
        String dst = resolveDst(direction, clientId, roomId, recipientCount);
        PacketLogEntry entry = new PacketLogEntry(
                System.currentTimeMillis(), direction, messageType, bytes, clientId, roomId, src, dst, recipientCount);
        globalPacketLog.addFirst(entry);
        while (globalPacketLog.size() > MAX_GLOBAL_PACKET_LOG) {
            globalPacketLog.pollLast();
        }
    }

    private String resolveSrc(PacketLogEntry.Direction direction, String clientId) {
        if (direction == PacketLogEntry.Direction.RX) {
            return formatClientEndpoint(clientId);
        }
        return "SERVER";
    }

    private String resolveDst(PacketLogEntry.Direction direction, String clientId, String roomId,
            Integer recipientCount) {
        if (direction == PacketLogEntry.Direction.RX) {
            return "SERVER";
        }
        if (roomId != null && recipientCount != null && recipientCount > 0) {
            return String.format("room:%s (%d recipients)", roomId, recipientCount);
        }
        return formatClientEndpoint(clientId);
    }

    private String formatClientEndpoint(String clientId) {
        if (clientId == null) {
            return "unknown-client";
        }
        ClientNetworkStats client = clientStats.get(clientId);
        if (client == null) {
            return shortClientId(clientId);
        }
        String playerName = client.getPlayerName();
        String address = client.getRemoteAddress() + ":" + client.getRemotePort();
        String id = shortClientId(clientId);
        if (playerName == null || playerName.isBlank()) {
            return String.format("%s@%s", id, address);
        }
        return String.format("%s (%s@%s)", playerName, id, address);
    }

    private String shortClientId(String clientId) {
        return clientId.length() > 8 ? clientId.substring(0, 8) : clientId;
    }

    // --- Room management ---

    public RoomNetworkStats getOrCreateRoomStats(String roomId) {
        return roomStats.computeIfAbsent(roomId, RoomNetworkStats::new);
    }

    public void removeRoomStats(String roomId) {
        roomStats.remove(roomId);
    }

    // --- Client stats access ---

    public ClientNetworkStats getClientStats(String clientId) {
        return clientStats.get(clientId);
    }

    public void updateClientRoom(String clientId, String roomId) {
        ClientNetworkStats client = clientStats.get(clientId);
        if (client != null) {
            client.setCurrentRoomId(roomId);
        }
    }

    public void updateClientPlayerName(String clientId, String playerName) {
        ClientNetworkStats client = clientStats.get(clientId);
        if (client != null) {
            client.setPlayerName(playerName);
        }
    }

    // --- Background updates ---

    private void startBackgroundUpdates() {
        updateScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NetworkStats-Updater");
            t.setDaemon(true);
            return t;
        });

        updateScheduler.scheduleAtFixedRate(this::performPeriodicUpdate,
                updateIntervalMs, updateIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void restartBackgroundUpdates() {
        if (updateScheduler != null) {
            updateScheduler.shutdown();
        }
        startBackgroundUpdates();
    }

    private void performPeriodicUpdate() {
        if (!enabled.get())
            return;

        try {
            // Calculate throughput
            calculateThroughput();

            // Update client throughputs
            clientStats.values().forEach(ClientNetworkStats::calculateThroughput);

            // Update room traffic samples
            roomStats.values().forEach(RoomNetworkStats::recordTrafficSample);

            // Record time-series samples
            bytesPerSecIn.add(currentBytesPerSecIn);
            bytesPerSecOut.add(currentBytesPerSecOut);
            msgsPerSec.add(currentMsgsPerSec);
            connectedClientsHistory.add(currentConnectedClients);

            // Record JVM metrics
            recordJvmMetrics();

            // Broadcast to admin subscribers
            broadcastToAdmins();

        } catch (Exception e) {
            logger.error("Error in periodic stats update", e);
        }
    }

    private void calculateThroughput() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastThroughputCalcTime;
        if (elapsed <= 0)
            return;

        long currentBytesSent = totalBytesSent.sum();
        long currentBytesReceived = totalBytesReceived.sum();
        long currentMsgsSent = totalMessagesSent.sum();
        long currentMsgsReceived = totalMessagesReceived.sum();

        double seconds = elapsed / 1000.0;
        currentBytesPerSecOut = (currentBytesSent - lastBytesSentSnapshot) / seconds;
        currentBytesPerSecIn = (currentBytesReceived - lastBytesReceivedSnapshot) / seconds;
        currentMsgsPerSec = ((currentMsgsSent - lastMsgsSentSnapshot) +
                (currentMsgsReceived - lastMsgsReceivedSnapshot)) / seconds;

        lastThroughputCalcTime = now;
        lastBytesSentSnapshot = currentBytesSent;
        lastBytesReceivedSnapshot = currentBytesReceived;
        lastMsgsSentSnapshot = currentMsgsSent;
        lastMsgsReceivedSnapshot = currentMsgsReceived;
    }

    private void recordJvmMetrics() {
        // CPU load
        cpuLoadHistory.add(readCpuLoadPercent());

        // Heap usage
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        heapUsedHistory.add(heapUsed / (1024.0 * 1024.0)); // MB

        // Thread count
        threadCountHistory.add(threadBean.getThreadCount());
    }

    private void updateCurrentClientCount() {
        currentConnectedClients = (int) clientStats.values().stream()
                .filter(ClientNetworkStats::isConnected)
                .count();
    }

    private double readCpuLoadPercent() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            double processLoad = sunBean.getProcessCpuLoad();
            if (processLoad >= 0) {
                return processLoad * 100.0;
            }
            double systemLoad = sunBean.getSystemCpuLoad();
            if (systemLoad >= 0) {
                return systemLoad * 100.0;
            }
        }
        double systemLoadAverage = osBean.getSystemLoadAverage();
        if (systemLoadAverage >= 0) {
            int cores = osBean.getAvailableProcessors();
            if (cores > 0) {
                return Math.min(100.0, (systemLoadAverage / cores) * 100.0);
            }
        }
        return 0;
    }

    // --- Admin subscription ---

    public interface AdminSubscriber {
        void onStatsUpdate(ServerStatsSnapshot snapshot);
    }

    public void subscribeAdmin(String subscriberId, AdminSubscriber subscriber) {
        adminSubscribers.put(subscriberId, subscriber);
    }

    public void unsubscribeAdmin(String subscriberId) {
        adminSubscribers.remove(subscriberId);
    }

    private void broadcastToAdmins() {
        if (adminSubscribers.isEmpty())
            return;
        ServerStatsSnapshot snapshot = toSnapshot();
        adminSubscribers.values().forEach(sub -> {
            try {
                sub.onStatsUpdate(snapshot);
            } catch (Exception e) {
                logger.error("Error broadcasting to admin subscriber", e);
            }
        });
    }

    // --- Getters ---

    public long getServerStartTime() {
        return serverStartTime;
    }

    public long getServerUptime() {
        return System.currentTimeMillis() - serverStartTime;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getCurrentConnectedClients() {
        return currentConnectedClients;
    }

    public long getTotalConnectionsAccepted() {
        return totalConnectionsAccepted.sum();
    }

    public long getTotalDisconnects() {
        return totalDisconnects.sum();
    }

    public long getTotalMessagesSent() {
        return totalMessagesSent.sum();
    }

    public long getTotalMessagesReceived() {
        return totalMessagesReceived.sum();
    }

    public long getTotalBytesSent() {
        return totalBytesSent.sum();
    }

    public long getTotalBytesReceived() {
        return totalBytesReceived.sum();
    }

    public double getCurrentBytesPerSecIn() {
        return currentBytesPerSecIn;
    }

    public double getCurrentBytesPerSecOut() {
        return currentBytesPerSecOut;
    }

    public double getCurrentMsgsPerSec() {
        return currentMsgsPerSec;
    }

    public double getAverageMessageSize() {
        long count = messageSizeCount.sum();
        return count > 0 ? (double) messageSizeSum.sum() / count : 0;
    }

    public int getMinMessageSize() {
        return minMessageSize == Integer.MAX_VALUE ? 0 : minMessageSize;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public long getParseErrors() {
        return parseErrors.sum();
    }

    public long getInvalidPayloads() {
        return invalidPayloads.sum();
    }

    public long getDroppedMessages() {
        return droppedMessages.sum();
    }

    public int getRoomCount() {
        return roomStats.size();
    }

    public List<PacketLogEntry> getGlobalPacketLog() {
        return new ArrayList<>(globalPacketLog);
    }

    /**
     * Get top message types by volume (bytes received).
     */
    public List<MessageTypeStats> getTopMessageTypes(int limit) {
        List<MessageTypeStats> stats = new ArrayList<>();
        messageTypeBytesRx.forEach((type, bytesAdder) -> {
            long bytes = bytesAdder.sum();
            long countRx = messageTypeCountsRx.getOrDefault(type, new LongAdder()).sum();
            long countTx = messageTypeCountsTx.getOrDefault(type, new LongAdder()).sum();
            stats.add(new MessageTypeStats(type, countRx, countTx, bytes));
        });
        stats.sort(Comparator.comparingLong(s -> -s.totalBytes));
        return stats.subList(0, Math.min(limit, stats.size()));
    }

    public Map<String, ClientNetworkStats.ClientStatsSnapshot> getAllClientSnapshots() {
        Map<String, ClientNetworkStats.ClientStatsSnapshot> snapshots = new HashMap<>();
        clientStats.forEach((id, stats) -> snapshots.put(id, stats.toSnapshot()));
        return snapshots;
    }

    public Map<String, RoomNetworkStats.RoomStatsSnapshot> getAllRoomSnapshots() {
        Map<String, RoomNetworkStats.RoomStatsSnapshot> snapshots = new HashMap<>();
        roomStats.forEach((id, stats) -> snapshots.put(id, stats.toSnapshot()));
        return snapshots;
    }

    /**
     * Create a full snapshot for transmission to admin clients.
     */
    public ServerStatsSnapshot toSnapshot() {
        return new ServerStatsSnapshot(
                serverStartTime,
                getServerUptime(),
                serverPort,
                currentConnectedClients,
                getTotalConnectionsAccepted(),
                getTotalDisconnects(),
                totalReconnectAttempts.sum(),
                getTotalMessagesSent(),
                getTotalMessagesReceived(),
                getTotalBytesSent(),
                getTotalBytesReceived(),
                currentBytesPerSecIn,
                currentBytesPerSecOut,
                currentMsgsPerSec,
                getAverageMessageSize(),
                getMinMessageSize(),
                getMaxMessageSize(),
                getParseErrors(),
                getInvalidPayloads(),
                getDroppedMessages(),
                getRoomCount(),
                getTopMessageTypes(10),
                bytesPerSecIn.getValues(),
                bytesPerSecOut.getValues(),
                msgsPerSec.getValues(),
                connectedClientsHistory.getValues(),
                cpuLoadHistory.getValues(),
                heapUsedHistory.getValues(),
                threadCountHistory.getValues(),
                memoryBean.getHeapMemoryUsage().getMax() / (1024.0 * 1024.0),
                memoryBean.getHeapMemoryUsage().getUsed() / (1024.0 * 1024.0),
                threadBean.getThreadCount(),
                getGcStats(),
                getAllClientSnapshots(),
                getAllRoomSnapshots());
    }

    private GcStats getGcStats() {
        long totalCount = 0;
        long totalTime = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            totalCount += gc.getCollectionCount();
            totalTime += gc.getCollectionTime();
        }
        return new GcStats(totalCount, totalTime);
    }

    public void shutdown() {
        if (updateScheduler != null) {
            updateScheduler.shutdown();
            try {
                updateScheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.info("ServerNetworkStats shutdown");
    }

    // --- Inner classes for snapshots ---

    public static class MessageTypeStats implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        public final String messageType;
        public final long countRx;
        public final long countTx;
        public final long totalBytes;

        public MessageTypeStats(String messageType, long countRx, long countTx, long totalBytes) {
            this.messageType = messageType;
            this.countRx = countRx;
            this.countTx = countTx;
            this.totalBytes = totalBytes;
        }
    }

    public static class GcStats implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        public final long totalCollections;
        public final long totalTimeMs;

        public GcStats(long totalCollections, long totalTimeMs) {
            this.totalCollections = totalCollections;
            this.totalTimeMs = totalTimeMs;
        }
    }

    public static class ServerStatsSnapshot implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final long serverStartTime;
        public final long serverUptime;
        public final int serverPort;
        public final int currentConnectedClients;
        public final long totalConnectionsAccepted;
        public final long totalDisconnects;
        public final long totalReconnectAttempts;
        public final long totalMessagesSent;
        public final long totalMessagesReceived;
        public final long totalBytesSent;
        public final long totalBytesReceived;
        public final double bytesPerSecIn;
        public final double bytesPerSecOut;
        public final double msgsPerSec;
        public final double avgMessageSize;
        public final int minMessageSize;
        public final int maxMessageSize;
        public final long parseErrors;
        public final long invalidPayloads;
        public final long droppedMessages;
        public final int roomCount;
        public final List<MessageTypeStats> topMessageTypes;
        public final double[] bytesPerSecInHistory;
        public final double[] bytesPerSecOutHistory;
        public final double[] msgsPerSecHistory;
        public final double[] connectedClientsHistory;
        public final double[] cpuLoadHistory;
        public final double[] heapUsedMbHistory;
        public final double[] threadCountHistory;
        public final double heapMaxMb;
        public final double heapUsedMb;
        public final int threadCount;
        public final GcStats gcStats;
        public final Map<String, ClientNetworkStats.ClientStatsSnapshot> clientStats;
        public final Map<String, RoomNetworkStats.RoomStatsSnapshot> roomStats;

        public ServerStatsSnapshot(long serverStartTime, long serverUptime, int serverPort,
                int currentConnectedClients, long totalConnectionsAccepted,
                long totalDisconnects, long totalReconnectAttempts,
                long totalMessagesSent, long totalMessagesReceived,
                long totalBytesSent, long totalBytesReceived,
                double bytesPerSecIn, double bytesPerSecOut, double msgsPerSec,
                double avgMessageSize, int minMessageSize, int maxMessageSize,
                long parseErrors, long invalidPayloads, long droppedMessages,
                int roomCount, List<MessageTypeStats> topMessageTypes,
                double[] bytesPerSecInHistory, double[] bytesPerSecOutHistory,
                double[] msgsPerSecHistory, double[] connectedClientsHistory,
                double[] cpuLoadHistory, double[] heapUsedMbHistory,
                double[] threadCountHistory, double heapMaxMb,
                double heapUsedMb, int threadCount,
                GcStats gcStats,
                Map<String, ClientNetworkStats.ClientStatsSnapshot> clientStats,
                Map<String, RoomNetworkStats.RoomStatsSnapshot> roomStats) {
            this.serverStartTime = serverStartTime;
            this.serverUptime = serverUptime;
            this.serverPort = serverPort;
            this.currentConnectedClients = currentConnectedClients;
            this.totalConnectionsAccepted = totalConnectionsAccepted;
            this.totalDisconnects = totalDisconnects;
            this.totalReconnectAttempts = totalReconnectAttempts;
            this.totalMessagesSent = totalMessagesSent;
            this.totalMessagesReceived = totalMessagesReceived;
            this.totalBytesSent = totalBytesSent;
            this.totalBytesReceived = totalBytesReceived;
            this.bytesPerSecIn = bytesPerSecIn;
            this.bytesPerSecOut = bytesPerSecOut;
            this.msgsPerSec = msgsPerSec;
            this.avgMessageSize = avgMessageSize;
            this.minMessageSize = minMessageSize;
            this.maxMessageSize = maxMessageSize;
            this.parseErrors = parseErrors;
            this.invalidPayloads = invalidPayloads;
            this.droppedMessages = droppedMessages;
            this.roomCount = roomCount;
            this.topMessageTypes = topMessageTypes;
            this.bytesPerSecInHistory = bytesPerSecInHistory;
            this.bytesPerSecOutHistory = bytesPerSecOutHistory;
            this.msgsPerSecHistory = msgsPerSecHistory;
            this.connectedClientsHistory = connectedClientsHistory;
            this.cpuLoadHistory = cpuLoadHistory;
            this.heapUsedMbHistory = heapUsedMbHistory;
            this.threadCountHistory = threadCountHistory;
            this.heapMaxMb = heapMaxMb;
            this.heapUsedMb = heapUsedMb;
            this.threadCount = threadCount;
            this.gcStats = gcStats;
            this.clientStats = clientStats;
            this.roomStats = roomStats;
        }
    }
}
