package com.chargeplatform.simulator;

import com.chargeplatform.simulator.server.SimulatorServer;

import java.util.concurrent.CountDownLatch;

/** Starts a standalone virtual charging pile TCP endpoint. */
public final class SimulatorApplication {
    private SimulatorApplication() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("simulator.port", "9100"));
        String deviceId = System.getProperty("simulator.device-id", "SIM-PILE-001");
        int connectorCount = Integer.parseInt(System.getProperty("simulator.connectors", "2"));
        try (SimulatorServer server = new SimulatorServer(port, deviceId, connectorCount)) {
            server.start();
            System.out.printf("Virtual charger %s listening on TCP %d with %d connectors%n", deviceId, port, connectorCount);
            new CountDownLatch(1).await();
        }
    }
}
