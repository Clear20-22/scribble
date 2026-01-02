package com.scribble.server;

import com.scribble.server.admin.AdminMonitorApp;
import com.scribble.utils.Constants;

public final class ServerMain {
    public static void main(String[] args) {
        int port = Constants.DEFAULT_PORT;
        boolean headless = false;
        boolean monitoringEnabled = true;
        Integer monitorIntervalMs = null;
        Integer monitorRingSize = null;
        Integer monitorLogSize = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--headless") || args[i].equals("-h")) {
                headless = true;
            } else if (args[i].equals("--no-monitoring") || args[i].equals("--disable-monitoring")) {
                monitoringEnabled = false;
            } else if (args[i].equals("--monitor-interval-ms")) {
                if (i + 1 < args.length) {
                    monitorIntervalMs = parsePositiveInt(args[++i], "monitor interval");
                }
            } else if (args[i].equals("--monitor-ring-size")) {
                if (i + 1 < args.length) {
                    monitorRingSize = parsePositiveInt(args[++i], "monitor ring size");
                }
            } else if (args[i].equals("--monitor-log-size")) {
                if (i + 1 < args.length) {
                    monitorLogSize = parsePositiveInt(args[++i], "monitor log size");
                }
            } else if (args[i].equals("--port") || args[i].equals("-p")) {
                if (i + 1 < args.length) {
                    try {
                        port = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException ignored) {
                        System.err.println("Invalid port. Using default " + Constants.DEFAULT_PORT);
                    }
                }
            } else {
                try {
                    port = Integer.parseInt(args[i]);
                } catch (NumberFormatException ignored) {
                    System.err.println("Invalid argument: " + args[i]);
                }
            }
        }

        if (monitoringEnabled) {
            if (monitorIntervalMs != null) {
                System.setProperty("scribble.monitor.updateIntervalMs", monitorIntervalMs.toString());
            }
            if (monitorRingSize != null) {
                System.setProperty("scribble.monitor.ringSize", monitorRingSize.toString());
            }
            if (monitorLogSize != null) {
                System.setProperty("scribble.monitor.logSize", monitorLogSize.toString());
            }
        }

        Server server = new Server(port, monitoringEnabled);

        // Launch Admin Monitor UI unless running in headless mode
        if (!headless && monitoringEnabled) {
            final int finalPort = port;
            AdminMonitorApp.launchWithServer(server, finalPort);
            System.out.println("Admin Monitor UI launching...");
        } else if (!monitoringEnabled) {
            System.out.println("Monitoring disabled (no UI)");
        } else {
            System.out.println("Running in headless mode (no UI)");
        }

        server.start();
    }

    private static Integer parsePositiveInt(String value, String label) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                System.err.println("Invalid " + label + " value: " + value);
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            System.err.println("Invalid " + label + " value: " + value);
            return null;
        }
    }
}
