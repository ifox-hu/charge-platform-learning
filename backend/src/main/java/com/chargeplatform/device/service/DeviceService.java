package com.chargeplatform.device.service;

import com.chargeplatform.common.exception.BusinessException;
import com.chargeplatform.device.domain.Charger;
import com.chargeplatform.device.domain.Connector;
import com.chargeplatform.device.dto.DeviceRequests;
import com.chargeplatform.device.mapper.ChargerMapper;
import com.chargeplatform.device.mapper.ConnectorMapper;
import com.chargeplatform.station.mapper.StationMapper;
import com.chargeplatform.order.mapper.ChargeOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import java.util.List;

@Service
public class DeviceService {
    private final ChargerMapper chargers;
    private final ConnectorMapper connectors;
    private final StationMapper stations;
    private final ChargeOrderMapper orders;

    public DeviceService(ChargerMapper chargers, ConnectorMapper connectors, StationMapper stations, ChargeOrderMapper orders) {
        this.chargers = chargers;
        this.connectors = connectors;
        this.stations = stations;
        this.orders = orders;
    }

    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public Charger createCharger(DeviceRequests.CreateCharger request) {
        if (stations.selectById(request.stationId()) == null) throw new BusinessException(404, "所属充电站不存在");
        Charger charger = new Charger(chargers.nextId(), request.stationId(), request.code().trim(), request.name().trim());
        chargers.insert(charger);
        return charger;
    }

    public List<Charger> chargers(Long stationId) {
        return chargers.selectList(new LambdaQueryWrapper<Charger>()
            .eq(stationId != null, Charger::getStationId, stationId)
            .orderByAsc(Charger::getId));
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public Charger changeStatus(Long id, String status) {
        Charger charger = chargers.selectById(id);
        if (charger == null) throw new BusinessException(404, "充电桩不存在");
        if (!"ONLINE".equals(status) && connectors.existsByChargerIdAndStatus(id, "CHARGING")) {
            throw new BusinessException(409, "存在正在充电的充电枪，不能将充电桩离线");
        }
        charger.updateStatus(status);
        chargers.updateById(charger);
        return charger;
    }

    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public Connector createConnector(DeviceRequests.CreateConnector request) {
        if (chargers.selectById(request.chargerId()) == null) throw new BusinessException(404, "所属充电桩不存在");
        Connector connector = new Connector(connectors.nextId(), request.chargerId(), request.code().trim(), request.name().trim(), request.ratedPower());
        connectors.insert(connector);
        return connector;
    }

    public List<Connector> connectors(Long chargerId) {
        return connectors.selectList(new LambdaQueryWrapper<Connector>()
            .eq(chargerId != null, Connector::getChargerId, chargerId)
            .orderByAsc(Connector::getId));
    }

    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public void deleteCharger(Long id) {
        if (chargers.selectById(id) == null) throw new BusinessException(404, "充电桩不存在");
        if (connectors.existsByChargerId(id)) throw new BusinessException(409, "请先删除该充电桩下的充电枪");
        chargers.deleteById(id);
    }

    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public void deleteConnector(Long id) {
        Connector connector = connectors.selectById(id);
        if (connector == null) throw new BusinessException(404, "充电枪不存在");
        if (!"IDLE".equals(connector.getStatus())) throw new BusinessException(409, "非空闲充电枪不能删除");
        if (orders.existsByConnectorId(id)) throw new BusinessException(409, "该充电枪已有订单记录，不能删除");
        connectors.deleteById(id);
    }
}
