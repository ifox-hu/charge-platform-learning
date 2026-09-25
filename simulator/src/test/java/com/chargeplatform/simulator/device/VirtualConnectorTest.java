package com.chargeplatform.simulator.device;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class VirtualConnectorTest {
    @Test
    void startsTicksAndStops() {
        VirtualConnector connector = new VirtualConnector(1, BigDecimal.valueOf(7.2));
        connector.start();
        connector.tick();
        assertEquals("CHARGING", connector.status());
        assertTrue(connector.energyKwh().compareTo(BigDecimal.ZERO) > 0);
        connector.stop();
        assertEquals("IDLE", connector.status());
    }

    @Test
    void cannotStartTwice() {
        VirtualConnector connector = new VirtualConnector(1, BigDecimal.valueOf(7.2));
        connector.start();
        assertThrows(IllegalStateException.class, connector::start);
    }
}
