package com.chargeplatform.simulator.device;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class VirtualCharger {
    private final String deviceId;
    private final Map<Integer, VirtualConnector> connectors;

    public VirtualCharger(String deviceId, int connectorCount) {
        if (connectorCount < 1 || connectorCount > 20) throw new IllegalArgumentException("connectorCount must be 1..20");
        this.deviceId = deviceId;
        this.connectors = IntStream.rangeClosed(1, connectorCount)
                .boxed().collect(Collectors.toMap(id -> id, id -> new VirtualConnector(id, BigDecimal.valueOf(7.2))));
    }

    public String deviceId() { return deviceId; }
    public Map<Integer, VirtualConnector> connectors() { return connectors; }
    public VirtualConnector connector(int id) {
        VirtualConnector connector = connectors.get(id);
        if (connector == null) throw new IllegalArgumentException("unknown connector: " + id);
        return connector;
    }
    public void tick() { connectors.values().forEach(VirtualConnector::tick); }
}
