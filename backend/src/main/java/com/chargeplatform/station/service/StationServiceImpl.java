package com.chargeplatform.station.service;

import com.chargeplatform.station.domain.Station;
import com.chargeplatform.station.mapper.StationMapper;
import com.chargeplatform.common.exception.BusinessException;
import com.chargeplatform.device.mapper.ChargerMapper;
import com.chargeplatform.price.mapper.PricePeriodMapper;
import com.chargeplatform.order.mapper.ChargeOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;
import java.math.BigDecimal;

@Service
public class StationServiceImpl implements StationService {

    private final StationMapper stations;
    private final ChargerMapper chargers;
    private final PricePeriodMapper prices;
    private final ChargeOrderMapper orders;

    public StationServiceImpl(StationMapper stations, ChargerMapper chargers, PricePeriodMapper prices, ChargeOrderMapper orders) {
        this.stations = stations;
        this.chargers = chargers;
        this.prices = prices;
        this.orders = orders;
    }

    @Override
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public Station create(String name, String address, String description) {
        return create(name, address, description, null, null, null);
    }

    @Override
    public Station create(String name, String address, String description, BigDecimal latitude, BigDecimal longitude, String coordinateType) {
        Station station = new Station(name.trim(), address.trim(), description, latitude, longitude, coordinateType);
        stations.insert(station);
        return station;
    }

    @Override
    public List<Station> findAll() {
        return stations.selectList(new LambdaQueryWrapper<Station>()
            .orderByAsc(Station::getId));
    }

    @Override
    public Page<Station> findPage(String name, int page, int size) {
        Page<Station> result = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        String keyword = name == null ? "" : name.trim();
        return stations.selectPage(result, new LambdaQueryWrapper<Station>()
            .like(!keyword.isEmpty(), Station::getName, keyword)
            .orderByAsc(Station::getId));
    }

    @Override
    public Station findById(Long id) {
        Station station = stations.selectById(id);
        if (station == null) throw new BusinessException(404, "充电站不存在");
        return station;
    }

    @Override
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public Station update(Long id, String name, String address, String description, String status) {
        Station current = findById(id);
        return update(id, name, address, description, status, current.getLatitude(), current.getLongitude(), current.getCoordinateType());
    }

    @Override
    public Station update(Long id, String name, String address, String description, String status, BigDecimal latitude, BigDecimal longitude, String coordinateType) {
        Station station = findById(id);
        station.update(name.trim(), address.trim(), description, status,
            latitude == null ? station.getLatitude() : latitude,
            longitude == null ? station.getLongitude() : longitude,
            coordinateType == null || coordinateType.isBlank() ? station.getCoordinateType() : coordinateType.trim());
        stations.updateById(station);
        return station;
    }

    @Override
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public Station updateCoordinates(Long id, BigDecimal latitude, BigDecimal longitude, String coordinateType) {
        if (latitude == null || longitude == null) throw new BusinessException(400, "经纬度不能为空");
        Station station = findById(id);
        station.updateCoordinates(latitude, longitude, coordinateType == null || coordinateType.isBlank() ? "GCJ02" : coordinateType.trim());
        stations.updateById(station);
        return station;
    }

    @Override
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public void delete(Long id) {
        if (stations.selectById(id) == null) {
            throw new BusinessException(404, "充电站不存在");
        }
        if (chargers.existsByStationId(id)) {
            throw new BusinessException(409, "请先删除该站点下的充电桩");
        }
        if (orders.existsByStationId(id)) {
            throw new BusinessException(409, "该站点已有充电订单，不能删除");
        }
        if (prices.existsByStationId(id)) {
            throw new BusinessException(409, "请先删除该站点下的电价时段");
        }
        stations.deleteById(id);
    }
}
