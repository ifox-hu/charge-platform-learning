package com.chargeplatform.simulator.device;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class VirtualConnector {
    private final int id;
    private final BigDecimal ratedPowerKw;
    private String status = "IDLE";
    private BigDecimal energyKwh = BigDecimal.ZERO;

    public VirtualConnector(int id, BigDecimal ratedPowerKw) {
        this.id = id;
        this.ratedPowerKw = ratedPowerKw;
    }

    public synchronized void start() {
        if (!"IDLE".equals(status)) {
            throw new IllegalStateException("connector is not idle");
        }
        status = "CHARGING";
        energyKwh = BigDecimal.ZERO;
    }

    public synchronized void stop() {
        status = "IDLE";
    }

    public synchronized void tick() {
        if ("CHARGING".equals(status)) {
            // Simulator ticks every second; this is deliberately accelerated for local demos.
            energyKwh = energyKwh.add(ratedPowerKw.divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP));
        }
    }

    public synchronized String status() { return status; }
    public int id() { return id; }
    public BigDecimal ratedPowerKw() { return ratedPowerKw; }
    public BigDecimal voltageV() { return BigDecimal.valueOf(220); }
    public synchronized BigDecimal currentA() { return ratedPowerKw.multiply(BigDecimal.valueOf(1000)).divide(voltageV(), 2, RoundingMode.HALF_UP); }
    public synchronized BigDecimal energyKwh() { return energyKwh; }
}
