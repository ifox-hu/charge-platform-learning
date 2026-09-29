package com.chargeplatform.simulator.device;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class VirtualConnector {
    private final int id;
    private final BigDecimal ratedPowerKw;
    private final double timeScale;
    private String status = "IDLE";
    private BigDecimal energyKwh = BigDecimal.ZERO;
    private long lastTickNanos;

    public VirtualConnector(int id, BigDecimal ratedPowerKw) {
        this(id, ratedPowerKw, 1.0);
    }

    public VirtualConnector(int id, BigDecimal ratedPowerKw, double timeScale) {
        if (timeScale <= 0) throw new IllegalArgumentException("timeScale must be positive");
        this.id = id;
        this.ratedPowerKw = ratedPowerKw;
        this.timeScale = timeScale;
    }

    public synchronized void start() {
        if (!"IDLE".equals(status)) {
            throw new IllegalStateException("connector is not idle");
        }
        status = "CHARGING";
        energyKwh = BigDecimal.ZERO;
        lastTickNanos = System.nanoTime();
    }

    public synchronized void stop() {
        status = "IDLE";
    }

    public synchronized void tick() {
        if ("CHARGING".equals(status)) {
            long now = System.nanoTime();
            // Keep an immediate first tick observable in tests and in a freshly connected UI.
            long elapsedNanos = lastTickNanos == 0 ? 1_000_000L : Math.max(1_000_000L, now - lastTickNanos);
            lastTickNanos = now;
            BigDecimal simulatedSeconds = BigDecimal.valueOf(elapsedNanos)
                    .divide(BigDecimal.valueOf(1_000_000_000L), 9, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(timeScale));
            BigDecimal increment = ratedPowerKw.multiply(simulatedSeconds)
                    .divide(BigDecimal.valueOf(3600), 9, RoundingMode.HALF_UP);
            energyKwh = energyKwh.add(increment).setScale(6, RoundingMode.HALF_UP);
        }
    }

    public synchronized String status() { return status; }
    public int id() { return id; }
    public BigDecimal ratedPowerKw() { return ratedPowerKw; }
    public BigDecimal voltageV() { return BigDecimal.valueOf(220); }
    public synchronized BigDecimal currentA() { return ratedPowerKw.multiply(BigDecimal.valueOf(1000)).divide(voltageV(), 2, RoundingMode.HALF_UP); }
    public synchronized BigDecimal energyKwh() { return energyKwh; }
    public double timeScale() { return timeScale; }
}
