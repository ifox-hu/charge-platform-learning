package com.chargeplatform.order.service;

import com.chargeplatform.device.domain.Connector;
import com.chargeplatform.device.mapper.ChargerMapper;
import com.chargeplatform.device.mapper.ConnectorMapper;
import com.chargeplatform.order.domain.ChargeOrder;
import com.chargeplatform.order.event.OrderEventPublisher;
import com.chargeplatform.order.mapper.ChargeOrderMapper;
import com.chargeplatform.price.mapper.PricePeriodMapper;
import com.chargeplatform.simulator.SimulatorBindingResolver;
import com.chargeplatform.simulator.SimulatorTcpClient;
import com.chargeplatform.station.mapper.StationMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChargeOrderSimulatorTest {

    @Test
    void settlesUsingConfirmedStopReadingInsteadOfRequestEnergy() {
        ChargeOrderMapper orders = mock(ChargeOrderMapper.class);
        ConnectorMapper connectors = mock(ConnectorMapper.class);
        PricePeriodMapper prices = mock(PricePeriodMapper.class);
        ChargeBillingService billing = mock(ChargeBillingService.class);
        SimulatorTcpClient simulator = mock(SimulatorTcpClient.class);
        SimulatorBindingResolver bindings = mock(SimulatorBindingResolver.class);
        ChargeOrder order = mock(ChargeOrder.class);
        Connector connector = mock(Connector.class);
        when(orders.selectByIdForUpdate(7L)).thenReturn(order);
        when(order.getStatus()).thenReturn("CHARGING");
        when(order.getId()).thenReturn(7L);
        when(order.getConnectorId()).thenReturn(37L);
        when(order.getStationId()).thenReturn(3L);
        when(order.getStartTime()).thenReturn(LocalDateTime.now().minusMinutes(1));
        when(order.getTotalAmount()).thenReturn(BigDecimal.ONE);
        when(connectors.selectById(37L)).thenReturn(connector);
        when(prices.selectByStationId(3L)).thenReturn(List.of());
        when(billing.calculate(any(), any(), any(), any())).thenReturn(new ChargeBillingService.BillingResult(BigDecimal.ONE, BigDecimal.ONE));
        when(simulator.enabled()).thenReturn(true);
        when(bindings.resolve(37L)).thenReturn(new SimulatorBindingResolver.Binding("SIM-PILE-002", 2, 37L));
        var charging = new SimulatorTcpClient.ConnectorSnapshot(2, "CHARGING", new BigDecimal("0.002"),
                BigDecimal.valueOf(7.2), BigDecimal.valueOf(220), BigDecimal.valueOf(32.73), Instant.now());
        var stopped = new SimulatorTcpClient.ConnectorSnapshot(2, "IDLE", new BigDecimal("0.004"),
                BigDecimal.valueOf(7.2), BigDecimal.valueOf(220), BigDecimal.ZERO, Instant.now());
        when(simulator.liveConnector("SIM-PILE-002", 2)).thenReturn(new SimulatorTcpClient.LiveConnector("SIM-PILE-002", charging));
        when(simulator.sendAndConfirm(eq("SIM-PILE-002"), any(), eq("IDLE"))).thenReturn(stopped);

        var service = new ChargeOrderService(orders, connectors, mock(ChargerMapper.class), mock(StationMapper.class),
                prices, billing, mock(OrderEventPublisher.class), simulator, bindings);
        service.stop(7L, new com.chargeplatform.order.dto.OrderRequests.Stop(new BigDecimal("99")));

        verify(billing).calculate(any(), any(), eq(new BigDecimal("0.004")), any());
        verify(order).complete(eq(new BigDecimal("0.004")), eq(BigDecimal.ONE), eq(BigDecimal.ONE), any());
        verify(simulator).sendAndConfirm(eq("SIM-PILE-002"), eq(new SimulatorTcpClient.Command("STOP", 2)), eq("IDLE"));
    }
}
