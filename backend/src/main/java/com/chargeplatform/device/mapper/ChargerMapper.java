package com.chargeplatform.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chargeplatform.device.domain.Charger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChargerMapper extends BaseMapper<Charger> {
    @Select("SELECT * FROM charger WHERE code = #{code} LIMIT 1")
    Charger selectByCode(@Param("code") String code);

    @Select("SELECT COUNT(*) > 0 FROM charger WHERE station_id = #{stationId}")
    boolean existsByStationId(@Param("stationId") Long stationId);
}
