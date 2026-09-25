package com.chargeplatform.order.service;

import com.chargeplatform.device.mapper.ChargerMapper;
import com.chargeplatform.device.mapper.ConnectorMapper;
import com.chargeplatform.order.domain.ChargeOrder;
import com.chargeplatform.order.event.OrderEventPublisher;
import com.chargeplatform.order.mapper.ChargeOrderMapper;
import com.chargeplatform.price.mapper.PricePeriodMapper;
import com.chargeplatform.station.mapper.StationMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChargeOrderExportTest {

    @Test
    void exportsUtf8CsvWithAllMatchingOrders() {
        ChargeOrderMapper orders = mock(ChargeOrderMapper.class);
        ChargeOrder order = mock(ChargeOrder.class);
        when(order.getOrderNo()).thenReturn("CO-1");
        when(order.getPlateNumber()).thenReturn("粤A12345");
        when(order.getStationId()).thenReturn(1L);
        when(order.getConnectorId()).thenReturn(2L);
        when(order.getStatus()).thenReturn("COMPLETED");
        when(order.getEnergyKwh()).thenReturn(new BigDecimal("10.000"));
        when(order.getElectricityFee()).thenReturn(new BigDecimal("8.00"));
        when(order.getServiceFee()).thenReturn(new BigDecimal("4.00"));
        when(order.getTotalAmount()).thenReturn(new BigDecimal("12.00"));
        when(orders.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(order));

        ChargeOrderService service = new ChargeOrderService(
                orders, mock(ConnectorMapper.class), mock(ChargerMapper.class), mock(StationMapper.class),
                mock(PricePeriodMapper.class), new ChargeBillingService(), mock(OrderEventPublisher.class));

        String csv = new String(service.exportCsv("粤A"), StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF订单号,车牌号"));
        assertTrue(csv.contains("CO-1,粤A12345,1,2,COMPLETED"));
        assertTrue(csv.contains("10.000,8.00,4.00,12.00"));
    }
}
