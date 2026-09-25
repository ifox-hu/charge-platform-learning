package com.chargeplatform.price.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chargeplatform.price.domain.PricePeriod;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalTime;
import java.util.List;

@Mapper
public interface PricePeriodMapper extends BaseMapper<PricePeriod> {
    @Select("SELECT * FROM price_period WHERE station_id = #{stationId} ORDER BY start_time")
    List<PricePeriod> selectByStationId(@Param("stationId") Long stationId);

    @Select("SELECT * FROM price_period WHERE station_id = #{stationId} AND start_time <= #{time} AND end_time > #{time} LIMIT 1")
    PricePeriod selectCurrentByStationId(@Param("stationId") Long stationId, @Param("time") LocalTime time);

    @Select("SELECT COUNT(*) > 0 FROM price_period WHERE station_id = #{stationId} AND start_time < #{endTime} AND end_time > #{startTime}")
    boolean existsOverlapping(@Param("stationId") Long stationId, @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime);

    @Select("SELECT COUNT(*) > 0 FROM price_period WHERE station_id = #{stationId}")
    boolean existsByStationId(@Param("stationId") Long stationId);
}
