package com.scribble.server;

import com.scribble.utils.Constants;

public final class ServerMain {
    public static void main(String[] args) {
        int port = Constants.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                System.err.println("Invalid port. Using default " + Constants.DEFAULT_PORT);
            }
        }
        Server server = new Server(port);
        server.start();
    }
}
