package com.chargeplatform.price.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@TableName("price_period")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PricePeriod {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stationId;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal electricityPrice;
    private BigDecimal servicePrice;
    public PricePeriod(Long stationId, LocalTime startTime, LocalTime endTime, BigDecimal electricityPrice, BigDecimal servicePrice) {
        this.stationId=stationId; this.startTime=startTime; this.endTime=endTime; this.electricityPrice=electricityPrice; this.servicePrice=servicePrice;
    }
}
