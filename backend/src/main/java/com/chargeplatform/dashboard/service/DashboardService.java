package com.chargeplatform.dashboard.service;

import com.chargeplatform.device.mapper.ChargerMapper;
import com.chargeplatform.device.mapper.ConnectorMapper;
import com.chargeplatform.order.mapper.ChargeOrderMapper;
import com.chargeplatform.station.mapper.StationMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DashboardService {
    private final StationMapper stations;
    private final ChargerMapper chargers;
    private final ConnectorMapper connectors;
    private final ChargeOrderMapper orders;

    public DashboardService(StationMapper stations, ChargerMapper chargers,
                            ConnectorMapper connectors, ChargeOrderMapper orders) {
        this.stations = stations;
        this.chargers = chargers;
        this.connectors = connectors;
        this.orders = orders;
    }

    @Cacheable(cacheNames = "dashboard", key = "'summary'")
    public Map<String, Long> summary() {
        return Map.of(
                "stationCount", stations.selectCount(null),
                "chargerCount", chargers.selectCount(null),
                "connectorCount", connectors.selectCount(null),
                "chargingOrderCount", orders.countByStatus("CHARGING"),
                "completedOrderCount", orders.countByStatus("COMPLETED"));
    }
}
