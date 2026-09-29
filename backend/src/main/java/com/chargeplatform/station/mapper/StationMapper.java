package com.chargeplatform.station.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chargeplatform.station.domain.Station;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StationMapper extends BaseMapper<Station> {
}
