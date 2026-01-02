package com.scribble.network.messages;

import java.io.Serial;

/**
 * Request message sent by admin clients to subscribe to server metrics updates.
 * Server will respond with periodic ServerMetricsMessage broadcasts.
 */
public final class SubscribeMetricsMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean subscribe;
    private final String adminToken; // Optional: for authentication

    public SubscribeMetricsMessage(boolean subscribe) {
        this(subscribe, null);
    }

    public SubscribeMetricsMessage(boolean subscribe, String adminToken) {
        this.subscribe = subscribe;
        this.adminToken = adminToken;
    }

    public boolean isSubscribe() {
        return subscribe;
    }

    public String getAdminToken() {
        return adminToken;
    }
}
