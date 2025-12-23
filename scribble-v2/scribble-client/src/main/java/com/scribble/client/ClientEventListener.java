package com.scribble.client;

import com.scribble.network.messages.*;

public interface ClientEventListener {
    default void onRoomList(RoomListMessage message) {}
    default void onJoinResult(JoinResultMessage message) {}
    default void onPlayerList(PlayerListMessage message) {}
    default void onGameState(GameStateMessage message) {}
    default void onDraw(DrawMessage message) {}
    default void onClear(ClearMessage message) {}
    default void onChat(ChatMessage message) {}
    default void onError(ErrorMessage message) {}
    default void onConnectionStatus(ConnectionStatus status) {}
    default void onDisconnected(String reason) {}
}
