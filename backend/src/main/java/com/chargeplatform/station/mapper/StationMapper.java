package com.chargeplatform.station.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chargeplatform.station.domain.Station;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StationMapper extends BaseMapper<Station> {
    @Select("SELECT COALESCE(MAX(id), 0) + 1 FROM station")
    Long nextId();
}
