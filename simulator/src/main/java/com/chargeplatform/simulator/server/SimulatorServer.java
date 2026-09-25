package com.chargeplatform.simulator.server;

import com.chargeplatform.simulator.device.VirtualCharger;
import com.chargeplatform.simulator.device.VirtualConnector;
import com.chargeplatform.simulator.protocol.ProtocolMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SimulatorServer implements AutoCloseable {
    private final int port;
    private final VirtualCharger charger;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private final CopyOnWriteArrayList<ClientSession> sessions = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public SimulatorServer(int port, String deviceId, int connectorCount) {
        this.port = port;
        this.charger = new VirtualCharger(deviceId, connectorCount);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        ticker.scheduleAtFixedRate(this::tickAndBroadcast, 1, 1, TimeUnit.SECONDS);
        clients.submit(this::acceptLoop);
    }

    public int localPort() {
        if (serverSocket == null) throw new IllegalStateException("server is not started");
        return serverSocket.getLocalPort();
    }

    private void tickAndBroadcast() {
        charger.tick();
        sessions.forEach(session -> {
            charger.connectors().values().forEach(connector -> {
                try { session.sendStatus(connector); } catch (IOException ignored) { }
            });
        });
    }

    private void acceptLoop() {
        while (running) {
            try {
                ClientSession session = new ClientSession(serverSocket.accept());
                sessions.add(session);
                clients.submit(session);
            } catch (IOException error) {
                if (running) error.printStackTrace();
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        ticker.shutdownNow();
        clients.shutdownNow();
        if (serverSocket != null) serverSocket.close();
    }

    private final class ClientSession implements Runnable {
        private final Socket socket;
        private BufferedWriter writer;

        private ClientSession(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try (socket;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                writer = output;
                send(ProtocolMessage.hello(charger.deviceId(), charger.connectors().size()));
                String line;
                while ((line = reader.readLine()) != null) handle(line);
            } catch (Exception error) {
                if (running) System.err.println("simulator client disconnected: " + error.getMessage());
            } finally {
                sessions.remove(this);
            }
        }

        private void handle(String line) throws IOException {
            Command command = mapper.readValue(line, Command.class);
            if ("PING".equalsIgnoreCase(command.command())) {
                send(ProtocolMessage.heartbeat(charger.deviceId()));
                return;
            }
            if (command.connectorId() == null) throw new IllegalArgumentException("connectorId is required");
            VirtualConnector connector = charger.connector(command.connectorId());
            if ("START".equalsIgnoreCase(command.command())) connector.start();
            else if ("STOP".equalsIgnoreCase(command.command())) connector.stop();
            else throw new IllegalArgumentException("unsupported command: " + command.command());
            sendStatus(connector);
        }

        private void sendStatus(VirtualConnector connector) throws IOException {
            send(ProtocolMessage.status(charger.deviceId(), connector.id(), connector.status(), connector.energyKwh(), connector.ratedPowerKw(), connector.voltageV(), connector.currentA()));
        }

        private synchronized void send(ProtocolMessage message) throws IOException {
            writer.write(mapper.writeValueAsString(message));
            writer.newLine();
            writer.flush();
        }
    }

    private record Command(String command, Integer connectorId) {}
}
