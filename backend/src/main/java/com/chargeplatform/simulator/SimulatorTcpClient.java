package com.chargeplatform.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chargeplatform.device.domain.Connector;
import com.chargeplatform.device.mapper.ConnectorMapper;
import com.chargeplatform.order.mapper.ChargeOrderMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
public class SimulatorTcpClient {
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String legacyHost;
    private final int legacyPort;
    private final String legacyDeviceId;
    private final String devicesConfig;
    private final long reconnectDelayMs;
    private final boolean syncDatabase;
    private final long offlineTimeoutMs;
    private final ConnectorMapper connectorMapper;
    private final ChargeOrderMapper chargeOrderMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(6);
    private final Map<String, DeviceConnection> devices = new ConcurrentHashMap<>();
    private volatile String defaultDeviceId;

    public SimulatorTcpClient(ObjectMapper mapper,
                              @Value("${app.simulator.enabled:false}") boolean enabled,
                              @Value("${app.simulator.host:127.0.0.1}") String host,
                              @Value("${app.simulator.port:9100}") int port,
                              @Value("${app.simulator.device-id:SIM-PILE-001}") String deviceId,
                              @Value("${app.simulator.devices:}") String devicesConfig,
                              @Value("${app.simulator.reconnect-delay-ms:3000}") long reconnectDelayMs,
                              @Value("${app.simulator.sync-database:false}") boolean syncDatabase,
                              @Value("${app.simulator.offline-timeout-ms:30000}") long offlineTimeoutMs,
                              ConnectorMapper connectorMapper,
                              ChargeOrderMapper chargeOrderMapper) {
        this.mapper = mapper; this.enabled = enabled; this.legacyHost = host; this.legacyPort = port;
        this.legacyDeviceId = deviceId; this.devicesConfig = devicesConfig; this.reconnectDelayMs = reconnectDelayMs;
        this.syncDatabase = syncDatabase; this.offlineTimeoutMs = offlineTimeoutMs;
        this.connectorMapper = connectorMapper; this.chargeOrderMapper = chargeOrderMapper;
    }

