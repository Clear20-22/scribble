package com.scribble.client;

import com.scribble.network.messages.*;

public interface ClientEventListener {
    default void onRoomList(RoomListMessage message) {}
    default void onJoinResult(JoinResultMessage message) {}
    default void onPlayerList(PlayerListMessage message) {}
    default void onGameState(GameStateMessage message) {}
    default void onDraw(DrawMessage message) {}
    default void onStrokeHistory(StrokeHistoryMessage message) {}
    default void onWordChoices(WordChoicesMessage message) {}
    default void onWordChosen(WordChosenMessage message) {}
    default void onClear(ClearMessage message) {}
    default void onChat(ChatMessage message) {}
    default void onError(ErrorMessage message) {}
    default void onGuessHint(GuessHintMessage message) {}
    default void onPlayAgainStatus(PlayAgainStatusMessage message) {}
    default void onConnectionStatus(ConnectionStatus status) {}
    default void onLatency(long latencyMs) {}
    default void onDisconnected(String reason) {}
}
