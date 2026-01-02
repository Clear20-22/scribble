package com.scribble.server.monitor;

import com.scribble.network.messages.ServerMetricsMessage;
import com.scribble.server.ClientHandler;
import com.scribble.utils.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Handles broadcasting server metrics to subscribed admin clients.
 * Converts internal stats snapshots to network messages.
 */
public final class NetworkMonitorBroadcaster implements ServerNetworkStats.AdminSubscriber {
    private static final Logger logger = Logger.getLogger(NetworkMonitorBroadcaster.class);

    private final Set<ClientHandler> subscribers = ConcurrentHashMap.newKeySet();
    private final ServerNetworkStats serverStats;

    public NetworkMonitorBroadcaster(ServerNetworkStats serverStats) {
        this.serverStats = serverStats;
        serverStats.subscribeAdmin("broadcaster", this);
    }

    public void subscribe(ClientHandler client) {
        subscribers.add(client);
        logger.info("Admin client subscribed to metrics: " + client.getClientId());
    }

    public void unsubscribe(ClientHandler client) {
        subscribers.remove(client);
        logger.info("Admin client unsubscribed from metrics: " + client.getClientId());
    }

    /**
     * Alias for subscribe() - for convenience.
     */
    public void addSubscriber(ClientHandler client) {
        subscribe(client);
    }

    /**
     * Alias for unsubscribe() - for convenience.
     */
    public void removeSubscriber(ClientHandler client) {
        unsubscribe(client);
    }

    public boolean isSubscribed(ClientHandler client) {
        return subscribers.contains(client);
    }

    public int getSubscriberCount() {
        return subscribers.size();
    }

    @Override
    public void onStatsUpdate(ServerNetworkStats.ServerStatsSnapshot snapshot) {
        if (subscribers.isEmpty())
            return;

        ServerMetricsMessage message = convertToMessage(snapshot);

        // Remove disconnected clients and send to active ones
        subscribers.removeIf(client -> !client.isConnected());

        for (ClientHandler client : subscribers) {
            try {
                client.send(message);
            } catch (Exception e) {
                logger.error("Failed to send metrics to admin client", e);
                subscribers.remove(client);
            }
        }
    }

    /**
     * Convert internal snapshot to network message format.
     */
    private ServerMetricsMessage convertToMessage(ServerNetworkStats.ServerStatsSnapshot snapshot) {
        // Convert client stats
        var clientInfoMap = snapshot.clientStats.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> convertClientStats(e.getValue())));

        // Convert room stats
        var roomInfoMap = snapshot.roomStats.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> convertRoomStats(e.getValue())));

        // Convert message type stats
        List<ServerMetricsMessage.MessageTypeInfo> messageTypes = snapshot.topMessageTypes.stream()
                .map(m -> new ServerMetricsMessage.MessageTypeInfo(
                        m.messageType, m.countRx, m.countTx, m.totalBytes))
                .collect(Collectors.toList());

        // Convert recent packets
        List<ServerMetricsMessage.PacketInfo> packets = serverStats.getGlobalPacketLog().stream()
                .limit(50)
                .map(p -> new ServerMetricsMessage.PacketInfo(
                        p.getTimestamp(),
                        p.getDirection().name(),
                        p.getMessageType(),
                        p.getSizeBytes(),
                        p.getClientId(),
                        p.getRoomId()))
                .collect(Collectors.toList());

        return new ServerMetricsMessage(
                snapshot.serverStartTime,
                snapshot.serverUptime,
                snapshot.serverPort,
                snapshot.currentConnectedClients,
                snapshot.totalConnectionsAccepted,
                snapshot.totalDisconnects,
                snapshot.totalReconnectAttempts,
                snapshot.totalMessagesSent,
                snapshot.totalMessagesReceived,
                snapshot.totalBytesSent,
                snapshot.totalBytesReceived,
                snapshot.bytesPerSecIn,
                snapshot.bytesPerSecOut,
                snapshot.msgsPerSec,
                snapshot.avgMessageSize,
                snapshot.minMessageSize,
                snapshot.maxMessageSize,
                snapshot.parseErrors,
                snapshot.invalidPayloads,
                snapshot.droppedMessages,
                snapshot.roomCount,
                messageTypes,
                snapshot.bytesPerSecInHistory,
                snapshot.bytesPerSecOutHistory,
                snapshot.msgsPerSecHistory,
                snapshot.connectedClientsHistory,
                snapshot.cpuLoadHistory,
                snapshot.heapUsedMbHistory,
                snapshot.threadCountHistory,
                snapshot.heapMaxMb,
                snapshot.gcStats.totalCollections,
                snapshot.gcStats.totalTimeMs,
                clientInfoMap,
                roomInfoMap,
                packets);
    }

    private ServerMetricsMessage.ClientInfo convertClientStats(ClientNetworkStats.ClientStatsSnapshot cs) {
        return new ServerMetricsMessage.ClientInfo(
                cs.clientId,
                cs.remoteAddress,
                cs.remotePort,
                cs.playerName,
                cs.currentRoomId,
                cs.connected,
                cs.sessionDurationMs,
                cs.messagesSent,
                cs.messagesReceived,
                cs.bytesSent,
                cs.bytesReceived,
                cs.lastRtt,
                cs.avgRtt,
                cs.minRtt,
                cs.maxRtt,
                cs.latencyHistory,
                cs.txBytesPerSec,
                cs.rxBytesPerSec,
                cs.msgsPerSec,
                cs.parseErrors,
                cs.droppedMessages);
    }

    private ServerMetricsMessage.RoomInfo convertRoomStats(RoomNetworkStats.RoomStatsSnapshot rs) {
        return new ServerMetricsMessage.RoomInfo(
                rs.roomId,
                rs.uptime,
                rs.messagesBroadcast,
                rs.messagesReceived,
                rs.bytesBroadcast,
                rs.bytesReceived,
                rs.currentPlayerCount,
                rs.currentSpectatorCount,
                rs.roundsPlayed,
                rs.drawMessages,
                rs.chatMessages,
                rs.guessAttempts,
                rs.correctGuesses,
                rs.trafficHistory);
    }

    public void shutdown() {
        subscribers.clear();
        serverStats.unsubscribeAdmin("broadcaster");
    }
}
