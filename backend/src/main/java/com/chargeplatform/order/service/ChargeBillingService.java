package com.chargeplatform.order.service;

import com.chargeplatform.common.exception.BusinessException;
import com.chargeplatform.price.domain.PricePeriod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class ChargeBillingService {

    public BillingResult calculate(LocalDateTime start, LocalDateTime end, BigDecimal energyKwh,
                                   List<PricePeriod> periods) {
        long totalSeconds = Duration.between(start, end).getSeconds();
        if (totalSeconds <= 0) throw new BusinessException(400, "充电结束时间必须晚于开始时间");
        if (periods.isEmpty()) throw new BusinessException(400, "该站点尚未配置分时电价");

        BigDecimal electricityFee = BigDecimal.ZERO;
        BigDecimal serviceFee = BigDecimal.ZERO;
        LocalDateTime cursor = start;

        while (cursor.isBefore(end)) {
            PricePeriod period = findPeriod(cursor.toLocalTime(), periods);
            if (period == null) throw new BusinessException(400, "充电时段存在未配置的电价");

            LocalDateTime boundary = cursor.toLocalDate().atTime(period.getEndTime());
            if (LocalTime.of(23, 59, 59).equals(period.getEndTime())) boundary = boundary.plusSeconds(1);
            if (!boundary.isAfter(cursor)) boundary = boundary.plusDays(1);
            LocalDateTime segmentEnd = boundary.isBefore(end) ? boundary : end;
            long segmentSeconds = Duration.between(cursor, segmentEnd).getSeconds();
            BigDecimal segmentEnergy = energyKwh.multiply(BigDecimal.valueOf(segmentSeconds))
                    .divide(BigDecimal.valueOf(totalSeconds), 12, RoundingMode.HALF_UP);
            electricityFee = electricityFee.add(segmentEnergy.multiply(period.getElectricityPrice()));
            serviceFee = serviceFee.add(segmentEnergy.multiply(period.getServicePrice()));
            cursor = segmentEnd;
        }

        return new BillingResult(
                electricityFee.setScale(2, RoundingMode.HALF_UP),
                serviceFee.setScale(2, RoundingMode.HALF_UP));
    }

    private PricePeriod findPeriod(LocalTime time, List<PricePeriod> periods) {
        return periods.stream()
                .filter(period -> !time.isBefore(period.getStartTime()))
                .filter(period -> time.isBefore(period.getEndTime())
                        || LocalTime.of(23, 59, 59).equals(period.getEndTime()))
                .findFirst()
                .orElse(null);
    }

    public record BillingResult(BigDecimal electricityFee, BigDecimal serviceFee) {
    }
}
