package com.chargeplatform.price.service;

import com.chargeplatform.common.exception.BusinessException;
import com.chargeplatform.price.domain.PricePeriod;
import com.chargeplatform.price.mapper.PricePeriodMapper;
import com.chargeplatform.station.mapper.StationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Service
public class PriceService {
    private final PricePeriodMapper prices;
    private final StationMapper stations;

    public PriceService(PricePeriodMapper prices, StationMapper stations) {
        this.prices = prices;
        this.stations = stations;
    }

    @Transactional
    public PricePeriod create(Long stationId, LocalTime startTime, LocalTime endTime,
                              BigDecimal electricityPrice, BigDecimal servicePrice) {
        if (stations.selectById(stationId) == null) throw new BusinessException(404, "充电站不存在");
        if (!startTime.isBefore(endTime)) throw new BusinessException(400, "开始时间必须早于结束时间");
        if (prices.existsOverlapping(stationId, startTime, endTime)) throw new BusinessException(409, "电价时段不能重叠");

        PricePeriod period = new PricePeriod(stationId, startTime, endTime, electricityPrice, servicePrice);
        prices.insert(period);
        return period;
    }

    public List<PricePeriod> list(Long stationId) {
        return prices.selectByStationId(stationId);
    }

    @Transactional
    public PricePeriod update(Long id, Long stationId, LocalTime startTime, LocalTime endTime,
                              BigDecimal electricityPrice, BigDecimal servicePrice) {
        PricePeriod period = prices.selectById(id);
        if (period == null) throw new BusinessException(404, "电价时段不存在");
        if (stations.selectById(stationId) == null) throw new BusinessException(404, "充电站不存在");
        if (!startTime.isBefore(endTime)) throw new BusinessException(400, "开始时间必须早于结束时间");
        boolean overlap = prices.selectByStationId(stationId).stream()
            .filter(item -> !item.getId().equals(id))
            .anyMatch(item -> item.getStartTime().isBefore(endTime) && item.getEndTime().isAfter(startTime));
        if (overlap) throw new BusinessException(409, "电价时段不能重叠");
        period.update(startTime, endTime, electricityPrice, servicePrice);
        prices.updateById(period);
        return period;
    }

    public void delete(Long id) {
        if (prices.selectById(id) == null) throw new BusinessException(404, "电价时段不存在");
        prices.deleteById(id);
    }
}
