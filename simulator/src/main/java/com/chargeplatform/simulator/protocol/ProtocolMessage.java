package com.chargeplatform.simulator.protocol;

import java.math.BigDecimal;

public record ProtocolMessage(
        String type,
        String deviceId,
        Integer connectorId,
        String status,
        BigDecimal energyKwh,
        BigDecimal powerKw,
        BigDecimal voltageV,
        BigDecimal currentA,
        String command,
        String message
) {
    public static ProtocolMessage hello(String deviceId, int connectorCount) {
        return new ProtocolMessage("HELLO", deviceId, null, "ONLINE", null, null, null, null,
                "connectors=" + connectorCount, "CHARGE-SIM/1.0");
    }

    public static ProtocolMessage heartbeat(String deviceId) {
        return new ProtocolMessage("HEARTBEAT", deviceId, null, "ONLINE", null, null, null, null, null, null);
    }

    public static ProtocolMessage status(String deviceId, int connectorId, String status,
                                         BigDecimal energyKwh, BigDecimal powerKw,
                                         BigDecimal voltageV, BigDecimal currentA) {
        return new ProtocolMessage("STATUS", deviceId, connectorId, status, energyKwh, powerKw, voltageV, currentA, null, null);
    }
}
