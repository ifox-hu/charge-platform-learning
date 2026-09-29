package com.chargeplatform.simulator;

import com.chargeplatform.device.domain.Charger;
import com.chargeplatform.device.domain.Connector;
import com.chargeplatform.device.mapper.ChargerMapper;
import com.chargeplatform.device.mapper.ConnectorMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulatorBindingResolverTest {

    @Test
    void mapsDatabaseConnectorToItsSimulatorDeviceAndLocalGun() {
        ChargerMapper chargers = mock(ChargerMapper.class);
        ConnectorMapper connectors = mock(ConnectorMapper.class);
        Charger charger = charger(12L, "PILE-002");
        Connector gunA = connector(21L, 12L, "GUN-002-A");
        Connector gunB = connector(37L, 12L, "GUN-002-B");
        when(connectors.selectById(37L)).thenReturn(gunB);
        when(chargers.selectById(12L)).thenReturn(charger);
        when(connectors.selectByChargerId(12L)).thenReturn(List.of(gunA, gunB));

        SimulatorBindingResolver.Binding binding = new SimulatorBindingResolver(chargers, connectors).resolve(37L);

        assertEquals("SIM-PILE-002", binding.deviceId());
        assertEquals(2, binding.localConnectorId());
        assertEquals(37L, binding.databaseConnectorId());
    }

    @Test
    void mapsSimulatorGunBackToDatabaseConnector() {
        ChargerMapper chargers = mock(ChargerMapper.class);
        ConnectorMapper connectors = mock(ConnectorMapper.class);
        Charger charger = charger(18L, "PILE-003");
        Connector gunD = connector(92L, 18L, "GUN-003-D");
        when(chargers.selectByCode("SIM-PILE-003")).thenReturn(null);
        when(chargers.selectByCode("PILE-003")).thenReturn(charger);
        when(connectors.selectByChargerId(18L)).thenReturn(List.of(gunD));

        Connector resolved = new SimulatorBindingResolver(chargers, connectors)
                .resolveDatabaseConnector("SIM-PILE-003", 4);

        assertSame(gunD, resolved);
    }

    private Charger charger(Long id, String code) {
        Charger charger = mock(Charger.class);
        when(charger.getId()).thenReturn(id);
        when(charger.getCode()).thenReturn(code);
        return charger;
    }

    private Connector connector(Long id, Long chargerId, String code) {
        Connector connector = mock(Connector.class);
        when(connector.getId()).thenReturn(id);
        when(connector.getChargerId()).thenReturn(chargerId);
        when(connector.getCode()).thenReturn(code);
        return connector;
    }
}
