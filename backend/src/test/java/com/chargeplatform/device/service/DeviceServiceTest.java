package com.chargeplatform.device.service;

import com.chargeplatform.common.exception.BusinessException;
import com.chargeplatform.device.domain.Charger;
import com.chargeplatform.device.domain.Connector;
import com.chargeplatform.device.mapper.ChargerMapper;
import com.chargeplatform.device.mapper.ConnectorMapper;
import com.chargeplatform.order.mapper.ChargeOrderMapper;
import com.chargeplatform.station.mapper.StationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private ChargerMapper chargers;
    @Mock
    private ConnectorMapper connectors;
    @Mock
    private StationMapper stations;
    @Mock
    private ChargeOrderMapper orders;

    private DeviceService service;

    @BeforeEach
    void setUp() {
        service = new DeviceService(chargers, connectors, stations, orders);
    }

    @Test
    void cannotTakeChargerOfflineWhileAConnectorIsCharging() {
        Long chargerId = 10L;
        Charger charger = mock(Charger.class);
        when(chargers.selectById(chargerId)).thenReturn(charger);
        when(connectors.existsByChargerIdAndStatus(chargerId, "CHARGING")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeStatus(chargerId, "OFFLINE"));

        assertEquals(409, exception.getStatus());
        assertEquals("存在正在充电的充电枪，不能将充电桩离线", exception.getMessage());
        verify(chargers, never()).updateById(charger);
    }

    @Test
    void cannotDeleteChargerWithConnectors() {
        Long chargerId = 10L;
        when(chargers.selectById(chargerId)).thenReturn(mock(Charger.class));
        when(connectors.existsByChargerId(chargerId)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteCharger(chargerId));

        assertEquals(409, exception.getStatus());
        assertEquals("请先删除该充电桩下的充电枪", exception.getMessage());
        verify(chargers, never()).deleteById(chargerId);
    }

    @Test
    void cannotDeleteNonIdleConnector() {
        Long connectorId = 20L;
        Connector connector = mock(Connector.class);
        when(connectors.selectById(connectorId)).thenReturn(connector);
        when(connector.getStatus()).thenReturn("CHARGING");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteConnector(connectorId));

        assertEquals(409, exception.getStatus());
        assertEquals("非空闲充电枪不能删除", exception.getMessage());
        verify(connectors, never()).deleteById(connectorId);
    }

    @Test
    void cannotDeleteIdleConnectorWithOrderHistory() {
        Long connectorId = 20L;
        Connector connector = mock(Connector.class);
        when(connectors.selectById(connectorId)).thenReturn(connector);
        when(connector.getStatus()).thenReturn("IDLE");
        when(orders.existsByConnectorId(connectorId)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteConnector(connectorId));

        assertEquals(409, exception.getStatus());
        assertEquals("该充电枪已有订单记录，不能删除", exception.getMessage());
        verify(connectors, never()).deleteById(connectorId);
    }
}
