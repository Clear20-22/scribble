package com.scribble.network.messages;

import java.io.Serial;

public final class PlayAgainStatusMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int yesVotes;
    private final int eligiblePlayers;
    private final int requiredVotes;

    public PlayAgainStatusMessage(int yesVotes, int eligiblePlayers, int requiredVotes) {
        this.yesVotes = yesVotes;
        this.eligiblePlayers = eligiblePlayers;
        this.requiredVotes = requiredVotes;
    }

    public int getYesVotes() {
        return yesVotes;
    }

    public int getEligiblePlayers() {
        return eligiblePlayers;
    }

    public int getRequiredVotes() {
        return requiredVotes;
    }
}