    @PostConstruct
    void start() {
        for (DeviceConfig config : parseConfigs()) {
            if (defaultDeviceId == null) defaultDeviceId = config.deviceId();
            devices.put(config.deviceId(), new DeviceConnection(config));
        }
        if (!enabled) return;
        devices.values().forEach(device -> {
            scheduler.execute(device::connectLoop);
            scheduler.scheduleAtFixedRate(() -> sendTo(device.config.deviceId(), new Command("PING", null)), 10, 10, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(device::markOfflineIfStale, 10, 10, TimeUnit.SECONDS);
        });
    }

    private List<DeviceConfig> parseConfigs() {
        if (devicesConfig != null && !devicesConfig.isBlank()) {
            return Arrays.stream(devicesConfig.split(",")).map(String::trim).filter(item -> !item.isBlank()).map(this::parseConfig).toList();
        }
        return List.of(new DeviceConfig(legacyDeviceId, legacyHost, legacyPort));
    }

    private DeviceConfig parseConfig(String value) {
        String[] pair = value.split("@", 2);
        String id = pair.length == 2 ? pair[0] : legacyDeviceId;
        String address = pair.length == 2 ? pair[1] : pair[0];
        int separator = address.lastIndexOf(':');
        if (separator < 0) return new DeviceConfig(id, legacyHost, Integer.parseInt(address));
        return new DeviceConfig(id, address.substring(0, separator), Integer.parseInt(address.substring(separator + 1)));
    }

    public synchronized void send(Command command) {
        String first = defaultDeviceId == null ? legacyDeviceId : defaultDeviceId;
        sendTo(first, command);
    }

    public void sendToDevice(String deviceId, Command command) { sendTo(deviceId, command); }

    private void sendTo(String deviceId, Command command) {
        DeviceConnection device = devices.get(deviceId);
        if (device == null) throw new IllegalStateException("unknown simulator device: " + deviceId);
        device.send(command);
    }

    public boolean enabled() { return enabled; }

    public FleetStatus status() {
        List<SimulatorStatus> statuses = devices.values().stream().sorted(Comparator.comparing(item -> item.config.deviceId())).map(DeviceConnection::status).toList();
        return new FleetStatus(enabled, statuses);
    }

    private void syncConnector(Message message) {
        if (!Set.of("IDLE", "CHARGING", "FAULT", "OFFLINE").contains(message.status())) return;
        Connector connector = connectorMapper.selectById(message.connectorId().longValue());
        if (connector == null) return;
        if (!connector.getStatus().equals(message.status())) { connector.changeStatus(message.status()); connectorMapper.updateById(connector); }
        if ("CHARGING".equals(message.status()) && message.energyKwh() != null) chargeOrderMapper.updateChargingEnergy(message.connectorId().longValue(), message.energyKwh());
    }

    @PreDestroy
    void stop() { scheduler.shutdownNow(); devices.values().forEach(DeviceConnection::close); }

    public record Command(String command, Integer connectorId) { }
    public record ConnectorSnapshot(Integer connectorId, String status, BigDecimal energyKwh, BigDecimal powerKw, BigDecimal voltageV, BigDecimal currentA, Instant lastMessageAt) { }
    public record SimulatorStatus(boolean enabled, boolean connected, String deviceId, Instant lastMessageAt, Map<Integer, ConnectorSnapshot> connectors) { }
    public record FleetStatus(boolean enabled, List<SimulatorStatus> devices) { }
    private record DeviceConfig(String deviceId, String host, int port) { }
    private record Message(String type, String deviceId, Integer connectorId, String status, BigDecimal energyKwh, BigDecimal powerKw, BigDecimal voltageV, BigDecimal currentA) { }

    private final class DeviceConnection {
        private final DeviceConfig config;
        private final Map<Integer, ConnectorSnapshot> connectors = new ConcurrentHashMap<>();
        private volatile Socket socket;
        private volatile BufferedWriter writer;
        private volatile boolean connected;
        private volatile Instant lastMessageAt;
        private volatile String actualDeviceId;

        private DeviceConnection(DeviceConfig config) { this.config = config; }

        private void connectLoop() {
            while (!scheduler.isShutdown()) {
                try (Socket current = new Socket(config.host(), config.port()); BufferedReader reader = new BufferedReader(new InputStreamReader(current.getInputStream())); BufferedWriter output = new BufferedWriter(new OutputStreamWriter(current.getOutputStream()))) {
                    socket = current; writer = output; connected = true;
                    String line; while ((line = reader.readLine()) != null) handle(line);
                } catch (IOException ignored) { connected = false; }
                finally { connected = false; socket = null; writer = null; }
                try { Thread.sleep(reconnectDelayMs); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }

        private void handle(String line) throws IOException {
            Message message = mapper.readValue(line, Message.class); lastMessageAt = Instant.now();
            if (message.deviceId() != null) actualDeviceId = message.deviceId();
            if ("STATUS".equals(message.type()) && message.connectorId() != null) {
                BigDecimal voltage = message.voltageV() == null ? BigDecimal.valueOf(220) : message.voltageV();
                BigDecimal current = message.currentA() == null && message.powerKw() != null
                        ? message.powerKw().multiply(BigDecimal.valueOf(1000)).divide(voltage, 2, java.math.RoundingMode.HALF_UP)
                        : message.currentA();
                connectors.put(message.connectorId(), new ConnectorSnapshot(message.connectorId(), message.status(), message.energyKwh(), message.powerKw(), voltage, current, lastMessageAt));
                if (syncDatabase) syncConnector(message);
            }
        }

        private synchronized void send(Command command) {
            if (!connected || writer == null) throw new IllegalStateException("simulator is not connected: " + config.deviceId());
            try { writer.write(mapper.writeValueAsString(command)); writer.newLine(); writer.flush(); }
            catch (IOException error) { connected = false; throw new IllegalStateException("failed to send simulator command", error); }
        }

        private void markOfflineIfStale() {
            Instant last = lastMessageAt;
            if (last == null || Instant.now().minusMillis(offlineTimeoutMs).isBefore(last)) return;
            connectors.replaceAll((id, snapshot) -> new ConnectorSnapshot(id, "OFFLINE", snapshot.energyKwh(), snapshot.powerKw(), snapshot.voltageV(), snapshot.currentA(), snapshot.lastMessageAt()));
        }

        private SimulatorStatus status() {
            boolean live = connected && lastMessageAt != null && Instant.now().minusMillis(offlineTimeoutMs).isBefore(lastMessageAt);
            return new SimulatorStatus(enabled, live, actualDeviceId == null ? config.deviceId() : actualDeviceId, lastMessageAt, Map.copyOf(connectors));
        }

        private void close() { try { if (socket != null) socket.close(); } catch (IOException ignored) { } }
    }
}
